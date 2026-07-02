package com.libris.service.circulation;

import com.libris.domain.catalog.BookCopy;
import com.libris.domain.catalog.BookRepository;
import com.libris.domain.catalog.BookCopyRepository;
import com.libris.domain.catalog.CopyStatus;
import com.libris.domain.circulation.*;
import com.libris.web.error.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class HoldService {

    private final HoldRepository holds;
    private final BookRepository books;
    private final BookCopyRepository copies;
    private final LoanRepository loans;

    public record PlacedHold(Long holdId, long queuePosition) {}

    /** Holds follow the IC-library rule: you can only request a title with no copy on the shelf. */
    @Transactional
    public PlacedHold place(Long bookId, Long readerId) {
        books.findById(bookId).orElseThrow(() -> ApiException.notFound("error.catalog.bookNotFound"));
        if (copies.countByBookIdAndStatus(bookId, CopyStatus.IN_LIBRARY) > 0) {
            throw ApiException.conflict("error.hold.copiesAvailable");
        }
        if (holds.existsByBookIdAndReaderIdAndStatusIn(bookId, readerId,
                List.of(HoldStatus.QUEUED, HoldStatus.READY))) {
            throw ApiException.conflict("error.hold.alreadyHolding");
        }
        boolean borrowingIt = loans.findByReaderIdAndReturnedAtIsNullOrderByDueAtAsc(readerId).stream()
                .anyMatch(l -> copies.findById(l.getCopyId())
                        .map(c -> c.getBookId().equals(bookId)).orElse(false));
        if (borrowingIt) {
            throw ApiException.conflict("error.hold.alreadyBorrowing");
        }
        Hold hold = holds.save(new Hold(bookId, readerId, Instant.now()));
        long position = holds.countByBookIdAndStatus(bookId, HoldStatus.QUEUED);
        return new PlacedHold(hold.getId(), position);
    }

    @Transactional
    public void cancel(Long holdId, Long readerId) {
        Hold hold = holds.findById(holdId)
                .filter(h -> h.getReaderId().equals(readerId))
                .orElseThrow(() -> ApiException.notFound("error.notFound"));
        switch (hold.getStatus()) {
            case QUEUED -> hold.setStatus(HoldStatus.CANCELLED);
            case READY -> {
                hold.setStatus(HoldStatus.CANCELLED);
                releaseShelfCopy(hold);
            }
            default -> throw ApiException.conflict("error.hold.notActive");
        }
    }

    /** A READY hold was cancelled/expired: hand its copy to the next in queue or reshelve. */
    void releaseShelfCopy(Hold hold) {
        if (hold.getReadyCopyId() == null) {
            return;
        }
        BookCopy copy = copies.findById(hold.getReadyCopyId()).orElse(null);
        if (copy == null || copy.getStatus() != CopyStatus.ON_HOLD_SHELF) {
            return;
        }
        Optional<Hold> next = holds.findFirstQueuedForUpdate(hold.getBookId());
        if (next.isPresent()) {
            Hold n = next.get();
            Instant now = Instant.now();
            n.setStatus(HoldStatus.READY);
            n.setReadyCopyId(copy.getId());
            n.setReadyAt(now);
            n.setExpiresAt(now.plus(CirculationService.HOLD_SHELF_DAYS));
        } else {
            copy.setStatus(CopyStatus.IN_LIBRARY);
        }
    }

    @Transactional(readOnly = true)
    public long queuePosition(Hold hold) {
        if (hold.getStatus() != HoldStatus.QUEUED) {
            return 0;
        }
        return holds.countByBookIdAndStatusAndQueuedAtBefore(
                hold.getBookId(), HoldStatus.QUEUED, hold.getQueuedAt()) + 1;
    }
}
