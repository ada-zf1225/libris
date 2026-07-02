package com.libris.web.admin;

import com.libris.domain.catalog.Book;
import com.libris.domain.catalog.BookCopy;
import com.libris.domain.catalog.CopyStatus;
import com.libris.service.catalog.CatalogService;
import com.libris.web.dto.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@org.springframework.security.access.prepost.PreAuthorize("hasAuthority('PERM_MANAGE_CATALOG')")
@RestController
@RequestMapping("/api/admin/books")
@RequiredArgsConstructor
public class AdminBookController {

    private final CatalogService catalog;

    public record BookRequest(
            @NotBlank @Size(max = 256) String title,
            @NotBlank @Size(max = 256) String author,
            @NotBlank @Size(max = 128) String publisher,
            @NotBlank @Size(max = 20) String isbn,
            @Size(max = 4000) String intro,
            @NotBlank @Size(max = 16) String language,
            @Min(0) Integer priceCents,
            LocalDate pubDate,
            @NotNull Integer categoryId,
            @Size(max = 512) String coverUrl) {

        CatalogService.BookInput toInput() {
            return new CatalogService.BookInput(title, author, publisher, isbn, intro, language,
                    priceCents, pubDate, categoryId, coverUrl);
        }
    }

    public record AddCopiesRequest(@Min(1) @Max(50) int count, @NotBlank @Size(max = 64) String location) {}

    public record BookView(Long id, String title, String author, String publisher, String isbn,
                           String intro, String language, Integer priceCents, LocalDate pubDate,
                           Integer categoryId, String coverUrl, Instant createdAt) {
        static BookView of(Book b) {
            return new BookView(b.getId(), b.getTitle(), b.getAuthor(), b.getPublisher(), b.getIsbn(),
                    b.getIntro(), b.getLanguage(), b.getPriceCents(), b.getPubDate(), b.getCategoryId(),
                    b.getCoverUrl(), b.getCreatedAt());
        }
    }

    public record CopyView(Long id, Long bookId, String barcode, String callNumber, String location,
                           CopyStatus status) {
        static CopyView of(BookCopy c) {
            return new CopyView(c.getId(), c.getBookId(), c.getBarcode(), c.getCallNumber(),
                    c.getLocation(), c.getStatus());
        }
    }

    @GetMapping
    public PageResponse<BookView> list(@RequestParam(required = false) String q,
                                       @RequestParam(required = false) Integer categoryId,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)),
                Sort.by(Sort.Direction.DESC, "id"));
        return PageResponse.of(catalog.search(q, categoryId, pageable), BookView::of);
    }

    @GetMapping("/{id}")
    public BookView get(@PathVariable Long id) {
        return BookView.of(catalog.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookView create(@Valid @RequestBody BookRequest body) {
        return BookView.of(catalog.create(body.toInput()));
    }

    @PutMapping("/{id}")
    public BookView update(@PathVariable Long id, @Valid @RequestBody BookRequest body) {
        return BookView.of(catalog.update(id, body.toInput()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        catalog.delete(id);
    }

    @GetMapping("/{id}/copies")
    public List<CopyView> copies(@PathVariable Long id) {
        return catalog.copiesOf(id).stream().map(CopyView::of).toList();
    }

    @PostMapping("/{id}/copies")
    @ResponseStatus(HttpStatus.CREATED)
    public List<CopyView> addCopies(@PathVariable Long id, @Valid @RequestBody AddCopiesRequest body) {
        return catalog.addCopies(id, body.count(), body.location()).stream().map(CopyView::of).toList();
    }

    @DeleteMapping("/copies/{copyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeCopy(@PathVariable Long copyId) {
        catalog.removeCopy(copyId);
    }
}
