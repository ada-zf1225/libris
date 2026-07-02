package com.libris.domain.circulation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FineRepository extends JpaRepository<Fine, Long> {

    List<Fine> findByReaderIdOrderByCreatedAtDesc(Long readerId);

    List<Fine> findByReaderIdAndStatus(Long readerId, Fine.Status status);

    @Query("""
            select coalesce(sum(f.amountCents), 0) from Fine f
            where f.readerId = :readerId and f.status = 'UNPAID'
            """)
    long unpaidTotal(@Param("readerId") Long readerId);
}
