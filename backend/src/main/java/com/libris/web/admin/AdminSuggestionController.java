package com.libris.web.admin;

import com.libris.domain.patron.PurchaseSuggestion;
import com.libris.domain.patron.PurchaseSuggestionRepository;
import com.libris.domain.user.User;
import com.libris.domain.user.UserRepository;
import com.libris.security.SecurityUser;
import com.libris.web.dto.PageResponse;
import com.libris.web.error.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@org.springframework.security.access.prepost.PreAuthorize("hasAuthority('PERM_MANAGE_SUGGESTIONS')")
@RestController
@RequestMapping("/api/admin/suggestions")
@RequiredArgsConstructor
public class AdminSuggestionController {

    private final PurchaseSuggestionRepository suggestions;
    private final UserRepository users;
    private final com.libris.service.notify.NotificationService notify;

    public record HandleRequest(@Size(max = 512) String reply) {}

    public record AdminSuggestionView(Long id, Long readerId, String readerName, String title,
                                      String author, String isbn, String reason, String status,
                                      String reply, Instant createdAt) {}

    @GetMapping
    public PageResponse<AdminSuggestionView> list(@RequestParam(required = false) String status,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));
        var result = (status == null || status.isBlank())
                ? suggestions.findAllByOrderByCreatedAtDesc(pageable)
                : suggestions.findByStatusOrderByCreatedAtAsc(
                        PurchaseSuggestion.Status.valueOf(status), pageable);
        Map<Long, String> names = users.findAllById(
                        result.getContent().stream().map(PurchaseSuggestion::getReaderId).toList())
                .stream().collect(Collectors.toMap(User::getId, User::getDisplayName, (a, b) -> a));
        return PageResponse.of(result, s -> new AdminSuggestionView(s.getId(), s.getReaderId(),
                names.getOrDefault(s.getReaderId(), "?"), s.getTitle(), s.getAuthor(), s.getIsbn(),
                s.getReason(), s.getStatus().name(), s.getReply(), s.getCreatedAt()));
    }

    @PostMapping("/{id}/approve")
    @Transactional
    public void approve(@PathVariable Long id, @Valid @RequestBody(required = false) HandleRequest body,
                        @AuthenticationPrincipal SecurityUser operator) {
        handle(id, PurchaseSuggestion.Status.APPROVED, body, operator);
    }

    @PostMapping("/{id}/reject")
    @Transactional
    public void reject(@PathVariable Long id, @Valid @RequestBody(required = false) HandleRequest body,
                       @AuthenticationPrincipal SecurityUser operator) {
        handle(id, PurchaseSuggestion.Status.REJECTED, body, operator);
    }

    private void handle(Long id, PurchaseSuggestion.Status status, HandleRequest body, SecurityUser operator) {
        PurchaseSuggestion s = suggestions.findById(id)
                .orElseThrow(() -> ApiException.notFound("error.notFound"));
        if (s.getStatus() != PurchaseSuggestion.Status.PENDING) {
            throw ApiException.conflict("error.suggestion.handled");
        }
        s.setStatus(status);
        s.setHandledBy(operator.getId());
        s.setHandledAt(Instant.now());
        s.setReply(body == null ? null : body.reply());
        notify.notifyUser(s.getReaderId(), "SUGGESTION", "notice.suggestionHandled",
                s.getTitle(), status == PurchaseSuggestion.Status.APPROVED ? "采纳 / approved" : "婉拒 / declined",
                s.getReply() == null ? "" : s.getReply());
    }
}
