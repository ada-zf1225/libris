package com.libris.jobs;

import com.libris.domain.catalog.Book;
import com.libris.domain.catalog.BookCopy;
import com.libris.domain.catalog.BookRepository;
import com.libris.domain.catalog.BookCopyRepository;
import com.libris.domain.circulation.*;
import com.libris.domain.user.User;
import com.libris.domain.user.UserRepository;
import com.libris.domain.user.UserStatus;
import com.libris.service.circulation.CirculationService;
import com.libris.service.notify.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Nightly/hourly fulfilment jobs — the Alma "Notifications – Send Courtesy
 * Notices / Handle Expired Hold Shelf" equivalents, single-library edition.
 * Job bodies are public and parameterised by "now" for direct testing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CirculationJobs {

    private final LoanRepository loans;
    private final HoldRepository holds;
    private final LoanPolicyRepository policies;
    private final UserRepository users;
    private final BookCopyRepository copies;
    private final BookRepository books;
    private final NotificationService notify;
    private final com.libris.domain.user.ReaderProfileRepository profiles;

    // ---------- due-soon courtesy notices ----------

    @Scheduled(cron = "0 0 8 * * *")
    public void dueSoonDaily() {
        int sent = sendDueSoonNotices(Instant.now());
        log.info("due-soon notices sent: {}", sent);
    }

    @Transactional
    public int sendDueSoonNotices(Instant now) {
        Instant windowEnd = now.plus(2, ChronoUnit.DAYS);
        List<Loan> dueSoon = loans.findDueBetween(now, windowEnd);
        int sent = 0;
        for (Loan loan : dueSoon) {
            String title = titleOf(loan);
            notify.notifyUser(loan.getReaderId(), "DUE_SOON", "notice.dueSoon",
                    title, loan.getDueAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate());
            sent++;
        }
        return sent;
    }

    // ---------- overdue escalation & automatic blocks ----------

    @Scheduled(cron = "0 30 8 * * *")
    public void overdueDaily() {
        int blocked = escalateOverdue(Instant.now());
        log.info("overdue escalation done, blocked readers: {}", blocked);
    }

    @Transactional
    public int escalateOverdue(Instant now) {
        List<Loan> overdue = loans.findOverdue(now);
        int blockedCount = 0;
        var byReader = overdue.stream().collect(java.util.stream.Collectors.groupingBy(Loan::getReaderId));
        for (var entry : byReader.entrySet()) {
            Long readerId = entry.getKey();
            for (Loan loan : entry.getValue()) {
                notify.notifyUser(readerId, "OVERDUE", "notice.overdue", titleOf(loan),
                        CirculationService.overdueDays(loan.getDueAt(), now));
            }
            int threshold = profiles.findById(readerId)
                    .flatMap(p -> policies.findByReaderType(p.getReaderType()))
                    .map(LoanPolicy::getBlockOverdueCount)
                    .orElse(Integer.MAX_VALUE);
            if (entry.getValue().size() >= threshold) {
                User user = users.findById(readerId).orElse(null);
                if (user != null && user.getStatus() != UserStatus.BLOCKED) {
                    user.setStatus(UserStatus.BLOCKED);
                    blockedCount++;
                    notify.notifyUser(readerId, "BLOCKED", "notice.blocked", entry.getValue().size());
                }
            }
        }
        return blockedCount;
    }

    // ---------- hold-shelf expiry ----------

    @Scheduled(cron = "0 0 * * * *")
    public void holdShelfHourly() {
        int expired = expireHoldShelf(Instant.now());
        if (expired > 0) {
            log.info("expired holds processed: {}", expired);
        }
    }

    @Transactional
    public int expireHoldShelf(Instant now) {
        List<Hold> expired = holds.findExpiredReady(now);
        for (Hold hold : expired) {
            hold.setStatus(HoldStatus.EXPIRED);
            notify.notifyUser(hold.getReaderId(), "HOLD_EXPIRED", "notice.holdExpired",
                    bookTitle(hold.getBookId()));
            // pass the copy to the next reader in the queue, or reshelve
            if (hold.getReadyCopyId() != null) {
                copies.findById(hold.getReadyCopyId()).ifPresent(copy -> {
                    if (copy.getStatus() != com.libris.domain.catalog.CopyStatus.ON_HOLD_SHELF) {
                        return;
                    }
                    Optional<Hold> next = holds.findFirstQueuedForUpdate(hold.getBookId());
                    if (next.isPresent()) {
                        Hold n = next.get();
                        n.setStatus(HoldStatus.READY);
                        n.setReadyCopyId(copy.getId());
                        n.setReadyAt(now);
                        n.setExpiresAt(now.plus(CirculationService.HOLD_SHELF_DAYS));
                        notify.notifyUser(n.getReaderId(), "HOLD_READY", "notice.holdReady",
                                bookTitle(n.getBookId()),
                                n.getExpiresAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate());
                    } else {
                        copy.setStatus(com.libris.domain.catalog.CopyStatus.IN_LIBRARY);
                    }
                });
            }
        }
        return expired.size();
    }

    // ---------- helpers ----------

    private String titleOf(Loan loan) {
        return copies.findById(loan.getCopyId())
                .flatMap(c -> books.findById(c.getBookId()))
                .map(Book::getTitle)
                .orElse("?");
    }

    private String bookTitle(Long bookId) {
        return books.findById(bookId).map(Book::getTitle).orElse("?");
    }
}
