package com.libris.domain.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookRepository extends JpaRepository<Book, Long> {

    @Query("""
            select b from Book b
            where (:q is null
                   or lower(b.title) like lower(concat('%', :q, '%'))
                   or lower(b.author) like lower(concat('%', :q, '%'))
                   or b.isbn like concat('%', :q, '%'))
              and (:categoryId is null or b.categoryId = :categoryId)
            """)
    Page<Book> searchForAdmin(@Param("q") String q, @Param("categoryId") Integer categoryId, Pageable pageable);
}
