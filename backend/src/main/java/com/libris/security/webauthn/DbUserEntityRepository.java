package com.libris.security.webauthn;

import com.libris.domain.user.User;
import com.libris.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bridges Spring Security's WebAuthn user-entity abstraction onto the users
 * table: the opaque user handle lives in {@code users.webauthn_handle}.
 */
@Component
@RequiredArgsConstructor
public class DbUserEntityRepository implements PublicKeyCredentialUserEntityRepository {

    private final UserRepository users;

    @Override
    @Transactional(readOnly = true)
    public PublicKeyCredentialUserEntity findById(Bytes id) {
        return users.findByWebauthnHandle(id.toBase64UrlString()).map(this::toEntity).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public PublicKeyCredentialUserEntity findByUsername(String username) {
        return users.findByUsername(username)
                .filter(u -> u.getWebauthnHandle() != null)
                .map(this::toEntity)
                .orElse(null);
    }

    @Override
    @Transactional
    public void save(PublicKeyCredentialUserEntity userEntity) {
        users.findByUsername(userEntity.getName())
                .ifPresent(u -> u.setWebauthnHandle(userEntity.getId().toBase64UrlString()));
    }

    @Override
    @Transactional
    public void delete(Bytes id) {
        users.findByWebauthnHandle(id.toBase64UrlString()).ifPresent(u -> u.setWebauthnHandle(null));
    }

    private PublicKeyCredentialUserEntity toEntity(User user) {
        return ImmutablePublicKeyCredentialUserEntity.builder()
                .id(Bytes.fromBase64(user.getWebauthnHandle()))
                .name(user.getUsername())
                .displayName(user.getDisplayName())
                .build();
    }
}
