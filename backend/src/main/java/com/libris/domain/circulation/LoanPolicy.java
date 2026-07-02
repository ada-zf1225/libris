package com.libris.domain.circulation;

import com.libris.domain.user.ReaderType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "loan_policies")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoanPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "reader_type", nullable = false, unique = true, length = 16)
    private ReaderType readerType;

    @Column(name = "loan_days", nullable = false)
    private int loanDays;

    @Column(name = "max_loans", nullable = false)
    private int maxLoans;

    @Column(name = "max_renewals", nullable = false)
    private int maxRenewals;

    @Column(name = "daily_fine_cents", nullable = false)
    private int dailyFineCents;

    @Column(name = "block_overdue_count", nullable = false)
    private int blockOverdueCount;

    @Column(name = "block_fine_cents", nullable = false)
    private int blockFineCents;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
