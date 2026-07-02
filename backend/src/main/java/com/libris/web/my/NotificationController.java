package com.libris.web.my;

import com.libris.domain.patron.Notification;
import com.libris.domain.patron.NotificationRepository;
import com.libris.security.SecurityUser;
import com.libris.web.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/my/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notifications;

    public record NotificationView(Long id, String type, String title, String body,
                                   Instant readAt, Instant createdAt) {
        static NotificationView of(Notification n) {
            return new NotificationView(n.getId(), n.getType(), n.getTitle(), n.getBody(),
                    n.getReadAt(), n.getCreatedAt());
        }
    }

    @GetMapping
    public PageResponse<NotificationView> list(@AuthenticationPrincipal SecurityUser principal,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        var pageable = PageRequest.of(Math.max(0, page), Math.min(50, Math.max(1, size)));
        return PageResponse.of(notifications.findByUserIdOrderByCreatedAtDesc(principal.getId(), pageable),
                NotificationView::of);
    }

    @GetMapping("/unread-count")
    public long unreadCount(@AuthenticationPrincipal SecurityUser principal) {
        return notifications.countByUserIdAndReadAtIsNull(principal.getId());
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void markRead(@PathVariable Long id, @AuthenticationPrincipal SecurityUser principal) {
        notifications.findById(id)
                .filter(n -> n.getUserId().equals(principal.getId()))
                .ifPresent(n -> {
                    if (n.getReadAt() == null) {
                        n.setReadAt(Instant.now());
                    }
                });
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void markAllRead(@AuthenticationPrincipal SecurityUser principal) {
        notifications.markAllRead(principal.getId());
    }
}
