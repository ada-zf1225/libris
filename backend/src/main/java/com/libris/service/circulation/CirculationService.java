package com.libris.service.circulation;

import com.libris.domain.catalog.Book;
import com.libris.domain.catalog.BookCopy;
import com.libris.domain.catalog.BookRepository;
import com.libris.domain.catalog.BookCopyRepository;
import com.libris.domain.catalog.CopyStatus;
import com.libris.domain.circulation.*;
import com.libris.domain.user.*;
import com.libris.web.error.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Circulation core. Every mutation locks the physical copy row first
 * (SELECT ... FOR UPDATE), so concurrent desk operations on the same item
 * serialise; the partial unique index on loans(copy_id) is the final
 * database-level guarantee.
 */
@Service
@RequiredArgsConstructor
public class CirculationService {

    public static final Duration HOLD_SHELF_DAYS = Duration.ofDays(7);

    private final BookRepository books;
    private final BookCopyRepository copies;
    private final LoanRepository loans;
    private final HoldRepository holds;
    private final FineRepository fines;
    private final LoanPolicyRepository policies;
    private final UserRepository users;
    private final ReaderProfileRepository profiles;
    private final CirculationLogRepository logs;
    private final com.libris.service.notify.NotificationService notify;

    // ---------- checkout ----------

    public record CheckoutResult(Long loanId, String bookTitle, String barcode, Instant dueAt, boolean fulfilledHold) {}

    @Transactional
    public CheckoutResult checkout(String barcode, Long readerId, Long operatorId) {
        BookCopy copy = copies.findByBarcodeForUpdate(barcode)
                .orElseThrow(() -> ApiException.notFound("error.circulation.copyNotFound", barcode));
        User reader = requireActiveReader(readerId);
        LoanPolicy policy = policyOf(reader.getId());

        // real-time block checks (Alma-style system blocks)
        Instant now = Instant.now();
        if (loans.countByReaderIdAndReturnedAtIsNull(readerId) >= policy.getMaxLoans()) {
            throw ApiException.conflict("error.circulation.maxLoansReached", policy.getMaxLoans());
        }
        if (loans.countOverdue(readerId, now) >= policy.getBlockOverdueCount()) {
            throw ApiException.conflict("error.circulation.blockedOverdue");
        }
        if (fines.unpaidTotal(readerId) >= policy.getBlockFineCents()) {
            throw ApiException.conflict("error.circulation.blockedFines");
        }

        boolean fulfilledHold = false;
        switch (copy.getStatus()) {
            case IN_LIBRARY -> { /* free to lend */ }
            case ON_HOLD_SHELF -> {
                Hold ready = holds.findByReadyCopyIdAndStatus(copy.getId(), HoldStatus.READY)
                        .orElseThrow(() -> ApiException.conflict("error.circulation.copyNotLendable"));
                if (!ready.getReaderId().equals(readerId)) {
                    throw ApiException.conflict("error.circulation.heldForAnotherReader");
                }
                ready.setStatus(HoldStatus.FULFILLED);
                fulfilledHold = true;
            }
            default -> throw ApiException.conflict("error.circulation.copyNotLendable");
        }

        Instant due = now.plus(policy.getLoanDays(), ChronoUnit.DAYS);
        Loan loan = loans.save(new Loan(copy.getId(), readerId, now, due, operatorId));
        copy.setStatus(CopyStatus.ON_LOAN);

        Book book = books.findById(copy.getBookId()).orElseThrow();
        log(operatorId, "CHECKOUT", copy.getId(), readerId,
                Map.of("barcode", barcode, "title", book.getTitle(), "dueAt", due.toString()));
        return new CheckoutResult(loan.getId(), book.getTitle(), barcode, due, fulfilledHold);
    }

    // ---------- checkin ----------

    public enum Routing { TO_SHELF, TO_HOLD_SHELF }

    public record CheckinResult(String bookTitle, String barcode, Routing routing,
                                String holdReaderName, Integer fineCents, long overdueDays) {}

