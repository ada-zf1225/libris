package com.libris.web.admin;

import com.libris.domain.user.*;
import com.libris.web.dto.PageResponse;
import com.libris.web.error.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Staff management — SUPER_ADMIN only (enforced at the URL layer too). */
@RestController
@RequestMapping("/api/admin/staff")
@RequiredArgsConstructor
public class AdminStaffController {

    private final UserRepository users;
    private final UserPermissionRepository permissions;
    private final PasswordEncoder passwordEncoder;

    public record StaffRequest(
            @NotBlank @Size(min = 3, max = 32) @Pattern(regexp = "[a-zA-Z0-9_]+") String username,
            @NotBlank @Size(max = 64) String displayName,
            @Email @Size(max = 128) String email,
            @Size(min = 8, max = 128) String initialPassword,
            @NotNull Set<Permission> permissions) {}

    public record StaffView(Long id, String username, String displayName, String email, String role,
                            String status, boolean mfaEnabled, List<String> permissions) {}

    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<StaffView> list(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        Specification<User> staff = (root, query, cb) -> root.get("role").in(Role.SUPER_ADMIN, Role.LIBRARIAN);
        var result = users.findAll(staff, PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)),
                Sort.by("id")));
        Map<Long, List<String>> grants = permissions.findAll().stream()
                .collect(Collectors.groupingBy(UserPermission::getUserId,
                        Collectors.mapping(p -> p.getPermission().name(), Collectors.toList())));
        return PageResponse.of(result, u -> toView(u, grants.getOrDefault(u.getId(), List.of())));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public StaffView create(@Valid @RequestBody StaffRequest body) {
        if (body.initialPassword() == null || body.initialPassword().isBlank()) {
            throw ApiException.badRequest("error.validation");
        }
        if (users.existsByUsername(body.username())) {
            throw ApiException.conflict("error.patron.usernameTaken");
        }
        User user = new User(body.username(), passwordEncoder.encode(body.initialPassword()),
                body.displayName(), Role.LIBRARIAN, body.email());
        users.save(user);
        grant(user.getId(), body.permissions());
        return toView(user, body.permissions().stream().map(Enum::name).sorted().toList());
    }

    @PutMapping("/{id}")
    @Transactional
    public StaffView update(@PathVariable Long id, @Valid @RequestBody StaffRequest body,
                            @AuthenticationPrincipal com.libris.security.SecurityUser operator) {
        User user = librarian(id);
        user.setDisplayName(body.displayName());
        user.setEmail(body.email());
        if (body.initialPassword() != null && !body.initialPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(body.initialPassword()));
        }
        permissions.deleteByUserId(id);
        grant(id, body.permissions());
        return toView(user, body.permissions().stream().map(Enum::name).sorted().toList());
    }

    @PostMapping("/{id}/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void disable(@PathVariable Long id, @AuthenticationPrincipal com.libris.security.SecurityUser operator) {
        if (id.equals(operator.getId())) {
            throw ApiException.conflict("error.staff.selfDisable");
        }
        librarian(id).setStatus(UserStatus.DISABLED);
    }

    @PostMapping("/{id}/enable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void enable(@PathVariable Long id) {
        librarian(id).setStatus(UserStatus.ACTIVE);
    }

    private User librarian(Long id) {
        return users.findById(id)
                .filter(u -> u.getRole() == Role.LIBRARIAN)
                .orElseThrow(() -> ApiException.notFound("error.notFound"));
    }

    private void grant(Long userId, Set<Permission> set) {
        set.stream()
                .filter(p -> p != Permission.MANAGE_STAFF) // staff management stays with SUPER_ADMIN
                .forEach(p -> permissions.save(new UserPermission(userId, p)));
    }

    private StaffView toView(User u, List<String> grants) {
        return new StaffView(u.getId(), u.getUsername(), u.getDisplayName(), u.getEmail(),
                u.getRole().name(), u.getStatus().name(), u.isMfaEnabled(),
                u.getRole() == Role.SUPER_ADMIN
                        ? Permission.all().stream().map(Enum::name).sorted().toList()
                        : grants);
    }
}
