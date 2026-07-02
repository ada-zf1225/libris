package com.libris.service.patron;

import com.libris.domain.user.*;
import com.libris.web.error.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class PatronService {

    private final UserRepository users;
    private final ReaderProfileRepository profiles;
    private final PasswordEncoder passwordEncoder;

    public record ReaderInput(String username, String displayName, String email, String phone,
                              ReaderType readerType, String sex, LocalDate birth, String address,
                              String initialPassword) {}

    @Transactional(readOnly = true)
    public Page<User> search(String q, Pageable pageable) {
        Specification<User> spec = (root, query, cb) -> cb.equal(root.get("role"), Role.READER);
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("username")), like),
                    cb.like(cb.lower(root.get("displayName")), like)));
        }
        return users.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public User get(Long id) {
        return users.findById(id)
                .filter(u -> u.getRole() == Role.READER)
                .orElseThrow(() -> ApiException.notFound("error.circulation.readerNotFound"));
    }

    @Transactional(readOnly = true)
    public User getByKey(String key) {
        return users.findByUsername(key)
                .filter(u -> u.getRole() == Role.READER)
                .or(() -> {
                    try {
                        return users.findById(Long.parseLong(key)).filter(u -> u.getRole() == Role.READER);
                    } catch (NumberFormatException e) {
                        return java.util.Optional.empty();
                    }
                })
                .orElseThrow(() -> ApiException.notFound("error.circulation.readerNotFound"));
    }

    @Transactional
    public User create(ReaderInput input) {
        if (users.existsByUsername(input.username())) {
            throw ApiException.conflict("error.patron.usernameTaken");
        }
        User user = new User(input.username(), passwordEncoder.encode(input.initialPassword()),
                input.displayName(), Role.READER, input.email());
        user.setPhone(input.phone());
        users.save(user);
        profiles.save(new ReaderProfile(user.getId(), input.readerType(), input.sex(), input.birth(),
                input.address()));
        return user;
    }

    @Transactional
    public User update(Long id, ReaderInput input) {
        User user = get(id);
        user.setDisplayName(input.displayName());
        user.setEmail(input.email());
        user.setPhone(input.phone());
        ReaderProfile profile = profiles.findById(id)
                .orElseThrow(() -> ApiException.notFound("error.circulation.readerNotFound"));
        profile.setReaderType(input.readerType());
        profile.setSex(input.sex());
        profile.setBirth(input.birth());
        profile.setAddress(input.address());
        return user;
    }

    @Transactional
    public void setBlocked(Long id, boolean blocked) {
        User user = get(id);
        user.setStatus(blocked ? UserStatus.BLOCKED : UserStatus.ACTIVE);
    }
}
