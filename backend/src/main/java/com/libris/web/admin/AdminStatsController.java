package com.libris.web.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {

    private final JdbcClient jdbc;

    public record TrendPoint(String day, long loans, long returns) {}

    public record TopBook(Long bookId, String title, String author, long loanCount) {}

    public record CategoryShare(String code, String nameZh, String nameEn, long loanCount) {}

    public record Totals(long books, long copies, long readers, long activeLoans,
                         long overdueLoans, long readyHolds, long unpaidFineCents) {}

    public record Dashboard(Totals totals, List<TrendPoint> trend, List<TopBook> topBooks,
                            List<CategoryShare> categoryShare, double overdueRate) {}

    @GetMapping
    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        Totals totals = new Totals(
                count("select count(*) from books"),
                count("select count(*) from book_copies"),
                count("select count(*) from users where role = 'READER'"),
                count("select count(*) from loans where returned_at is null"),
                count("select count(*) from loans where returned_at is null and due_at < now()"),
                count("select count(*) from holds where status = 'READY'"),
                count("select coalesce(sum(amount_cents), 0) from fines where status = 'UNPAID'"));

        List<TrendPoint> trend = jdbc.sql("""
                        with days as (select generate_series(current_date - 29, current_date, '1 day')::date as d)
                        select to_char(days.d, 'MM-DD') as day,
                               (select count(*) from loans l where l.loaned_at::date = days.d)   as loans,
                               (select count(*) from loans l where l.returned_at::date = days.d) as returns
                          from days order by days.d
                        """)
                .query((rs, i) -> new TrendPoint(rs.getString("day"), rs.getLong("loans"), rs.getLong("returns")))
                .list();

        List<TopBook> topBooks = jdbc.sql("""
                        select b.id, b.title, b.author, count(*) as n
                          from loans l
                          join book_copies c on c.id = l.copy_id
                          join books b on b.id = c.book_id
                         group by b.id, b.title, b.author
                         order by n desc, b.id
                         limit 10
                        """)
                .query((rs, i) -> new TopBook(rs.getLong("id"), rs.getString("title"),
                        rs.getString("author"), rs.getLong("n")))
                .list();

        List<CategoryShare> share = jdbc.sql("""
                        select cat.code, cat.name_zh, cat.name_en, count(*) as n
                          from loans l
                          join book_copies c on c.id = l.copy_id
                          join books b on b.id = c.book_id
                          join categories cat on cat.id = b.category_id
                         group by cat.code, cat.name_zh, cat.name_en
                         order by n desc
                        """)
                .query((rs, i) -> new CategoryShare(rs.getString("code"), rs.getString("name_zh"),
                        rs.getString("name_en"), rs.getLong("n")))
                .list();

        long allLoans = count("select count(*) from loans");
        long overdueEver = count("""
                select count(*) from loans
                where (returned_at is not null and returned_at > due_at)
                   or (returned_at is null and due_at < now())
                """);
        double overdueRate = allLoans == 0 ? 0 : Math.round(overdueEver * 1000.0 / allLoans) / 10.0;

        return new Dashboard(totals, trend, topBooks, share, overdueRate);
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }
}