    @Transactional
    public CheckinResult checkin(String barcode, Long operatorId) {
        BookCopy copy = copies.findByBarcodeForUpdate(barcode)
                .orElseThrow(() -> ApiException.notFound("error.circulation.copyNotFound", barcode));
        Loan loan = loans.findByCopyIdAndReturnedAtIsNull(copy.getId())
                .orElseThrow(() -> ApiException.conflict("error.circulation.notOnLoan"));

        Instant now = Instant.now();
        loan.setReturnedAt(now);

        long overdueDays = overdueDays(loan.getDueAt(), now);
        Integer fineCents = null;
        if (overdueDays > 0) {
            LoanPolicy policy = policyOf(loan.getReaderId());
            int amount = Math.toIntExact(overdueDays * policy.getDailyFineCents());
            if (amount > 0) {
                fines.save(new Fine(loan.getId(), loan.getReaderId(), amount, Fine.Reason.OVERDUE));
                fineCents = amount;
            }
        }

        Book book = books.findById(copy.getBookId()).orElseThrow();

        // hold promotion: oldest queued hold wins the returned copy
        Optional<Hold> queued = holds.findFirstQueuedForUpdate(copy.getBookId());
        Routing routing;
        String holdReaderName = null;
        if (queued.isPresent()) {
            Hold hold = queued.get();
            hold.setStatus(HoldStatus.READY);
            hold.setReadyCopyId(copy.getId());
            hold.setReadyAt(now);
            hold.setExpiresAt(now.plus(HOLD_SHELF_DAYS));
            copy.setStatus(CopyStatus.ON_HOLD_SHELF);
            holdReaderName = users.findById(hold.getReaderId()).map(User::getDisplayName).orElse("?");
            routing = Routing.TO_HOLD_SHELF;
            notify.notifyUser(hold.getReaderId(), "HOLD_READY", "notice.holdReady",
                    book.getTitle(),
                    hold.getExpiresAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate());
        } else {
            copy.setStatus(CopyStatus.IN_LIBRARY);
            routing = Routing.TO_SHELF;
        }

        log(operatorId, "CHECKIN", copy.getId(), loan.getReaderId(), Map.of(
                "barcode", barcode, "title", book.getTitle(),
                "overdueDays", overdueDays, "fineCents", fineCents == null ? 0 : fineCents,
                "routing", routing.name()));
        return new CheckinResult(book.getTitle(), barcode, routing, holdReaderName, fineCents, overdueDays);
    }

    // ---------- renew ----------

    public record RenewResult(Long loanId, Instant newDueAt, int renewCount) {}

    /** {@code actingReaderId} is non-null when a patron renews their own loan (ownership enforced). */
    @Transactional
    public RenewResult renew(Long loanId, Long actingReaderId, Long operatorId) {
        Loan loan = loans.findById(loanId)
                .orElseThrow(() -> ApiException.notFound("error.circulation.loanNotFound"));
        if (loan.getReturnedAt() != null) {
            throw ApiException.conflict("error.circulation.alreadyReturned");
        }
        if (actingReaderId != null && !loan.getReaderId().equals(actingReaderId)) {
            throw ApiException.notFound("error.circulation.loanNotFound"); // do not leak others' loans
        }
        Instant now = Instant.now();
        if (loan.isOverdue(now)) {
            throw ApiException.conflict("error.circulation.overdueNotRenewable");
        }
        LoanPolicy policy = policyOf(loan.getReaderId());
        if (loan.getRenewCount() >= policy.getMaxRenewals()) {
            throw ApiException.conflict("error.circulation.maxRenewalsReached", policy.getMaxRenewals());
        }
        BookCopy copy = copies.findById(loan.getCopyId()).orElseThrow();
        if (holds.existsByBookIdAndStatusIn(copy.getBookId(), List.of(HoldStatus.QUEUED, HoldStatus.READY))) {
            throw ApiException.conflict("error.circulation.holdBlocksRenewal");
        }

        loan.setDueAt(now.plus(policy.getLoanDays(), ChronoUnit.DAYS));
        loan.setRenewCount(loan.getRenewCount() + 1);
        log(operatorId, "RENEW", loan.getCopyId(), loan.getReaderId(),
                Map.of("loanId", loanId, "newDueAt", loan.getDueAt().toString(), "renewCount", loan.getRenewCount()));
        return new RenewResult(loan.getId(), loan.getDueAt(), loan.getRenewCount());
    }

