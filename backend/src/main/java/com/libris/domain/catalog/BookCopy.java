package com.libris.domain.catalog;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "book_copies")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookCopy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(nullable = false, unique = true, length = 32)
    private String barcode;

    @Column(name = "call_number", nullable = false, length = 64)
    private String callNumber;

    @Column(nullable = false, length = 64)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CopyStatus status = CopyStatus.IN_LIBRARY;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public BookCopy(Long bookId, String barcode, String callNumber, String location) {
        this.bookId = bookId;
        this.barcode = barcode;
        this.callNumber = callNumber;
        this.location = location;
    }
}
