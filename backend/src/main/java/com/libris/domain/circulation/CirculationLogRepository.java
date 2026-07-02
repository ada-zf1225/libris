package com.libris.domain.circulation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CirculationLogRepository extends JpaRepository<CirculationLog, Long> {

    Page<CirculationLog> findByOrderByCreatedAtDesc(Pageable pageable);
}
