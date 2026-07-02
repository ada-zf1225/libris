package com.libris.domain.circulation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    Optional<Loan> findByCopyIdAndReturnedAtIsNull(Long copyId);

    List<Loan> findByReaderIdAndReturnedAtIsNullOrderByDueAtAsc(Long readerId);

    Page<Loan> findByReaderIdAndReturnedAtIsNotNull(Long readerId, Pageable pageable);

    long countByReaderIdAndReturnedAtIsNull(Long readerId);

    @Query("""
            select count(l) from Loan l
            where l.readerId = :readerId and l.returnedAt is null and l.dueAt < :now
            """)
    long countOverdue(@Param("readerId") Long readerId, @Param("now") Instant now);

    @Query("select l from Loan l where l.returnedAt is null and l.dueAt between :from and :to")
    List<Loan> findDueBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select l from Loan l where l.returnedAt is null and l.dueAt < :now")
    List<Loan> findOverdue(@Param("now") Instant now);
}
