package com.libris.service.catalog;

import com.libris.domain.catalog.*;
import com.libris.domain.circulation.LoanRepository;
import com.libris.web.error.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final BookRepository books;
    private final BookCopyRepository copies;
    private final CategoryRepository categories;
    private final LoanRepository loans;

    public record BookInput(String title, String author, String publisher, String isbn, String intro,
                            String language, Integer priceCents, LocalDate pubDate, Integer categoryId,
                            String coverUrl) {}

    @Transactional(readOnly = true)
    public Page<Book> search(String q, Integer categoryId, Pageable pageable) {
        String query = (q == null || q.isBlank()) ? null : q.trim();
        return books.searchForAdmin(query, categoryId, pageable);
    }

    @Transactional(readOnly = true)
    public Book get(Long id) {
        return books.findById(id).orElseThrow(() -> ApiException.notFound("error.catalog.bookNotFound"));
    }

    @Transactional
    public Book create(BookInput input) {
        requireCategory(input.categoryId());
        Book book = new Book(input.title(), input.author(), input.publisher(), input.isbn(), input.intro(),
                input.language(), input.priceCents(), input.pubDate(), input.categoryId(),
                coverOrDefault(input.coverUrl(), input.isbn()));
        return books.save(book);
    }

    @Transactional
    public Book update(Long id, BookInput input) {
        requireCategory(input.categoryId());
        Book book = get(id);
        book.setTitle(input.title());
        book.setAuthor(input.author());
        book.setPublisher(input.publisher());
        book.setIsbn(input.isbn());
        book.setIntro(input.intro());
        book.setLanguage(input.language());
        book.setPriceCents(input.priceCents());
        book.setPubDate(input.pubDate());
        book.setCategoryId(input.categoryId());
        book.setCoverUrl(coverOrDefault(input.coverUrl(), input.isbn()));
        return book;
    }

    @Transactional
    public void delete(Long id) {
        Book book = get(id);
        if (!copies.findByBookIdOrderById(id).isEmpty()) {
            throw ApiException.conflict("error.catalog.hasCopies");
        }
        books.delete(book);
    }

    @Transactional(readOnly = true)
    public List<BookCopy> copiesOf(Long bookId) {
        get(bookId);
        return copies.findByBookIdOrderById(bookId);
    }

    @Transactional
    public List<BookCopy> addCopies(Long bookId, int count, String location) {
        Book book = get(bookId);
        String code = categories.findById(book.getCategoryId()).map(Category::getCode).orElse("Z");
        List<BookCopy> created = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long n = copies.nextBarcodeNumber();
            String barcode = "LB" + String.format("%06d", n);
            String callNumber = code + "-" + String.format("%04d", bookId) + "-" + n;
            created.add(copies.save(new BookCopy(bookId, barcode, callNumber, location)));
        }
        return created;
    }

    @Transactional
    public void removeCopy(Long copyId) {
        BookCopy copy = copies.findById(copyId)
                .orElseThrow(() -> ApiException.notFound("error.catalog.copyNotFound"));
        if (copy.getStatus() == CopyStatus.ON_LOAN || copy.getStatus() == CopyStatus.ON_HOLD_SHELF) {
            throw ApiException.conflict("error.catalog.copyInUse");
        }
        try {
            copies.delete(copy);
            copies.flush();
        } catch (DataIntegrityViolationException e) {
            // circulation history references this copy — physical deletion is not allowed
            throw ApiException.conflict("error.catalog.copyHasHistory");
        }
    }

    private void requireCategory(Integer categoryId) {
        if (categoryId == null || !categories.existsById(categoryId)) {
            throw ApiException.badRequest("error.catalog.categoryInvalid");
        }
    }

    private String coverOrDefault(String coverUrl, String isbn) {
        if (coverUrl != null && !coverUrl.isBlank()) {
            return coverUrl;
        }
        return "https://covers.openlibrary.org/b/isbn/" + isbn + "-M.jpg";
    }
}
