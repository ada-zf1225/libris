package com.libris.domain.patron;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseSuggestionRepository extends JpaRepository<PurchaseSuggestion, Long> {

    List<PurchaseSuggestion> findByReaderIdOrderByCreatedAtDesc(Long readerId);

    Page<PurchaseSuggestion> findByStatusOrderByCreatedAtAsc(PurchaseSuggestion.Status status, Pageable pageable);

    Page<PurchaseSuggestion> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
