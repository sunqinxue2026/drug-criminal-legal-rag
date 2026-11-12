package com.gjq.controller;

import com.gjq.common.Result;
import com.gjq.entity.Category;
import com.gjq.service.CategoryService;
import com.gjq.service.support.RequestAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/category")
public class CategoryController {

    private final CategoryService categoryService;
    private final RequestAuthorizationService requestAuthorizationService;

    public CategoryController(CategoryService categoryService,
                              RequestAuthorizationService requestAuthorizationService) {
        this.categoryService = categoryService;
        this.requestAuthorizationService = requestAuthorizationService;
    }

    @GetMapping("/list")
    public Result<List<Category>> getAllCategories() {
        List<Category> categories = categoryService.getAllCategories();
        return Result.success(categories);
    }

    @GetMapping("/{id}")
    public Result<Category> getCategoryById(@PathVariable Long id) {
        Category category = categoryService.getById(id);
        return Result.success(category);
    }

    @PostMapping
    public Result<String> createCategory(@RequestBody Category category, HttpServletRequest request) {
        requestAuthorizationService.requireAdmin(request);
        categoryService.save(category);
        return Result.success("创建成功");
    }

    @PutMapping
    public Result<String> updateCategory(@RequestBody Category category, HttpServletRequest request) {
        requestAuthorizationService.requireAdmin(request);
        categoryService.updateById(category);
        return Result.success("更新成功");
    }

    @DeleteMapping("/{id}")
    public Result<String> deleteCategory(@PathVariable Long id, HttpServletRequest request) {
        requestAuthorizationService.requireAdmin(request);
        categoryService.removeById(id);
        return Result.success("删除成功");
    }
}
