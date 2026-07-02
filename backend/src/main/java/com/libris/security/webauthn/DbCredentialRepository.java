package com.libris.security.webauthn;

import com.libris.domain.user.User;
import com.libris.domain.user.UserRepository;
import com.libris.domain.user.WebauthnCredential;
import com.libris.domain.user.WebauthnCredentialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.web.webauthn.api.*;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DbCredentialRepository implements UserCredentialRepository {

    private final WebauthnCredentialRepository credentials;
    private final UserRepository users;

    @Override
    @Transactional
    public void delete(Bytes credentialId) {
        credentials.deleteById(credentialId.toBase64UrlString());
    }

    @Override
    @Transactional(readOnly = true)
    public CredentialRecord findByCredentialId(Bytes credentialId) {
        return credentials.findById(credentialId.toBase64UrlString()).map(this::toRecord).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CredentialRecord> findByUserId(Bytes userId) {
        return credentials.findByUserEntityId(userId.toBase64UrlString()).stream()
                .map(this::toRecord)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void save(CredentialRecord record) {
        String id = record.getCredentialId().toBase64UrlString();
        String entityId = record.getUserEntityUserId().toBase64UrlString();
        WebauthnCredential entity = credentials.findById(id).orElseGet(() -> {
            Long userId = users.findByWebauthnHandle(entityId).map(User::getId).orElseThrow();
            return new WebauthnCredential(id, userId, entityId,
                    record.getLabel() == null || record.getLabel().isBlank() ? "Passkey" : record.getLabel());
        });
        entity.setPublicKeyCose(record.getPublicKey().getBytes());
        entity.setSignatureCount(record.getSignatureCount());
        entity.setUvInitialized(record.isUvInitialized());
        entity.setBackupEligible(record.isBackupEligible());
        entity.setBackupState(record.isBackupState());
        if (record.getTransports() != null) {
            entity.setTransports(record.getTransports().stream()
                    .map(AuthenticatorTransport::getValue)
                    .collect(Collectors.joining(",")));
        }
        entity.setLastUsedAt(record.getLastUsed());
        credentials.save(entity);
    }

    private CredentialRecord toRecord(WebauthnCredential c) {
        Set<AuthenticatorTransport> transports = c.getTransports() == null || c.getTransports().isBlank()
                ? Set.of()
                : Arrays.stream(c.getTransports().split(","))
                        .map(AuthenticatorTransport::valueOf)
                        .collect(Collectors.toSet());
        return ImmutableCredentialRecord.builder()
                .credentialType(PublicKeyCredentialType.PUBLIC_KEY)
                .credentialId(Bytes.fromBase64(c.getCredentialId()))
                .userEntityUserId(Bytes.fromBase64(c.getUserEntityId()))
                .publicKey(new ImmutablePublicKeyCose(c.getPublicKeyCose()))
                .signatureCount(c.getSignatureCount())
                .uvInitialized(c.isUvInitialized())
                .backupEligible(c.isBackupEligible())
                .backupState(c.isBackupState())
                .transports(transports)
                .label(c.getLabel())
                .created(c.getCreatedAt())
                .lastUsed(c.getLastUsedAt() == null ? Instant.EPOCH : c.getLastUsedAt())
                .build();
    }
}
