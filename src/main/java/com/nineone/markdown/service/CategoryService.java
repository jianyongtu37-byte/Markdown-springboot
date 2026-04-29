package com.nineone.markdown.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nineone.markdown.entity.Category;

import java.util.List;

/**
 * 分类服务接口
 */
public interface CategoryService extends IService<Category> {

    /**
     * 创建分类
     * @param category 分类实体
     * @return 创建的分类ID
     */
    Long createCategory(Category category);

    /**
     * 根据ID获取分类详情
     * @param id 分类ID
     * @return 分类实体
     */
    Category getCategory(Long id);

    /**
     * 更新分类
     * @param category 分类实体
     * @return 是否更新成功
     */
    boolean updateCategory(Category category);

    /**
     * 删除分类
     * @param id 分类ID
     * @return 是否删除成功
     */
    boolean deleteCategory(Long id);

    /**
     * 获取所有分类列表（按排序顺序）
     * @return 分类列表
     */
    List<Category> getAllCategories();

    /**
     * 更新分类排序
     * @param id 分类ID
     * @param sortOrder 排序值
     * @return 是否更新成功
     */
    boolean updateSortOrder(Long id, Integer sortOrder);
}