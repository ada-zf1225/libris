package com.libris.domain.patron;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    List<Favorite> findByReaderIdOrderByCreatedAtDesc(Long readerId);

    Optional<Favorite> findByReaderIdAndBookId(Long readerId, Long bookId);

    boolean existsByReaderIdAndBookId(Long readerId, Long bookId);
}
