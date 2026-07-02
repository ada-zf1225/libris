package com.libris.domain.circulation;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HoldRepository extends JpaRepository<Hold, Long> {

    List<Hold> findByReaderIdOrderByCreatedAtDesc(Long readerId);

    boolean existsByBookIdAndReaderIdAndStatusIn(Long bookId, Long readerId, List<HoldStatus> statuses);

    boolean existsByBookIdAndStatusIn(Long bookId, List<HoldStatus> statuses);

    long countByBookIdAndStatus(Long bookId, HoldStatus status);

    long countByBookIdAndStatusAndQueuedAtBefore(Long bookId, HoldStatus status, java.time.Instant queuedAt);

    Optional<Hold> findByReadyCopyIdAndStatus(Long copyId, HoldStatus status);

    @Query("select h from Hold h where h.status = 'READY' and h.expiresAt < :now")
    List<Hold> findExpiredReady(@Param("now") java.time.Instant now);

    /** Oldest queued hold for a title — locked so two returns can't promote the same hold twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select h from Hold h
            where h.bookId = :bookId and h.status = 'QUEUED'
            order by h.queuedAt asc
            limit 1
            """)
    Optional<Hold> findFirstQueuedForUpdate(@Param("bookId") Long bookId);
}
