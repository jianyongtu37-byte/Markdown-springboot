package com.nineone.markdown.controller;

import com.nineone.common.result.Result;
import com.nineone.markdown.entity.Category;
import com.nineone.markdown.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 分类控制器
 */
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Validated
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * 创建分类
     */
    @PostMapping
    public Result<Long> createCategory(@Valid @RequestBody Category category) {
        Long categoryId = categoryService.createCategory(category);
        return Result.success("分类创建成功", categoryId);
    }

    /**
     * 获取分类详情
     */
    @GetMapping("/{id}")
    public Result<Category> getCategory(@PathVariable("id") Long id) {
        Category category = categoryService.getCategory(id);
        if (category == null) {
            return Result.<Category>notFound("分类不存在");
        }
        return Result.success(category);
    }

    /**
     * 更新分类
     */
    @PutMapping("/{id}")
    public Result<Void> updateCategory(@PathVariable("id") Long id, @Valid @RequestBody Category category) {
        category.setId(id);
        boolean success = categoryService.updateCategory(category);
        if (!success) {
            return Result.notFound("分类不存在，更新失败");
        }
        return Result.success("分类更新成功", null);
    }

    /**
     * 删除分类
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteCategory(@PathVariable("id") Long id) {
        boolean success = categoryService.deleteCategory(id);
        if (!success) {
            return Result.notFound("分类不存在，删除失败");
        }
        return Result.success("分类删除成功", null);
    }

    /**
     * 获取所有分类列表（按排序顺序）
     */
    @GetMapping
    public Result<List<Category>> getAllCategories() {
        List<Category> categories = categoryService.getAllCategories();
        return Result.success(categories);
    }

    /**
     * 调整分类排序
     */
    @PostMapping("/{id}/sort")
    public Result<Void> updateSortOrder(@PathVariable("id") Long id, @RequestParam(value = "sortOrder") Integer sortOrder) {
        boolean success = categoryService.updateSortOrder(id, sortOrder);
        if (!success) {
            return Result.notFound("分类不存在，排序更新失败");
        }
        return Result.success("分类排序更新成功", null);
    }
}