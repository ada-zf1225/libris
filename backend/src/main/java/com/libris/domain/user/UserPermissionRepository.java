package com.libris.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserPermissionRepository extends JpaRepository<UserPermission, UserPermission.Key> {

    List<UserPermission> findByUserId(Long userId);

    @Modifying
    @Query("delete from UserPermission p where p.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
