package com.libris.web.admin;

import com.libris.domain.circulation.CirculationLog;
import com.libris.domain.circulation.CirculationLogRepository;
import com.libris.web.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@org.springframework.security.access.prepost.PreAuthorize("hasAuthority('PERM_VIEW_LOGS')")
@RestController
@RequestMapping("/api/admin/circulation-logs")
@RequiredArgsConstructor
public class CirculationLogController {

    private final CirculationLogRepository logs;

    public record LogView(Long id, Long operatorId, String action, Long copyId, Long readerId,
                          Map<String, Object> detail, Instant createdAt) {
        static LogView of(CirculationLog log) {
            return new LogView(log.getId(), log.getOperatorId(), log.getAction(), log.getCopyId(),
                    log.getReaderId(), log.getDetail(), log.getCreatedAt());
        }
    }

    @GetMapping
    public PageResponse<LogView> list(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));
        return PageResponse.of(logs.findByOrderByCreatedAtDesc(pageable), LogView::of);
    }
}
