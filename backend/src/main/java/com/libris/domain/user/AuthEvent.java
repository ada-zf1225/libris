package com.libris.domain.user;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "auth_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(length = 32)
    private String username;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(length = 64)
    private String ip;

    @Column(name = "user_agent", length = 256)
    private String userAgent;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public AuthEvent(Long userId, String username, String type, String ip, String userAgent) {
        this.userId = userId;
        this.username = username;
        this.type = type;
        this.ip = ip;
        this.userAgent = userAgent == null ? null : userAgent.substring(0, Math.min(255, userAgent.length()));
    }
}
