package com.libris.domain.catalog;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "books")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 256)
    private String title;

    @Column(nullable = false, length = 256)
    private String author;

    @Column(nullable = false, length = 128)
    private String publisher;

    @Column(nullable = false, length = 20)
    private String isbn;

    @Column(columnDefinition = "text")
    private String intro;

    @Column(nullable = false, length = 16)
    private String language;

    @Column(name = "price_cents")
    private Integer priceCents;

    @Column(name = "pub_date")
    private LocalDate pubDate;

    @Column(name = "category_id", nullable = false)
    private Integer categoryId;

    @Column(name = "cover_url", length = 512)
    private String coverUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Book(String title, String author, String publisher, String isbn, String intro,
                String language, Integer priceCents, LocalDate pubDate, Integer categoryId, String coverUrl) {
        this.title = title;
        this.author = author;
        this.publisher = publisher;
        this.isbn = isbn;
        this.intro = intro;
        this.language = language;
        this.priceCents = priceCents;
        this.pubDate = pubDate;
        this.categoryId = categoryId;
        this.coverUrl = coverUrl;
    }
}
