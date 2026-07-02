package com.libris.util;

import com.libris.domain.circulation.Hold;
import com.libris.domain.circulation.HoldRepository;
import com.libris.domain.circulation.HoldStatus;

import java.util.List;

public final class HoldTestSupport {

    private HoldTestSupport() {
    }

    public static Hold activeHold(HoldRepository repository, Long bookId, Long readerId) {
        return repository.findByReaderIdOrderByCreatedAtDesc(readerId).stream()
                .filter(h -> h.getBookId().equals(bookId))
                .filter(h -> h.getStatus() == HoldStatus.QUEUED || h.getStatus() == HoldStatus.READY)
                .findFirst()
                .orElseThrow();
    }
}
