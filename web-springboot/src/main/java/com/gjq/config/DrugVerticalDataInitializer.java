package com.gjq.config;

import com.gjq.entity.Category;
import com.gjq.service.CategoryService;
import com.gjq.utils.DrugVerticalDomainSupport;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class DrugVerticalDataInitializer {

    private final CategoryService categoryService;

    public DrugVerticalDataInitializer(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostConstruct
    public void initialize() {
        ensureCategories();
    }

    private Map<String, Category> ensureCategories() {
        Map<String, Category> existing = categoryService.getAllCategories().stream()
                .collect(Collectors.toMap(Category::getName, item -> item, (left, right) -> left, LinkedHashMap::new));

        boolean changed = false;
        for (DrugVerticalDomainSupport.SeedCategory seed : DrugVerticalDomainSupport.defaultCategories()) {
            Category category = existing.get(seed.getName());
            if (category == null) {
                Category created = new Category();
                created.setName(seed.getName());
                created.setDescription(seed.getDescription());
                created.setSort(seed.getSort());
                created.setIconUrl(seed.getIconUrl());
                created.setThemeColor(seed.getThemeColor());
                categoryService.save(created);
                changed = true;
                continue;
            }
            boolean dirty = false;
            if (!seed.getDescription().equals(category.getDescription())) {
                category.setDescription(seed.getDescription());
                dirty = true;
            }
            if (category.getSort() == null || category.getSort() != seed.getSort()) {
                category.setSort(seed.getSort());
                dirty = true;
            }
            if (!StringUtils.hasText(category.getIconUrl())) {
                category.setIconUrl(seed.getIconUrl());
                dirty = true;
            }
            if (!StringUtils.hasText(category.getThemeColor())) {
                category.setThemeColor(seed.getThemeColor());
                dirty = true;
            }
            if (dirty) {
                categoryService.updateById(category);
                changed = true;
            }
        }

        if (changed) {
            existing = categoryService.getAllCategories().stream()
                    .collect(Collectors.toMap(Category::getName, item -> item, (left, right) -> left, LinkedHashMap::new));
        }
        return existing;
    }
}
