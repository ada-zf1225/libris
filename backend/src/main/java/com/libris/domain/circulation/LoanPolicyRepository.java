package com.libris.domain.circulation;

import com.libris.domain.user.ReaderType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LoanPolicyRepository extends JpaRepository<LoanPolicy, Long> {

    Optional<LoanPolicy> findByReaderType(ReaderType readerType);
}
