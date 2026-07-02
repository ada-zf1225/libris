package com.libris.domain.user;

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
@Table(name = "reader_profiles")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReaderProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reader_type", nullable = false, length = 16)
    private ReaderType readerType;

    @Column(length = 8)
    private String sex;

    private LocalDate birth;

    @Column(length = 128)
    private String address;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ReaderProfile(Long userId, ReaderType readerType, String sex, LocalDate birth, String address) {
        this.userId = userId;
        this.readerType = readerType;
        this.sex = sex;
        this.birth = birth;
        this.address = address;
    }
}
