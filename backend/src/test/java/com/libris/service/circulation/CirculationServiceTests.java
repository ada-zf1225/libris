package com.libris.service.circulation;

import com.libris.TestcontainersConfiguration;
import com.libris.domain.catalog.BookCopyRepository;
import com.libris.domain.catalog.CopyStatus;
import com.libris.domain.circulation.*;
import com.libris.domain.user.UserRepository;
import com.libris.web.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Circulation core against real PostgreSQL: full lifecycle, policy rules and
 * the concurrency guarantee that one physical copy can never be lent twice.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CirculationServiceTests {

    @Autowired CirculationService circulation;
    @Autowired UserRepository users;
    @Autowired BookCopyRepository copies;
    @Autowired LoanRepository loans;
    @Autowired HoldRepository holds;
    @Autowired FineRepository fines;

    private Long reader(String username) {
        return users.findByUsername(username).orElseThrow().getId();
    }

    private Long admin() {
        return reader("admin");
    }

    @Test
    void fullLifecycle_checkout_renew_holdBlocksRenewal_checkin_promotesHold() {
        Long zhang = reader("zhanghua");
        Long li = reader("liyichen");

        // LB000031: the only two copies of 三体 are LB000031/LB000032 (book id 16)
        var out = circulation.checkout("LB000031", zhang, admin());
        assertThat(out.bookTitle()).contains("三体");
        assertThat(copies.findByBarcode("LB000031").orElseThrow().getStatus()).isEqualTo(CopyStatus.ON_LOAN);

        // renewal works while nobody holds the title
        var renewed = circulation.renew(out.loanId(), null, admin());
        assertThat(renewed.renewCount()).isEqualTo(1);

        // second reader takes the last copy, third places a hold → renewal now blocked
        circulation.checkout("LB000032", li, admin());
        Long wang = reader("wangwaner");
        holds.save(new Hold(copies.findByBarcode("LB000031").orElseThrow().getBookId(), wang, Instant.now()));

        ApiException blocked = catchThrowableOfType(ApiException.class,
                () -> circulation.renew(out.loanId(), null, admin()));
        assertThat(blocked.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(blocked.getMessageKey()).isEqualTo("error.circulation.holdBlocksRenewal");

        // check-in routes the copy to the hold shelf for the queued reader
        var in = circulation.checkin("LB000031", admin());
        assertThat(in.routing()).isEqualTo(CirculationService.Routing.TO_HOLD_SHELF);
        assertThat(in.holdReaderName()).isEqualTo("王莞尔");
        var copy = copies.findByBarcode("LB000031").orElseThrow();
        assertThat(copy.getStatus()).isEqualTo(CopyStatus.ON_HOLD_SHELF);

        // another reader cannot take a copy reserved on the shelf
        ApiException wrongReader = catchThrowableOfType(ApiException.class,
                () -> circulation.checkout("LB000031", zhang, admin()));
        assertThat(wrongReader.getMessageKey()).isEqualTo("error.circulation.heldForAnotherReader");

        // the hold owner picks it up: hold fulfilled, loan created
        var pickup = circulation.checkout("LB000031", wang, admin());
        assertThat(pickup.fulfilledHold()).isTrue();
        assertThat(holds.findByReaderIdOrderByCreatedAtDesc(wang).get(0).getStatus())
                .isEqualTo(HoldStatus.FULFILLED);
    }

    @Test
    void overdueLoanProducesFineOnReturn_andCannotRenew() {
        Long reader = reader("lierfei");
        var out = circulation.checkout("LB000037", reader, admin()); // 活着 copy 1

        // simulate an 8-days-overdue loan
        Loan loan = loans.findById(out.loanId()).orElseThrow();
        loan.setDueAt(Instant.now().minus(8, ChronoUnit.DAYS));
        loans.save(loan);

        ApiException renewDenied = catchThrowableOfType(ApiException.class,
                () -> circulation.renew(loan.getId(), null, admin()));
        assertThat(renewDenied.getMessageKey()).isEqualTo("error.circulation.overdueNotRenewable");

        var in = circulation.checkin("LB000037", admin());
        assertThat(in.overdueDays()).isEqualTo(8);
        assertThat(in.fineCents()).isEqualTo(80); // 8 days × 10 分

        List<Fine> readerFines = fines.findByReaderIdAndStatus(reader, Fine.Status.UNPAID);
        assertThat(readerFines).hasSize(1);
        assertThat(readerFines.get(0).getReason()).isEqualTo(Fine.Reason.OVERDUE);

        circulation.payFine(readerFines.get(0).getId(), admin());
        assertThat(fines.unpaidTotal(reader)).isZero();
    }

    @Test
    void markLost_closesLoanAndChargesReplacement() {
        Long reader = reader("zhangminghua");
        circulation.checkout("LB000039", reader, admin()); // 百年孤独 copy 1
        var lost = circulation.markLost("LB000039", admin());
        assertThat(lost.fineCents()).isEqualTo(3950);
        assertThat(copies.findByBarcode("LB000039").orElseThrow().getStatus()).isEqualTo(CopyStatus.LOST);
        assertThat(loans.findByCopyIdAndReturnedAtIsNull(
                copies.findByBarcode("LB000039").orElseThrow().getId())).isEmpty();
    }

    @Test
    void concurrentCheckoutOfSameCopy_onlyOneWins() throws Exception {
        Long zhang = reader("zhanghua");
        Long li = reader("liyichen");
        String barcode = "LB000041"; // 红楼梦 copy 1

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        List<Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Long readerId = i % 2 == 0 ? zhang : li;
            futures.add(pool.submit(() -> {
                try {
                    start.await();
                    circulation.checkout(barcode, readerId, admin());
                    successes.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    conflicts.incrementAndGet();
                }
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(successes.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(threads - 1);
        assertThat(copies.findByBarcode(barcode).orElseThrow().getStatus()).isEqualTo(CopyStatus.ON_LOAN);
    }
}
