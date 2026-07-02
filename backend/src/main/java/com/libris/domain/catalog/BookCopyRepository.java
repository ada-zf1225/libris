package com.libris.domain.catalog;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookCopyRepository extends JpaRepository<BookCopy, Long> {

    Optional<BookCopy> findByBarcode(String barcode);

    List<BookCopy> findByBookIdOrderById(Long bookId);

    long countByBookIdAndStatus(Long bookId, CopyStatus status);

    /**
     * Row-locked lookup used by every circulation mutation: serialises
     * concurrent desk operations on the same physical copy.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from BookCopy c where c.barcode = :barcode")
    Optional<BookCopy> findByBarcodeForUpdate(@Param("barcode") String barcode);

    @Query(value = "select nextval('barcode_seq')", nativeQuery = true)
    long nextBarcodeNumber();
}
