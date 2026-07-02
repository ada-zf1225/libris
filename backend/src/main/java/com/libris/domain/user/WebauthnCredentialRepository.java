package com.libris.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WebauthnCredentialRepository extends JpaRepository<WebauthnCredential, String> {

    List<WebauthnCredential> findByUserId(Long userId);

    List<WebauthnCredential> findByUserEntityId(String userEntityId);
}
