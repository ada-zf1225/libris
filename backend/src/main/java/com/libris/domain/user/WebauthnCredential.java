package com.libris.domain.user;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "webauthn_credentials")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WebauthnCredential {

    @Id
    @Column(name = "credential_id", length = 512)
    private String credentialId; // base64url

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_entity_id", nullable = false, length = 64)
    private String userEntityId; // base64url handle

    @Column(nullable = false, length = 64)
    private String label;

    @Column(name = "credential_type", nullable = false, length = 32)
    private String credentialType = "public-key";

    @Column(name = "public_key_cose", nullable = false)
    private byte[] publicKeyCose;

    @Column(name = "signature_count", nullable = false)
    private long signatureCount;

    @Column(length = 128)
    private String transports; // csv

    @Column(name = "uv_initialized", nullable = false)
    private boolean uvInitialized;

    @Column(name = "backup_eligible", nullable = false)
    private boolean backupEligible;

    @Column(name = "backup_state", nullable = false)
    private boolean backupState;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    public WebauthnCredential(String credentialId, Long userId, String userEntityId, String label) {
        this.credentialId = credentialId;
        this.userId = userId;
        this.userEntityId = userEntityId;
        this.label = label;
    }
}
