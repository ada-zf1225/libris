package com.libris.jobs;

import com.libris.TestcontainersConfiguration;
import com.libris.domain.catalog.BookCopyRepository;
import com.libris.domain.catalog.CopyStatus;
import com.libris.domain.circulation.Fine;
import com.libris.domain.circulation.FineRepository;
import com.libris.domain.circulation.HoldRepository;
import com.libris.domain.circulation.HoldStatus;
import com.libris.domain.circulation.Loan;
import com.libris.domain.circulation.LoanRepository;
import com.libris.domain.patron.NotificationRepository;
import com.libris.domain.user.UserRepository;
import com.libris.domain.user.UserStatus;
import com.libris.service.circulation.CirculationService;
import com.libris.service.circulation.HoldService;
import com.libris.util.HoldTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CirculationJobsTests {

    @Autowired CirculationJobs jobs;
    @Autowired CirculationService circulation;
    @Autowired HoldService holdService;
    @Autowired LoanRepository loans;
    @Autowired HoldRepository holds;
    @Autowired UserRepository users;
    @Autowired BookCopyRepository copies;
    @Autowired NotificationRepository notifications;
    @Autowired FineRepository fines;

    private Long id(String username) {
        return users.findByUsername(username).orElseThrow().getId();
    }

    /**
     * Test classes share one database; earlier classes may leave unpaid fines
     * (e.g. a lost-item charge) that would trip the real-time fine block.
     * Settle them so this class only observes its own state.
     */
    private void settleFines(Long readerId) {
        for (Fine fine : fines.findByReaderIdAndStatus(readerId, Fine.Status.UNPAID)) {
            fine.setStatus(Fine.Status.PAID);
            fine.setPaidAt(Instant.now());
            fines.save(fine);
        }
    }

    private long notificationCount(Long userId, String type) {
        return notifications.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 100))
                .getContent().stream().filter(n -> n.getType().equals(type)).count();
    }

    @Test
    void dueSoonAndOverdueJobsNotifyAndAutoBlock() {
        Long admin = id("admin");
        Long reader = id("wangxiaowei"); // teacher, block threshold 3 overdue
        settleFines(reader);

        // one loan due tomorrow → courtesy notice
        var soonLoan = circulation.checkout("LB000045", reader, admin); // 平凡的世界 c1
        Loan soon = loans.findById(soonLoan.loanId()).orElseThrow();
        soon.setDueAt(Instant.now().plus(1, ChronoUnit.DAYS));
        loans.save(soon);

        int sent = jobs.sendDueSoonNotices(Instant.now());
        assertThat(sent).isGreaterThanOrEqualTo(1);
        assertThat(notificationCount(reader, "DUE_SOON")).isGreaterThanOrEqualTo(1);

        // three overdue loans → overdue notices + automatic block
        for (String barcode : new String[]{"LB000047", "LB000049", "LB000051"}) {
            var out = circulation.checkout(barcode, reader, admin);
            Loan loan = loans.findById(out.loanId()).orElseThrow();
            loan.setDueAt(Instant.now().minus(5, ChronoUnit.DAYS));
            loans.save(loan);
        }
        int blocked = jobs.escalateOverdue(Instant.now());
        assertThat(blocked).isEqualTo(1);
        assertThat(users.findById(reader).orElseThrow().getStatus()).isEqualTo(UserStatus.BLOCKED);
        assertThat(notificationCount(reader, "OVERDUE")).isGreaterThanOrEqualTo(3);
        assertThat(notificationCount(reader, "BLOCKED")).isEqualTo(1);
    }

    @Test
    void holdShelfExpiryPromotesNextInQueue() {
        Long admin = id("admin");
        Long zhang = id("zhanghua");
        Long ming = id("zhangminghua");
        Long yi = id("liyichen");
        settleFines(zhang);
        settleFines(ming);
        settleFines(yi);

        // book 24 白夜行: copies LB000047? no — (24-1)*2+1 = 47 taken above… use 解忧杂货店 book 25 → 49,50 also used.
        // 小王子 book 29 → barcodes LB000057/58
        circulation.checkout("LB000057", zhang, admin);
        circulation.checkout("LB000058", ming, admin);
        holdService.place(29L, yi);
        Long fei = id("lierfei");
        holdService.place(29L, fei);

        circulation.checkin("LB000057", admin); // → hold shelf for yi
        var yiHold = HoldTestSupport.activeHold(holds, 29L, yi);
        assertThat(yiHold.getStatus()).isEqualTo(HoldStatus.READY);

        // simulate 8 days later: yi never picked it up
        yiHold.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        holds.save(yiHold);

        int expired = jobs.expireHoldShelf(Instant.now());
        assertThat(expired).isEqualTo(1);
        assertThat(holds.findById(yiHold.getId()).orElseThrow().getStatus()).isEqualTo(HoldStatus.EXPIRED);

        var feiHold = HoldTestSupport.activeHold(holds, 29L, fei);
        assertThat(feiHold.getStatus()).isEqualTo(HoldStatus.READY);
        assertThat(copies.findById(feiHold.getReadyCopyId()).orElseThrow().getStatus())
                .isEqualTo(CopyStatus.ON_HOLD_SHELF);
        assertThat(notificationCount(yi, "HOLD_EXPIRED")).isEqualTo(1);
        assertThat(notificationCount(fei, "HOLD_READY")).isEqualTo(1);
    }
}
