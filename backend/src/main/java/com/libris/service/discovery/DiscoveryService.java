package com.libris.service.discovery;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Primo-style discovery over PostgreSQL: 'simple' tsvector matching for
 * token queries combined with pg_trgm similarity (which carries Chinese
 * substring matching and typo tolerance), plus facet aggregation.
 */
@Service
@RequiredArgsConstructor
public class DiscoveryService {

    private final JdbcClient jdbc;

    // ---------- result records ----------

    public record BookHit(Long id, String title, String author, String publisher, String isbn,
                          String language, LocalDate pubDate, Integer categoryId, String coverUrl,
                          String intro, long copies, long available) {}

    public record PaperHit(Long id, String title, String authors, String venue, int year,
                           String pages, String abstractText, String doi, String url) {}

    public record Facets(Map<Integer, Long> byCategory, Map<String, Long> byLanguage,
                         Map<String, Long> byDecade, long availableCount, long total) {}

    public record SearchResult<T>(List<T> content, long total, int page, int size, Facets facets) {}

    public record Suggestion(String text, String type) {}

    // ---------- books ----------

    @Transactional(readOnly = true)
    public SearchResult<BookHit> searchBooks(String q, Integer categoryId, String language,
                                             Integer yearFrom, Integer yearTo, boolean availableOnly,
                                             String sort, int page, int size) {
        StringBuilder where = new StringBuilder(" where 1=1 ");
        Map<String, Object> params = new HashMap<>();

        boolean hasQuery = q != null && !q.isBlank();
        if (hasQuery) {
            where.append("""
                    and (b.search_vec @@ plainto_tsquery('simple', :q)
                         or b.title ilike '%' || :q || '%'
                         or b.author ilike '%' || :q || '%'
                         or similarity(b.title, :q) > 0.15
                         or similarity(b.author, :q) > 0.2)
                    """);
            params.put("q", q.trim());
        }
        if (categoryId != null) {
            where.append(" and b.category_id = :categoryId ");
            params.put("categoryId", categoryId);
        }
        if (language != null && !language.isBlank()) {
            where.append(" and b.language = :language ");
            params.put("language", language);
        }
        if (yearFrom != null) {
            where.append(" and b.pub_date >= make_date(:yearFrom, 1, 1) ");
            params.put("yearFrom", yearFrom);
        }
        if (yearTo != null) {
            where.append(" and b.pub_date <= make_date(:yearTo, 12, 31) ");
            params.put("yearTo", yearTo);
        }
        if (availableOnly) {
            where.append(" and exists (select 1 from book_copies c where c.book_id = b.id and c.status = 'IN_LIBRARY') ");
        }

        String orderBy = switch (sort == null ? "relevance" : sort) {
            case "newest" -> " order by b.pub_date desc nulls last, b.id desc ";
            case "title" -> " order by b.title asc ";
            default -> hasQuery
                    ? """
                       order by (ts_rank(b.search_vec, plainto_tsquery('simple', :q)) * 2
                                 + similarity(b.title, :q)
                                 + similarity(b.author, :q) * 0.5) desc, b.id desc
                      """
                    : " order by b.id desc ";
        };

        String base = "from books b" + where;
        long total = jdbc.sql("select count(*) " + base).params(params).query(Long.class).single();

        List<BookHit> hits = jdbc.sql("""
                        select b.*,
                               (select count(*) from book_copies c where c.book_id = b.id) as copies,
                               (select count(*) from book_copies c where c.book_id = b.id and c.status = 'IN_LIBRARY') as available
                        """ + base + orderBy + " limit :limit offset :offset")
                .params(params)
                .param("limit", size)
                .param("offset", (long) page * size)
                .query(DiscoveryService::mapBookHit)
                .list();

        Facets facets = bookFacets(base, params);
        return new SearchResult<>(hits, total, page, size, facets);
    }

    private Facets bookFacets(String base, Map<String, Object> params) {
        Map<Integer, Long> byCategory = new HashMap<>();
        jdbc.sql("select b.category_id as k, count(*) as n " + base + " group by b.category_id")
                .params(params)
                .query(rs -> { byCategory.put(rs.getInt("k"), rs.getLong("n")); });
        Map<String, Long> byLanguage = new HashMap<>();
        jdbc.sql("select b.language as k, count(*) as n " + base + " group by b.language")
                .params(params)
                .query(rs -> { byLanguage.put(rs.getString("k"), rs.getLong("n")); });
        Map<String, Long> byDecade = new HashMap<>();
        jdbc.sql("""
                        select (extract(year from b.pub_date)::int / 10 * 10)::text as k, count(*) as n
                        """ + base + " and b.pub_date is not null group by k")
                .params(params)
                .query(rs -> { byDecade.put(rs.getString("k"), rs.getLong("n")); });
        long availableCount = jdbc.sql("select count(*) " + base
                        + " and exists (select 1 from book_copies c where c.book_id = b.id and c.status = 'IN_LIBRARY')")
                .params(params).query(Long.class).single();
        long total = jdbc.sql("select count(*) " + base).params(params).query(Long.class).single();
        return new Facets(byCategory, byLanguage, byDecade, availableCount, total);
    }

