package com.libris.security;

import com.libris.domain.user.Permission;
import com.libris.domain.user.User;
import com.libris.domain.user.UserStatus;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SecurityUser implements UserDetails {

    @Getter
    private final Long id;
    private final String username;
    private final String passwordHash;
    private final boolean enabled;
    private final List<GrantedAuthority> authorities;

    public SecurityUser(User user, Set<Permission> permissions) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.passwordHash = user.getPasswordHash();
        this.enabled = user.getStatus() != UserStatus.DISABLED;
        List<GrantedAuthority> auths = new ArrayList<>();
        auths.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        for (Permission permission : permissions) {
            auths.add(new SimpleGrantedAuthority("PERM_" + permission.name()));
        }
        this.authorities = List.copyOf(auths);
    }

    @Override
    public List<GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isAccountNonLocked() {
        return enabled;
    }
}
