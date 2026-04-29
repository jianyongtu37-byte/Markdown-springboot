package com.nineone.markdown.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nineone.markdown.entity.Article;
import com.nineone.markdown.entity.Category;
import com.nineone.markdown.exception.PermissionDeniedException;
import com.nineone.markdown.mapper.ArticleMapper;
import com.nineone.markdown.mapper.CategoryMapper;
import com.nineone.markdown.service.CategoryService;
import com.nineone.markdown.util.UserContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.List;

/**
 * 分类服务实现类
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    private final CategoryMapper categoryMapper;
    private final ArticleMapper articleMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createCategory(Category category) {
        // 参数验证
        Assert.notNull(category, "分类不能为空");
        Assert.hasText(category.getName(), "分类名称不能为空");
        
        // 检查分类名称是否已存在（忽略数据权限，需要查看所有用户的分类）
        Category existingCategory = categoryMapper.selectByNameIgnorePermission(category.getName());
        if (existingCategory != null) {
            throw new IllegalArgumentException("分类名称已存在");
        }
        
        // 设置用户ID - 确保用户创建的分类属于当前用户
        Long currentUserId = UserContextHolder.getUserId();
        if (currentUserId == null) {
            throw new IllegalStateException("用户未登录，无法创建分类");
        }
        category.setUserId(currentUserId);
        
        // 设置是否为默认分类（用户创建的分类不是系统默认分类）
        if (category.getIsDefault() == null) {
            category.setIsDefault(false);
        }
        
        // 设置默认排序值
        if (category.getSortOrder() == null) {
            // 获取最大排序值并加1
            Integer maxSortOrder = getMaxSortOrder();
            category.setSortOrder(maxSortOrder != null ? maxSortOrder + 1 : 0);
        }
        
        // 保存分类
        categoryMapper.insert(category);
        return category.getId();
    }

    @Override
    public Category getCategory(Long id) {
        Assert.notNull(id, "分类ID不能为空");
        
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            return null;
        }
        
        // 检查当前用户是否有权限查看这个分类
        if (!hasCategoryViewPermission(category)) {
            throw new PermissionDeniedException("没有权限查看该分类");
        }
        
        return category;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateCategory(Category category) {
        Assert.notNull(category, "分类不能为空");
        Assert.notNull(category.getId(), "分类ID不能为空");
        
        // 检查分类是否存在
        Category existingCategory = categoryMapper.selectById(category.getId());
        if (existingCategory == null) {
            return false;
        }
        
        // 检查当前用户是否有权限修改这个分类
        checkCategoryPermission(existingCategory);
        
        // 如果名称有变化，检查新名称是否已存在（忽略数据权限，需要查看所有用户的分类）
        if (!existingCategory.getName().equals(category.getName())) {
            Category duplicateCategory = categoryMapper.selectByNameIgnorePermission(category.getName());
            if (duplicateCategory != null) {
                throw new IllegalArgumentException("分类名称已存在");
            }
        }
        
        // 保护重要字段，防止前端恶意修改
        // 保持原有的用户ID和默认分类状态
        category.setUserId(existingCategory.getUserId());
        category.setIsDefault(existingCategory.getIsDefault());
        
        // 更新分类
        int updateCount = categoryMapper.updateById(category);
        return updateCount > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteCategory(Long id) {
        Assert.notNull(id, "分类ID不能为空");
        
        // 检查分类是否存在
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            return false;
        }
        
        // 检查当前用户是否有权限删除这个分类
        checkCategoryPermission(category);
        
        // 检查是否是系统默认分类（系统默认分类不能删除）
        if (Boolean.TRUE.equals(category.getIsDefault())) {
            throw new IllegalStateException("系统默认分类不可删除");
        }
        
        // 检查是否有文章使用该分类
        LambdaQueryWrapper<Article> articleQuery = new LambdaQueryWrapper<>();
        articleQuery.eq(Article::getCategoryId, id);
        Long articleCount = articleMapper.selectCount(articleQuery);
        if (articleCount > 0) {
            throw new IllegalStateException("该分类下有文章，无法删除");
        }
        
        // 删除分类
        int deleteCount = categoryMapper.deleteById(id);
        return deleteCount > 0;
    }

    @Override
    public List<Category> getAllCategories() {
        // 按排序字段升序排序
        LambdaQueryWrapper<Category> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByAsc(Category::getSortOrder);
        return categoryMapper.selectList(queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateSortOrder(Long id, Integer sortOrder) {
        Assert.notNull(id, "分类ID不能为空");
        Assert.notNull(sortOrder, "排序值不能为空");
        
        // 检查分类是否存在
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            return false;
        }
        
        // 检查当前用户是否有权限修改这个分类的排序
        checkCategoryPermission(category);
        
        // 更新排序值
        category.setSortOrder(sortOrder);
        int updateCount = categoryMapper.updateById(category);
        return updateCount > 0;
    }

    /**
     * 获取当前用户的最大排序值
     * 使用原生SQL方法避免DataPermissionInterceptor的bug
     */
    private Integer getMaxSortOrder() {
        // 获取当前用户ID
        Long currentUserId = UserContextHolder.getUserId();
        if (currentUserId == null) {
            // 未登录用户无法获取分类
            return 0;
        }
        
        // 使用原生SQL查询获取当前用户的最大排序值（包含公共分类）
        Category category = categoryMapper.selectMaxSortOrderForUser(currentUserId);
        return category != null ? category.getSortOrder() : 0;
    }

    /**
     * 检查当前用户是否有权限查看指定分类
     * @param category 要查看的目标分类
     * @return true 表示有权限查看
     */
    private boolean hasCategoryViewPermission(Category category) {
        // 管理员可以查看所有分类
        if (UserContextHolder.isAdmin()) {
            return true;
        }
        
        // 获取当前用户ID
        Long currentUserId = UserContextHolder.getUserId();
        if (currentUserId == null) {
            // 未登录用户只能查看系统公共分类(user_id=0)
            return category.getUserId() == null || category.getUserId() == 0;
        }
        
        // 普通用户可以查看自己的分类和系统公共分类
        return category.getUserId() == null || 
               category.getUserId() == 0 || 
               category.getUserId().equals(currentUserId);
    }

    /**
     * 检查当前用户是否有权限操作指定分类
     * @param category 要操作的目标分类
     * @throws PermissionDeniedException 如果用户没有权限
     */
    private void checkCategoryPermission(Category category) {
        // 管理员可以操作所有分类
        if (UserContextHolder.isAdmin()) {
            return;
        }
        
        // 获取当前用户ID
        Long currentUserId = UserContextHolder.getUserId();
        if (currentUserId == null) {
            throw new PermissionDeniedException("用户未登录，无法操作分类");
        }
        
        // 普通用户只能操作自己的分类
        if (!currentUserId.equals(category.getUserId())) {
            throw new PermissionDeniedException("没有权限操作该分类");
        }
    }
}
