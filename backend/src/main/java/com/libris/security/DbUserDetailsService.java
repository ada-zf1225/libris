package com.libris.security;

import com.libris.domain.user.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DbUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserPermissionRepository permissionRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("user not found: " + username));
        return new SecurityUser(user, permissionsOf(user));
    }

    public Set<Permission> permissionsOf(User user) {
        return switch (user.getRole()) {
            case SUPER_ADMIN -> Permission.all();
            case LIBRARIAN -> {
                Set<Permission> granted = permissionRepository.findByUserId(user.getId()).stream()
                        .map(UserPermission::getPermission)
                        .collect(Collectors.toCollection(() -> EnumSet.noneOf(Permission.class)));
                yield granted;
            }
            case READER -> Set.of();
        };
    }
}
