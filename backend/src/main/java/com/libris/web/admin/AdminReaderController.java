package com.libris.web.admin;

import com.libris.domain.user.ReaderProfile;
import com.libris.domain.user.ReaderProfileRepository;
import com.libris.domain.user.ReaderType;
import com.libris.domain.user.User;
import com.libris.service.circulation.CirculationQueryService;
import com.libris.service.patron.PatronService;
import com.libris.web.dto.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@org.springframework.security.access.prepost.PreAuthorize("hasAuthority('PERM_MANAGE_READERS')")
@RestController
@RequestMapping("/api/admin/readers")
@RequiredArgsConstructor
public class AdminReaderController {

    private final PatronService patrons;
    private final ReaderProfileRepository profiles;
    private final CirculationQueryService query;

    public record ReaderRequest(
            @NotBlank @Size(min = 3, max = 32) @Pattern(regexp = "[a-zA-Z0-9_]+") String username,
            @NotBlank @Size(max = 64) String displayName,
            @Email @Size(max = 128) String email,
            @Size(max = 32) String phone,
            @NotNull ReaderType readerType,
            @Size(max = 8) String sex,
            LocalDate birth,
            @Size(max = 128) String address,
            // required on create, ignored on update
            @Size(min = 8, max = 128) String initialPassword) {

        PatronService.ReaderInput toInput() {
            return new PatronService.ReaderInput(username, displayName, email, phone, readerType,
                    sex, birth, address, initialPassword);
        }
    }

    public record ReaderView(Long id, String username, String displayName, String status, String email,
                             String phone, String readerType, String sex, LocalDate birth, String address) {}

    @GetMapping
    public PageResponse<ReaderView> list(@RequestParam(required = false) String q,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)),
                Sort.by(Sort.Direction.ASC, "id"));
        var result = patrons.search(q, pageable);
        Map<Long, ReaderProfile> profileMap = profiles
                .findAllById(result.getContent().stream().map(User::getId).toList())
                .stream().collect(Collectors.toMap(ReaderProfile::getUserId, Function.identity()));
        return PageResponse.of(result, u -> toView(u, profileMap.get(u.getId())));
    }

    @GetMapping("/{id}")
    public ReaderView get(@PathVariable Long id) {
        User user = patrons.get(id);
        return toView(user, profiles.findById(id).orElse(null));
    }

    @GetMapping("/{id}/loans")
    public List<CirculationQueryService.LoanView> activeLoans(@PathVariable Long id) {
        patrons.get(id);
        return query.activeLoansOf(id);
    }

    @GetMapping("/{id}/fines")
    public List<CirculationQueryService.FineView> fines(@PathVariable Long id) {
        patrons.get(id);
        return query.finesOf(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReaderView create(@Valid @RequestBody ReaderRequest body) {
        if (body.initialPassword() == null || body.initialPassword().isBlank()) {
            throw com.libris.web.error.ApiException.badRequest("error.validation");
        }
        User user = patrons.create(body.toInput());
        return toView(user, profiles.findById(user.getId()).orElse(null));
    }

    @PutMapping("/{id}")
    public ReaderView update(@PathVariable Long id, @Valid @RequestBody ReaderRequest body) {
        User user = patrons.update(id, body.toInput());
        return toView(user, profiles.findById(id).orElse(null));
    }

    @PostMapping("/{id}/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void block(@PathVariable Long id) {
        patrons.setBlocked(id, true);
    }

    @PostMapping("/{id}/unblock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@PathVariable Long id) {
        patrons.setBlocked(id, false);
    }

    private ReaderView toView(User u, ReaderProfile p) {
        return new ReaderView(u.getId(), u.getUsername(), u.getDisplayName(), u.getStatus().name(),
                u.getEmail(), u.getPhone(),
                p == null ? null : p.getReaderType().name(),
                p == null ? null : p.getSex(),
                p == null ? null : p.getBirth(),
                p == null ? null : p.getAddress());
    }
}
