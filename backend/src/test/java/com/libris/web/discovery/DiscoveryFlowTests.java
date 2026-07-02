package com.libris.web.discovery;

import com.libris.TestcontainersConfiguration;
import com.libris.domain.circulation.HoldRepository;
import com.libris.domain.circulation.HoldStatus;
import com.libris.domain.user.UserRepository;
import com.libris.service.circulation.CirculationService;
import com.libris.service.circulation.HoldService;
import com.libris.service.discovery.DiscoveryService;
import com.libris.util.HoldTestSupport;
import com.libris.web.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DiscoveryFlowTests {

    @Autowired DiscoveryService discovery;
    @Autowired HoldService holds;
    @Autowired CirculationService circulation;
    @Autowired UserRepository users;
    @Autowired HoldRepository holdRepository;

    private Long id(String username) {
        return users.findByUsername(username).orElseThrow().getId();
    }

    @Test
    void fullTextAndFuzzySearchFindSeededBooks() {
        // exact token
        var exact = discovery.searchBooks("三体", null, null, null, null, false, "relevance", 0, 10);
        assertThat(exact.content()).isNotEmpty();
        assertThat(exact.content().get(0).title()).contains("三体");
        assertThat(exact.total()).isGreaterThanOrEqualTo(3);

        // fuzzy substring on author
        var author = discovery.searchBooks("东野", null, null, null, null, false, "relevance", 0, 10);
        assertThat(author.content()).anyMatch(hit -> hit.author().contains("东野圭吾"));

        // english tokens
        var english = discovery.searchBooks("algorithms", null, null, null, null, false, "relevance", 0, 10);
        assertThat(english.content()).anyMatch(hit -> hit.title().toLowerCase().contains("algorithms"));

        // facets reflect the filtered universe
        assertThat(exact.facets().byCategory()).isNotEmpty();
        assertThat(exact.facets().total()).isEqualTo(exact.total());
    }

    @Test
    void facetFiltersNarrowResults() {
        var cs = discovery.searchBooks(null, 18, "英文", null, null, false, "title", 0, 50);
        assertThat(cs.content()).isNotEmpty();
        assertThat(cs.content()).allMatch(hit -> hit.language().equals("英文"));

        var recent = discovery.searchBooks(null, null, null, 2015, 2025, false, "newest", 0, 50);
        assertThat(recent.content()).allMatch(hit ->
                hit.pubDate() == null || hit.pubDate().getYear() >= 2015);
    }

    @Test
    void suggestionsReturnTitlesAndAuthors() {
        var s = discovery.suggest("时间简");
        assertThat(s).anyMatch(x -> x.text().contains("时间简史"));
    }

    @Test
    void holdLifecycle_placeRequiresNoShelfCopy_cancelReadyPromotesNext() {
        Long admin = id("admin");
        Long zhang = id("zhanghua");
        Long li = id("liyichen");
        Long wang = id("wangwaner");

        // 围城 has copies LB000043/44 (book 22)
        ApiException early = catchThrowableOfType(ApiException.class, () -> holds.place(22L, zhang));
        assertThat(early.getMessageKey()).isEqualTo("error.hold.copiesAvailable");

        circulation.checkout("LB000043", zhang, admin);
        circulation.checkout("LB000044", li, admin);

        // borrower cannot hold their own loan
        ApiException owner = catchThrowableOfType(ApiException.class, () -> holds.place(22L, zhang));
        assertThat(owner.getMessageKey()).isEqualTo("error.hold.alreadyBorrowing");

        var placed = holds.place(22L, wang);
        assertThat(placed.queuePosition()).isEqualTo(1);

        Long fei = id("lierfei");
        var second = holds.place(22L, fei);
        assertThat(second.queuePosition()).isEqualTo(2);

        // return: copy goes to hold shelf for wang; cancelling wang's READY hold promotes fei
        var in = circulation.checkin("LB000043", admin);
        assertThat(in.routing()).isEqualTo(CirculationService.Routing.TO_HOLD_SHELF);
        var wangHold = HoldTestSupport.activeHold(holdRepository, 22L, wang);
        assertThat(wangHold.getStatus()).isEqualTo(HoldStatus.READY);

        holds.cancel(wangHold.getId(), wang);
        var feiHold = HoldTestSupport.activeHold(holdRepository, 22L, fei);
        assertThat(feiHold.getStatus()).isEqualTo(HoldStatus.READY);
        assertThat(feiHold.getReadyCopyId()).isEqualTo(wangHold.getReadyCopyId());
    }
}
