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
@Table(name = "fines")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Fine {

    public enum Reason { OVERDUE, LOST }

    public enum Status { UNPAID, PAID, WAIVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "loan_id", nullable = false)
    private Long loanId;

    @Column(name = "reader_id", nullable = false)
    private Long readerId;

    @Column(name = "amount_cents", nullable = false)
    private int amountCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Reason reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.UNPAID;

    @Column(name = "paid_at")
    private Instant paidAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Fine(Long loanId, Long readerId, int amountCents, Reason reason) {
        this.loanId = loanId;
        this.readerId = readerId;
        this.amountCents = amountCents;
        this.reason = reason;
    }
}
