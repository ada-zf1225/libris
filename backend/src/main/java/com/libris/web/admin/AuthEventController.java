package com.libris.web.admin;

import com.libris.domain.user.AuthEvent;
import com.libris.domain.user.AuthEventRepository;
import com.libris.web.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/admin/auth-events")
@RequiredArgsConstructor
public class AuthEventController {

    private final AuthEventRepository events;

    public record EventView(Long id, Long userId, String username, String type, String ip,
                            String userAgent, Instant createdAt) {
        static EventView of(AuthEvent e) {
            return new EventView(e.getId(), e.getUserId(), e.getUsername(), e.getType(), e.getIp(),
                    e.getUserAgent(), e.getCreatedAt());
        }
    }

    @GetMapping
    public PageResponse<EventView> list(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(events.findByOrderByCreatedAtDesc(
                PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)))), EventView::of);
    }
}
