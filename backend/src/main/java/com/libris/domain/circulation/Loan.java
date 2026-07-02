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
@Table(name = "loans")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "copy_id", nullable = false)
    private Long copyId;

    @Column(name = "reader_id", nullable = false)
    private Long readerId;

    @Column(name = "loaned_at", nullable = false)
    private Instant loanedAt;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "returned_at")
    private Instant returnedAt;

    @Column(name = "renew_count", nullable = false)
    private int renewCount;

    @Column(name = "operator_id")
    private Long operatorId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Loan(Long copyId, Long readerId, Instant loanedAt, Instant dueAt, Long operatorId) {
        this.copyId = copyId;
        this.readerId = readerId;
        this.loanedAt = loanedAt;
        this.dueAt = dueAt;
        this.operatorId = operatorId;
    }

    public boolean isOverdue(Instant now) {
        return returnedAt == null && now.isAfter(dueAt);
    }
}