    private static BookHit mapBookHit(ResultSet rs, int rowNum) throws SQLException {
        var pub = rs.getDate("pub_date");
        String intro = rs.getString("intro");
        return new BookHit(rs.getLong("id"), rs.getString("title"), rs.getString("author"),
                rs.getString("publisher"), rs.getString("isbn"), rs.getString("language"),
                pub == null ? null : pub.toLocalDate(), rs.getInt("category_id"), rs.getString("cover_url"),
                intro != null && intro.length() > 160 ? intro.substring(0, 160) + "…" : intro,
                rs.getLong("copies"), rs.getLong("available"));
    }

    // ---------- suggestions ----------

    @Transactional(readOnly = true)
    public List<Suggestion> suggest(String q) {
        if (q == null || q.isBlank() || q.trim().length() < 2) {
            return List.of();
        }
        String query = q.trim();
        return jdbc.sql("""
                        (select title as text, 'title' as type, similarity(title, :q) as score
                           from books
                          where title ilike '%' || :q || '%' or similarity(title, :q) > 0.15
                          order by score desc limit 5)
                        union all
                        (select distinct author as text, 'author' as type, similarity(author, :q) as score
                           from books
                          where author ilike '%' || :q || '%' or similarity(author, :q) > 0.2
                          order by score desc limit 3)
                        """)
                .param("q", query)
                .query((rs, i) -> new Suggestion(rs.getString("text"), rs.getString("type")))
                .list();
    }

    // ---------- related ----------

    @Transactional(readOnly = true)
    public List<BookHit> related(Long bookId, int limit) {
        return jdbc.sql("""
                        select b.*,
                               (select count(*) from book_copies c where c.book_id = b.id) as copies,
                               (select count(*) from book_copies c where c.book_id = b.id and c.status = 'IN_LIBRARY') as available
                          from books b, books src
                         where src.id = :id and b.id <> :id
                           and (b.category_id = src.category_id or b.author = src.author)
                         order by (b.author = src.author)::int desc, b.id desc
                         limit :limit
                        """)
                .param("id", bookId)
                .param("limit", limit)
                .query(DiscoveryService::mapBookHit)
                .list();
    }

    // ---------- papers ----------

    @Transactional(readOnly = true)
    public SearchResult<PaperHit> searchPapers(String q, Integer yearFrom, Integer yearTo,
                                               int page, int size) {
        StringBuilder where = new StringBuilder(" where 1=1 ");
        Map<String, Object> params = new HashMap<>();
        boolean hasQuery = q != null && !q.isBlank();
        if (hasQuery) {
            where.append("""
                    and (p.search_vec @@ plainto_tsquery('simple', :q)
                         or p.title ilike '%' || :q || '%'
                         or p.authors ilike '%' || :q || '%'
                         or similarity(p.title, :q) > 0.12)
                    """);
            params.put("q", q.trim());
        }
        if (yearFrom != null) {
            where.append(" and p.year >= :yearFrom ");
            params.put("yearFrom", yearFrom);
        }
        if (yearTo != null) {
            where.append(" and p.year <= :yearTo ");
            params.put("yearTo", yearTo);
        }
        String base = "from papers p" + where;
        long total = jdbc.sql("select count(*) " + base).params(params).query(Long.class).single();
        String orderBy = hasQuery
                ? " order by (ts_rank(p.search_vec, plainto_tsquery('simple', :q)) * 2 + similarity(p.title, :q)) desc "
                : " order by p.year desc, p.id desc ";
        List<PaperHit> hits = jdbc.sql("select p.* " + base + orderBy + " limit :limit offset :offset")
                .params(params)
                .param("limit", size)
                .param("offset", (long) page * size)
                .query((rs, i) -> new PaperHit(rs.getLong("id"), rs.getString("title"), rs.getString("authors"),
                        rs.getString("venue"), rs.getInt("year"), rs.getString("pages"),
                        truncate(rs.getString("abstract")), rs.getString("doi"), rs.getString("url")))
                .list();
        return new SearchResult<>(hits, total, page, size, null);
    }

    private static String truncate(String s) {
        return s != null && s.length() > 220 ? s.substring(0, 220) + "…" : s;
    }
}