    // ---------- lost ----------

    public record LostResult(String bookTitle, String barcode, Integer fineCents) {}

    @Transactional
    public LostResult markLost(String barcode, Long operatorId) {
        BookCopy copy = copies.findByBarcodeForUpdate(barcode)
                .orElseThrow(() -> ApiException.notFound("error.circulation.copyNotFound", barcode));
        if (copy.getStatus() == CopyStatus.LOST) {
            throw ApiException.conflict("error.circulation.alreadyLost");
        }
        Book book = books.findById(copy.getBookId()).orElseThrow();

        Integer fineCents = null;
        Optional<Loan> active = loans.findByCopyIdAndReturnedAtIsNull(copy.getId());
        if (active.isPresent()) {
            Loan loan = active.get();
            loan.setReturnedAt(Instant.now());
            int amount = book.getPriceCents() != null ? book.getPriceCents() : 0;
            if (amount > 0) {
                fines.save(new Fine(loan.getId(), loan.getReaderId(), amount, Fine.Reason.LOST));
                fineCents = amount;
            }
        }
        copy.setStatus(CopyStatus.LOST);
        log(operatorId, "MARK_LOST", copy.getId(), active.map(Loan::getReaderId).orElse(null),
                Map.of("barcode", barcode, "title", book.getTitle(), "fineCents", fineCents == null ? 0 : fineCents));
        return new LostResult(book.getTitle(), barcode, fineCents);
    }

    // ---------- fines ----------

    @Transactional
    public void payFine(Long fineId, Long operatorId) {
        Fine fine = fines.findById(fineId).orElseThrow(() -> ApiException.notFound("error.notFound"));
        if (fine.getStatus() != Fine.Status.UNPAID) {
            throw ApiException.conflict("error.circulation.fineSettled");
        }
        fine.setStatus(Fine.Status.PAID);
        fine.setPaidAt(Instant.now());
        log(operatorId, "FINE_PAID", null, fine.getReaderId(),
                Map.of("fineId", fineId, "amountCents", fine.getAmountCents()));
    }

    @Transactional
    public void waiveFine(Long fineId, Long operatorId) {
        Fine fine = fines.findById(fineId).orElseThrow(() -> ApiException.notFound("error.notFound"));
        if (fine.getStatus() != Fine.Status.UNPAID) {
            throw ApiException.conflict("error.circulation.fineSettled");
        }
        fine.setStatus(Fine.Status.WAIVED);
        log(operatorId, "FINE_WAIVED", null, fine.getReaderId(),
                Map.of("fineId", fineId, "amountCents", fine.getAmountCents()));
    }

    // ---------- shared helpers ----------

    public LoanPolicy policyOf(Long readerId) {
        ReaderType type = profiles.findById(readerId)
                .map(ReaderProfile::getReaderType)
                .orElseThrow(() -> ApiException.notFound("error.circulation.readerNotFound"));
        return policies.findByReaderType(type)
                .orElseThrow(() -> ApiException.notFound("error.circulation.policyMissing"));
    }

    public static long overdueDays(Instant dueAt, Instant now) {
        if (!now.isAfter(dueAt)) {
            return 0;
        }
        long seconds = Duration.between(dueAt, now).getSeconds();
        return (seconds + 86_399) / 86_400; // ceil to whole days
    }

    private User requireActiveReader(Long readerId) {
        User reader = users.findById(readerId)
                .filter(u -> u.getRole() == Role.READER)
                .orElseThrow(() -> ApiException.notFound("error.circulation.readerNotFound"));
        if (reader.getStatus() == UserStatus.BLOCKED) {
            throw ApiException.conflict("error.circulation.readerBlocked");
        }
        return reader;
    }

    private void log(Long operatorId, String action, Long copyId, Long readerId, Map<String, Object> detail) {
        logs.save(new CirculationLog(operatorId, action, copyId, readerId, detail));
    }
}
