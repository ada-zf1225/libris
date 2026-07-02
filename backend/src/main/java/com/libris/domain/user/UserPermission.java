package com.libris.domain.user;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Entity
@Table(name = "user_permissions")
@IdClass(UserPermission.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPermission {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Permission permission;

    public UserPermission(Long userId, Permission permission) {
        this.userId = userId;
        this.permission = permission;
    }

    public record Key(Long userId, Permission permission) implements Serializable {
    }
}
