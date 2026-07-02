package com.libris.domain.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {

    @Id
    private Integer id;

    @Column(nullable = false, length = 4)
    private String code;

    @Column(name = "name_zh", nullable = false, length = 64)
    private String nameZh;

    @Column(name = "name_en", nullable = false, length = 128)
    private String nameEn;
}
