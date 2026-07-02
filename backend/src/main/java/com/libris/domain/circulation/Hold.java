package com.libris.domain.circulation;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "holds")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Hold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(name = "reader_id", nullable = false)
    private Long readerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private HoldStatus status = HoldStatus.QUEUED;

    @Column(name = "queued_at", nullable = false)
    private Instant queuedAt;

    @Column(name = "ready_copy_id")
    private Long readyCopyId;

    @Column(name = "ready_at")
    private Instant readyAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Hold(Long bookId, Long readerId, Instant queuedAt) {
        this.bookId = bookId;
        this.readerId = readerId;
        this.queuedAt = queuedAt;
    }
}
