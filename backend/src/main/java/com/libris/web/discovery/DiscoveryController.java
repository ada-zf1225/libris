package com.libris.web.discovery;

import com.libris.domain.catalog.Book;
import com.libris.domain.catalog.BookCopy;
import com.libris.domain.catalog.BookCopyRepository;
import com.libris.domain.catalog.CopyStatus;
import com.libris.domain.circulation.HoldRepository;
import com.libris.domain.circulation.HoldStatus;
import com.libris.domain.circulation.Loan;
import com.libris.domain.circulation.LoanRepository;
import com.libris.domain.patron.FavoriteRepository;
import com.libris.security.SecurityUser;
import com.libris.service.catalog.CatalogService;
import com.libris.service.circulation.HoldService;
import com.libris.service.discovery.DiscoveryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Validated
public class DiscoveryController {

    private final DiscoveryService discovery;
    private final CatalogService catalog;
    private final BookCopyRepository copies;
    private final LoanRepository loans;
    private final HoldRepository holds;
    private final FavoriteRepository favorites;
    private final HoldService holdService;

    // ---------- search ----------

    @GetMapping("/api/books")
    public DiscoveryService.SearchResult<DiscoveryService.BookHit> books(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) Integer yearFrom,
            @RequestParam(required = false) Integer yearTo,
            @RequestParam(defaultValue = "false") boolean availableOnly,
            @RequestParam(defaultValue = "relevance") String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return discovery.searchBooks(q, categoryId, language, yearFrom, yearTo, availableOnly, sort, page, size);
    }

    @GetMapping("/api/books/suggest")
    public List<DiscoveryService.Suggestion> suggest(@RequestParam String q) {
        return discovery.suggest(q);
    }

    @GetMapping("/api/papers")
    public DiscoveryService.SearchResult<DiscoveryService.PaperHit> papers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer yearFrom,
            @RequestParam(required = false) Integer yearTo,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return discovery.searchPapers(q, yearFrom, yearTo, page, size);
    }

    // ---------- detail ----------

    public record CopyAvailability(Long copyId, String barcode, String callNumber, String location,
                                   CopyStatus status, Instant dueAt) {}

    public record BookDetail(Long id, String title, String author, String publisher, String isbn,
                             String intro, String language, Integer priceCents, LocalDate pubDate,
                             Integer categoryId, String coverUrl,
                             List<CopyAvailability> copies, long availableCount, long holdQueueLength,
                             boolean holdable, boolean amIHolding, boolean amIFavorite,
                             List<DiscoveryService.BookHit> related) {}

    @GetMapping("/api/books/{id}")
    public BookDetail detail(@PathVariable Long id, @AuthenticationPrincipal SecurityUser principal) {
        Book book = catalog.get(id);
        List<BookCopy> copyList = copies.findByBookIdOrderById(id);
        List<CopyAvailability> availability = copyList.stream().map(c -> {
            Instant dueAt = c.getStatus() == CopyStatus.ON_LOAN
                    ? loans.findByCopyIdAndReturnedAtIsNull(c.getId()).map(Loan::getDueAt).orElse(null)
                    : null;
            return new CopyAvailability(c.getId(), c.getBarcode(), c.getCallNumber(), c.getLocation(),
                    c.getStatus(), dueAt);
        }).toList();

        long available = copyList.stream().filter(c -> c.getStatus() == CopyStatus.IN_LIBRARY).count();
        long queueLength = holds.countByBookIdAndStatus(id, HoldStatus.QUEUED);
        boolean amIHolding = principal != null && holds.existsByBookIdAndReaderIdAndStatusIn(
                id, principal.getId(), List.of(HoldStatus.QUEUED, HoldStatus.READY));
        boolean amIFavorite = principal != null
                && favorites.existsByReaderIdAndBookId(principal.getId(), id);
        boolean holdable = available == 0 && !copyList.isEmpty()
                && copyList.stream().anyMatch(c -> c.getStatus() == CopyStatus.ON_LOAN
                        || c.getStatus() == CopyStatus.ON_HOLD_SHELF);

        return new BookDetail(book.getId(), book.getTitle(), book.getAuthor(), book.getPublisher(),
                book.getIsbn(), book.getIntro(), book.getLanguage(), book.getPriceCents(), book.getPubDate(),
                book.getCategoryId(), book.getCoverUrl(),
                availability, available, queueLength, holdable, amIHolding, amIFavorite,
                discovery.related(id, 6));
    }

    // ---------- holds ----------

    @PostMapping("/api/books/{id}/holds")
    @ResponseStatus(HttpStatus.CREATED)
    public HoldService.PlacedHold placeHold(@PathVariable Long id,
                                            @AuthenticationPrincipal SecurityUser principal) {
        return holdService.place(id, principal.getId());
    }
}
