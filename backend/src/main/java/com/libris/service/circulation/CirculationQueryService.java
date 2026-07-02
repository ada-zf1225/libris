package com.libris.service.circulation;

import com.libris.domain.catalog.Book;
import com.libris.domain.catalog.BookCopy;
import com.libris.domain.catalog.BookRepository;
import com.libris.domain.catalog.BookCopyRepository;
import com.libris.domain.circulation.*;
import com.libris.domain.user.ReaderProfile;
import com.libris.domain.user.ReaderProfileRepository;
import com.libris.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Read-side assembly for desk and patron views (batched lookups, no N+1). */
@Service
@RequiredArgsConstructor
public class CirculationQueryService {

    private final LoanRepository loans;
    private final FineRepository fines;
    private final HoldRepository holds;
    private final BookRepository books;
    private final BookCopyRepository copies;
    private final ReaderProfileRepository profiles;
    private final LoanPolicyRepository policies;

    public record LoanView(Long id, Long copyId, String barcode, Long bookId, String bookTitle,
                           Instant loanedAt, Instant dueAt, Instant returnedAt,
                           int renewCount, boolean overdue) {}

    public record FineView(Long id, Long loanId, String bookTitle, int amountCents,
                           String reason, String status, Instant createdAt, Instant paidAt) {}

    public record ReaderSummary(Long id, String username, String displayName, String status,
                                String readerType, String email, String phone,
                                int activeLoanCount, int maxLoans, long overdueCount,
                                long unpaidFineCents, List<LoanView> activeLoans) {}

    @Transactional(readOnly = true)
    public ReaderSummary readerSummary(User reader) {
        List<Loan> active = loans.findByReaderIdAndReturnedAtIsNullOrderByDueAtAsc(reader.getId());
        List<LoanView> views = toLoanViews(active);
        ReaderProfile profile = profiles.findById(reader.getId()).orElse(null);
        int maxLoans = profile == null ? 0 : policies.findByReaderType(profile.getReaderType())
                .map(LoanPolicy::getMaxLoans).orElse(0);
        Instant now = Instant.now();
        return new ReaderSummary(
                reader.getId(), reader.getUsername(), reader.getDisplayName(), reader.getStatus().name(),
                profile == null ? null : profile.getReaderType().name(), reader.getEmail(), reader.getPhone(),
                active.size(), maxLoans, loans.countOverdue(reader.getId(), now),
                fines.unpaidTotal(reader.getId()), views);
    }

    @Transactional(readOnly = true)
    public List<LoanView> activeLoansOf(Long readerId) {
        return toLoanViews(loans.findByReaderIdAndReturnedAtIsNullOrderByDueAtAsc(readerId));
    }

    @Transactional(readOnly = true)
    public List<FineView> finesOf(Long readerId) {
        List<Fine> list = fines.findByReaderIdOrderByCreatedAtDesc(readerId);
        Map<Long, Loan> loanMap = loans.findAllById(list.stream().map(Fine::getLoanId).toList())
                .stream().collect(Collectors.toMap(Loan::getId, Function.identity()));
        Map<Long, String> titles = titlesForCopies(loanMap.values().stream().map(Loan::getCopyId).toList());
        return list.stream().map(f -> {
            Loan loan = loanMap.get(f.getLoanId());
            String title = loan == null ? "" : titles.getOrDefault(loan.getCopyId(), "");
            return new FineView(f.getId(), f.getLoanId(), title, f.getAmountCents(),
                    f.getReason().name(), f.getStatus().name(), f.getCreatedAt(), f.getPaidAt());
        }).toList();
    }

    public List<LoanView> toLoanViews(List<Loan> list) {
        Map<Long, BookCopy> copyMap = copies.findAllById(list.stream().map(Loan::getCopyId).toList())
                .stream().collect(Collectors.toMap(BookCopy::getId, Function.identity()));
        Map<Long, Book> bookMap = books.findAllById(copyMap.values().stream().map(BookCopy::getBookId).toList())
                .stream().collect(Collectors.toMap(Book::getId, Function.identity()));
        Instant now = Instant.now();
        return list.stream().map(l -> {
            BookCopy copy = copyMap.get(l.getCopyId());
            Book book = copy == null ? null : bookMap.get(copy.getBookId());
            return new LoanView(l.getId(), l.getCopyId(),
                    copy == null ? "?" : copy.getBarcode(),
                    book == null ? null : book.getId(),
                    book == null ? "?" : book.getTitle(),
                    l.getLoanedAt(), l.getDueAt(), l.getReturnedAt(), l.getRenewCount(),
                    l.isOverdue(now));
        }).toList();
    }

    private Map<Long, String> titlesForCopies(List<Long> copyIds) {
        Map<Long, BookCopy> copyMap = copies.findAllById(copyIds).stream()
                .collect(Collectors.toMap(BookCopy::getId, Function.identity()));
        Map<Long, Book> bookMap = books.findAllById(copyMap.values().stream().map(BookCopy::getBookId).toList())
                .stream().collect(Collectors.toMap(Book::getId, Function.identity()));
        return copyMap.values().stream().collect(Collectors.toMap(BookCopy::getId,
                c -> bookMap.containsKey(c.getBookId()) ? bookMap.get(c.getBookId()).getTitle() : ""));
    }
}
