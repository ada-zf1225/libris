package com.libris.web;

import com.libris.domain.catalog.Category;
import com.libris.domain.catalog.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryRepository categories;

    public record CategoryView(Integer id, String code, String nameZh, String nameEn) {
        static CategoryView of(Category c) {
            return new CategoryView(c.getId(), c.getCode(), c.getNameZh(), c.getNameEn());
        }
    }

    @GetMapping
    public List<CategoryView> list() {
        return categories.findAll().stream().map(CategoryView::of).toList();
    }
}
