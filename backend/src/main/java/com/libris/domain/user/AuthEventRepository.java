package com.libris.domain.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthEventRepository extends JpaRepository<AuthEvent, Long> {

    Page<AuthEvent> findByOrderByCreatedAtDesc(Pageable pageable);
}
