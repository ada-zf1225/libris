package com.libris.web.my;

import com.libris.domain.catalog.Book;
import com.libris.domain.catalog.BookRepository;
import com.libris.domain.circulation.Fine;
import com.libris.domain.circulation.FineRepository;
import com.libris.domain.circulation.Hold;
import com.libris.domain.circulation.HoldRepository;
import com.libris.domain.circulation.LoanRepository;
import com.libris.domain.patron.Favorite;
import com.libris.domain.patron.FavoriteRepository;
import com.libris.domain.patron.PurchaseSuggestion;
import com.libris.domain.patron.PurchaseSuggestionRepository;
import com.libris.security.SecurityUser;
import com.libris.service.circulation.CirculationQueryService;
import com.libris.service.circulation.CirculationService;
import com.libris.service.circulation.HoldService;
import com.libris.web.dto.PageResponse;
import com.libris.web.error.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Patron self-service — the "My Library Card" of Libris. Every route is owner-scoped. */
@RestController
@RequestMapping("/api/my")
@RequiredArgsConstructor
public class MyLibraryController {

    private final CirculationQueryService query;
    private final CirculationService circulation;
    private final HoldService holdService;
    private final HoldRepository holds;
    private final LoanRepository loans;
    private final FineRepository fines;
    private final FavoriteRepository favorites;
    private final PurchaseSuggestionRepository suggestions;
    private final BookRepository books;

    // ---------- overview ----------

    public record Overview(int activeLoans, long overdue, long activeHolds, long unpaidFineCents,
                           long favorites) {}

    @GetMapping("/overview")
    public Overview overview(@AuthenticationPrincipal SecurityUser principal) {
        Long id = principal.getId();
        long activeHolds = holds.findByReaderIdOrderByCreatedAtDesc(id).stream()
                .filter(h -> h.getStatus().name().equals("QUEUED") || h.getStatus().name().equals("READY"))
                .count();
        return new Overview(
                (int) loans.countByReaderIdAndReturnedAtIsNull(id),
                loans.countOverdue(id, Instant.now()),
                activeHolds,
                fines.unpaidTotal(id),
                favorites.findByReaderIdOrderByCreatedAtDesc(id).size());
    }

    // ---------- loans ----------

    @GetMapping("/loans")
    public List<CirculationQueryService.LoanView> loans(@AuthenticationPrincipal SecurityUser principal) {
        return query.activeLoansOf(principal.getId());
    }

    @GetMapping("/loans/history")
    public PageResponse<CirculationQueryService.LoanView> history(
            @AuthenticationPrincipal SecurityUser principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        var pageable = PageRequest.of(Math.max(0, page), Math.min(50, Math.max(1, size)),
                Sort.by(Sort.Direction.DESC, "returnedAt"));
        var result = loans.findByReaderIdAndReturnedAtIsNotNull(principal.getId(), pageable);
        List<CirculationQueryService.LoanView> views = query.toLoanViews(result.getContent());
        return new PageResponse<>(views, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @PostMapping("/loans/{id}/renew")
    public CirculationService.RenewResult renew(@PathVariable Long id,
                                                @AuthenticationPrincipal SecurityUser principal) {
        return circulation.renew(id, principal.getId(), principal.getId());
    }

    // ---------- holds ----------

    public record HoldView(Long id, Long bookId, String bookTitle, String status,
                           Instant queuedAt, Instant expiresAt, long queuePosition) {}

    @GetMapping("/holds")
    public List<HoldView> myHolds(@AuthenticationPrincipal SecurityUser principal) {
        List<Hold> list = holds.findByReaderIdOrderByCreatedAtDesc(principal.getId());
        Map<Long, Book> bookMap = books.findAllById(list.stream().map(Hold::getBookId).toList())
                .stream().collect(Collectors.toMap(Book::getId, Function.identity()));
        return list.stream().map(h -> new HoldView(h.getId(), h.getBookId(),
                bookMap.containsKey(h.getBookId()) ? bookMap.get(h.getBookId()).getTitle() : "?",
                h.getStatus().name(), h.getQueuedAt(), h.getExpiresAt(),
                holdService.queuePosition(h))).toList();
    }

    @DeleteMapping("/holds/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelHold(@PathVariable Long id, @AuthenticationPrincipal SecurityUser principal) {
        holdService.cancel(id, principal.getId());
    }

    // ---------- fines (self-service mock payment) ----------

    @GetMapping("/fines")
    public List<CirculationQueryService.FineView> myFines(@AuthenticationPrincipal SecurityUser principal) {
        return query.finesOf(principal.getId());
    }

    @PostMapping("/fines/{id}/pay")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void payOwnFine(@PathVariable Long id, @AuthenticationPrincipal SecurityUser principal) {
        Fine fine = fines.findById(id)
                .filter(f -> f.getReaderId().equals(principal.getId()))
                .orElseThrow(() -> ApiException.notFound("error.notFound"));
        if (fine.getStatus() != Fine.Status.UNPAID) {
            throw ApiException.conflict("error.circulation.fineSettled");
        }
        // simulated payment — no real gateway by design (see SECURITY.md scope)
        fine.setStatus(Fine.Status.PAID);
        fine.setPaidAt(Instant.now());
    }

    // ---------- favorites ----------

    public record FavoriteView(Long id, Long bookId, String title, String author, String coverUrl,
                               Instant createdAt) {}

    @GetMapping("/favorites")
    public List<FavoriteView> myFavorites(@AuthenticationPrincipal SecurityUser principal) {
        List<Favorite> list = favorites.findByReaderIdOrderByCreatedAtDesc(principal.getId());
        Map<Long, Book> bookMap = books.findAllById(list.stream().map(Favorite::getBookId).toList())
                .stream().collect(Collectors.toMap(Book::getId, Function.identity()));
        return list.stream().map(f -> {
            Book b = bookMap.get(f.getBookId());
            return new FavoriteView(f.getId(), f.getBookId(),
                    b == null ? "?" : b.getTitle(), b == null ? "" : b.getAuthor(),
                    b == null ? null : b.getCoverUrl(), f.getCreatedAt());
        }).toList();
    }

    @PostMapping("/favorites/{bookId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public void addFavorite(@PathVariable Long bookId, @AuthenticationPrincipal SecurityUser principal) {
        books.findById(bookId).orElseThrow(() -> ApiException.notFound("error.catalog.bookNotFound"));
        if (!favorites.existsByReaderIdAndBookId(principal.getId(), bookId)) {
            favorites.save(new Favorite(principal.getId(), bookId));
        }
    }

    @DeleteMapping("/favorites/{bookId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void removeFavorite(@PathVariable Long bookId, @AuthenticationPrincipal SecurityUser principal) {
        favorites.findByReaderIdAndBookId(principal.getId(), bookId).ifPresent(favorites::delete);
    }

    // ---------- purchase suggestions ----------

    public record SuggestionRequest(@NotBlank @Size(max = 256) String title,
                                    @Size(max = 256) String author,
                                    @Size(max = 20) String isbn,
                                    @Size(max = 512) String reason) {}

    public record SuggestionView(Long id, String title, String author, String isbn, String reason,
                                 String status, String reply, Instant createdAt, Instant handledAt) {}

    @GetMapping("/suggestions")
    public List<SuggestionView> mySuggestions(@AuthenticationPrincipal SecurityUser principal) {
        return suggestions.findByReaderIdOrderByCreatedAtDesc(principal.getId()).stream()
                .map(MyLibraryController::toView).toList();
    }

    @PostMapping("/suggestions")
    @ResponseStatus(HttpStatus.CREATED)
    public SuggestionView createSuggestion(@Valid @RequestBody SuggestionRequest body,
                                           @AuthenticationPrincipal SecurityUser principal) {
        var saved = suggestions.save(new PurchaseSuggestion(principal.getId(), body.title(),
                body.author(), body.isbn(), body.reason()));
        return toView(saved);
    }

    private static SuggestionView toView(PurchaseSuggestion s) {
        return new SuggestionView(s.getId(), s.getTitle(), s.getAuthor(), s.getIsbn(), s.getReason(),
                s.getStatus().name(), s.getReply(), s.getCreatedAt(), s.getHandledAt());
    }
}
