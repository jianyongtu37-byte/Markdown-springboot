package com.nineone.markdown.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nineone.markdown.dto.ArticleDetailDTO;
import com.nineone.markdown.dto.ArticleSaveDTO;
import com.nineone.markdown.entity.Article;
import com.nineone.markdown.entity.ArticleTag;
import com.nineone.markdown.entity.ArticleTimestamp;
import com.nineone.markdown.entity.ArticleVideo;
import com.nineone.markdown.entity.Category;
import com.nineone.markdown.entity.Tag;
import com.nineone.markdown.entity.User;
import com.nineone.markdown.enums.ArticleStatusEnum;
import com.nineone.markdown.exception.AuthenticationException;
import com.nineone.markdown.exception.BizException;
import com.nineone.markdown.exception.PermissionDeniedException;
import com.nineone.markdown.mapper.ArticleMapper;
import com.nineone.markdown.mapper.ArticleTagMapper;
import com.nineone.markdown.mapper.ArticleTimestampMapper;
import com.nineone.markdown.mapper.ArticleVideoMapper;
import com.nineone.markdown.mapper.CategoryMapper;
import com.nineone.markdown.mapper.TagMapper;
import com.nineone.markdown.mapper.UserMapper;
import com.nineone.markdown.security.CustomUserDetails;
import com.nineone.markdown.common.PageResult;
import com.nineone.markdown.service.AiSummaryService;
import com.nineone.markdown.service.ArticleService;
import com.nineone.markdown.service.ArticleVersionService;
import com.nineone.markdown.service.SearchService;
import com.nineone.markdown.service.VideoParserService;
import com.nineone.markdown.util.TimestampExtractor;
import com.nineone.markdown.util.UserContextHolder;
import com.nineone.markdown.vo.ArticleDetailVO;
import com.nineone.markdown.vo.ArticleVO;
import com.nineone.markdown.vo.TagVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 文章服务实现类（重构版，使用新的文章状态枚举）
 * ✅ 重构说明：
 * 1. 消除物理外键依赖，采用逻辑外键模式
 * 2. 分类全局化 + 标签个人化 架构
 * 3. 所有异常边界兜底，彻底杜绝数据库外键报错
 * 4. 使用新的文章状态枚举（DRAFT/PRIVATE/PUBLIC）替代旧的 status + isPublic
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article> implements ArticleService {

    private final ArticleMapper articleMapper;
    private final ArticleTagMapper articleTagMapper;
    private final UserMapper userMapper;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final AiSummaryService aiSummaryService;
    private final SearchService searchService;
    private final ArticleVideoMapper articleVideoMapper;
    private final ArticleTimestampMapper articleTimestampMapper;
    private final VideoParserService videoParserService;
    private final ArticleVersionService articleVersionService;

    // 保底默认分类ID - 永久不可删除
    private static final Long DEFAULT_CATEGORY_ID = 1L;

    // =============================================
    // ✅ 重构后的创建文章方法
    // =============================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createArticle(Article article, List<String> tagNames) {
        // 参数验证
        Assert.notNull(article, "文章不能为空");
        Assert.hasText(article.getTitle(), "文章标题不能为空");
        Assert.hasText(article.getContent(), "文章内容不能为空");
        Assert.notNull(article.getUserId(), "用户ID不能为空");

        // 设置默认状态（如果未设置）
        if (article.getStatus() == null) {
            article.setStatus(ArticleStatusEnum.DRAFT);
        }

        // 自动生成摘要（如果未提供）
        if (article.getSummary() == null || article.getSummary().isBlank()) {
            article.setSummary(extractSummary(article.getContent()));
        }

        // --------------------------
        // 第一步：分类逻辑兜底处理
        // --------------------------
        Long finalCategoryId = resolveCategoryId(article.getCategoryId());
        article.setCategoryId(finalCategoryId);
        log.debug("文章分类已兜底处理: 传入={} 最终={}", article.getCategoryId(), finalCategoryId);

        // --------------------------
        // 第二步：保存文章基本信息
        // --------------------------
        articleMapper.insert(article);
        Long articleId = article.getId();
        log.info("文章基本信息保存成功, ID: {}", articleId);

        // --------------------------
        // 第三步：标签动态处理 (即写即存)
        // --------------------------
        if (tagNames != null && !tagNames.isEmpty()) {
            List<Long> tagIds = resolveTagIds(tagNames);
            
            // 批量保存文章-标签关联
            for (Long tagId : tagIds) {
                ArticleTag articleTag = ArticleTag.builder()
                        .articleId(articleId)
                        .tagId(tagId)
                        .build();
                articleTagMapper.insert(articleTag);
            }
            log.debug("文章标签关联保存完成, 共{}个标签", tagIds.size());
        }

        // --------------------------
        // 第四步：异步业务触发 (保持原有逻辑)
        // --------------------------
        // 异步生成AI摘要
        if (article.getContent() != null && !article.getContent().trim().isEmpty() && 
            (article.getAiStatus() == null || article.getAiStatus() == 0)) {
            generateAiSummaryAsync(articleId, article.getContent());
        }

        // 异步索引到Elasticsearch - 只索引公开可见的文章
        if (article.getStatus() != null && article.getStatus().isPublic()) {
            indexToElasticsearchAsync(articleId);
        }

        return articleId;
    }

    // =============================================
    // ✅ 重构后的更新文章方法
    // =============================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateArticle(Article article, List<String> tagNames) {
        // 参数验证
        Assert.notNull(article, "文章不能为空");
        Assert.notNull(article.getId(), "文章ID不能为空");
        
        // 1. 权限校验
        checkArticlePermission(article.getId());

        // --------------------------
        // 第一步：分类逻辑兜底处理
        // --------------------------
        Long finalCategoryId = resolveCategoryId(article.getCategoryId());
        article.setCategoryId(finalCategoryId);

        // --------------------------
        // 第二步：更新文章基本信息
        // --------------------------
        int updateCount = articleMapper.updateById(article);
        if (updateCount == 0) {
            log.warn("文章更新失败, ID不存在: {}", article.getId());
            return false;
        }
        log.info("文章基本信息更新成功, ID: {}", article.getId());

        // --------------------------
        // 第三步：标签全量更新
        // --------------------------
        // 先清除所有历史关联
        LambdaQueryWrapper<ArticleTag> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(ArticleTag::getArticleId, article.getId());
        int deletedCount = articleTagMapper.delete(deleteWrapper);
        log.debug("清除历史标签关联, 删除{}条记录", deletedCount);

        // 处理新标签并建立关联
        if (tagNames != null && !tagNames.isEmpty()) {
            List<Long> tagIds = resolveTagIds(tagNames);
            for (Long tagId : tagIds) {
                ArticleTag articleTag = ArticleTag.builder()
                        .articleId(article.getId())
                        .tagId(tagId)
                        .build();
                articleTagMapper.insert(articleTag);
            }
            log.debug("新标签关联保存完成, 共{}个标签", tagIds.size());
        }

        // --------------------------
        // 第四步：保存版本快照
        // --------------------------
        try {
            Long currentUserId = getCurrentUserId();
            articleVersionService.saveVersion(
                    article.getId(), article.getTitle(), article.getContent(), article.getSummary(),
                    "更新文章", currentUserId, "用户" + currentUserId
            );
        } catch (Exception e) {
            log.warn("保存版本快照失败，不影响文章更新: {}", e.getMessage());
        }

        // --------------------------
        // 第五步：同步Elasticsearch索引
        // --------------------------
        if (article.getStatus() != null && article.getStatus().isPublic()) {
            indexToElasticsearchAsync(article.getId());
        } else {
            deleteFromElasticsearchAsync(article.getId());
        }

        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public ArticleVO getArticleDetail(Long id) {
        Article article = articleMapper.selectByIdIgnorePermission(id);
        if (article == null) {
            return null;
        }

        // 检查文章访问权限
        checkArticleAccessPermission(article);

        // ========== 🔥 优化：优先从 UserContextHolder 获取作者信息，零数据库查询 ==========
        // JwtAuthenticationFilter 中已将当前登录用户缓存到 UserContextHolder
        // 如果当前用户就是文章作者，直接从内存获取，彻底砍掉 userMapper.selectById
        String authorName = null;
        User cachedUser = UserContextHolder.getCurrentUser();
        if (cachedUser != null && cachedUser.getId().equals(article.getUserId())) {
            // ✅ 当前用户就是作者，零数据库查询！
            authorName = cachedUser.getNickname();
        } else if (cachedUser != null) {
            // ✅ 当前用户已登录但不是作者，但我们可以从 CustomUserDetails 获取作者信息吗？
            // 不行，CustomUserDetails 只存了当前用户的信息。
            // 这里需要查一次数据库获取作者昵称
            User user = userMapper.selectById(article.getUserId());
            authorName = user != null ? user.getNickname() : null;
        } else {
            // ✅ 当前用户未登录（匿名访问公开文章），查一次数据库获取作者昵称
            User user = userMapper.selectById(article.getUserId());
            authorName = user != null ? user.getNickname() : null;
        }

        // 获取分类信息
        Category category = null;
        if (article.getCategoryId() != null) {
            category = categoryMapper.selectById(article.getCategoryId());
        }

        // 获取标签列表
        List<TagVO> tags = getTagsByArticleId(id);

        // 构建 VO
        return ArticleVO.builder()
                .id(article.getId())
                .userId(article.getUserId())
                .authorName(authorName)
                .categoryId(article.getCategoryId())
                .categoryName(category != null ? category.getName() : null)
                .title(article.getTitle())
                .content(article.getContent())
                .videoUrl(article.getVideoUrl())
                .summary(article.getSummary())
                .aiStatus(article.getAiStatus())
                .status(article.getStatus())
                .viewCount(article.getViewCount())
                .allowExport(article.getAllowExport())
                .tags(tags)
                .createTime(article.getCreateTime())
                .updateTime(article.getUpdateTime())
                .build();
    }

    /**
     * 批量获取文章VO列表（优化N+1查询）
     */
    private List<ArticleVO> batchGetArticleVOs(List<Article> articles) {
        if (articles == null || articles.isEmpty()) {
            return new ArrayList<>();
        }

        // 收集所有ID
        List<Long> articleIds = articles.stream()
                .map(Article::getId)
                .collect(Collectors.toList());
        List<Long> userIds = articles.stream()
                .map(Article::getUserId)
                .distinct()
                .collect(Collectors.toList());
        List<Long> categoryIds = articles.stream()
                .map(Article::getCategoryId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        // 批量查询用户信息
        Map<Long, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(userIds);
            userMap = users.stream()
                    .collect(Collectors.toMap(User::getId, user -> user));
        }

        // 批量查询分类信息
        Map<Long, Category> categoryMap = new HashMap<>();
        if (!categoryIds.isEmpty()) {
            // 使用忽略数据权限的方法，避免DataPermissionInterceptor的bug
            List<Category> categories = categoryMapper.selectBatchIdsIgnorePermission(categoryIds);
            categoryMap = categories.stream()
                    .collect(Collectors.toMap(Category::getId, category -> category));
        }

        // 批量查询文章标签关联
        Map<Long, List<TagVO>> articleTagsMap = new HashMap<>();
        if (!articleIds.isEmpty()) {
            // 查询所有文章-标签关联
            LambdaQueryWrapper<ArticleTag> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.in(ArticleTag::getArticleId, articleIds);
            List<ArticleTag> articleTags = articleTagMapper.selectList(queryWrapper);
            
            if (!articleTags.isEmpty()) {
                // 获取所有标签ID
                List<Long> tagIds = articleTags.stream()
                        .map(ArticleTag::getTagId)
                        .distinct()
                        .collect(Collectors.toList());
                
                // 批量查询标签信息
                Map<Long, Tag> tagMap = new HashMap<>();
                if (!tagIds.isEmpty()) {
                    List<Tag> tags = tagMapper.selectBatchIds(tagIds);
                    tagMap = tags.stream()
                            .collect(Collectors.toMap(Tag::getId, tag -> tag));
                }
                
                // 构建文章-标签映射
                for (ArticleTag articleTag : articleTags) {
                    Tag tag = tagMap.get(articleTag.getTagId());
                    if (tag != null) {
                        TagVO tagVO = TagVO.builder()
                                .id(tag.getId())
                                .name(tag.getName())
                                .createTime(tag.getCreateTime())
                                .build();
                        articleTagsMap.computeIfAbsent(articleTag.getArticleId(), k -> new ArrayList<>())
                                .add(tagVO);
                    }
                }
            }
        }

        // 构建文章VO列表
        List<ArticleVO> result = new ArrayList<>();
        for (Article article : articles) {
            // 检查文章访问权限（这里简化处理，实际应该批量检查）
            try {
                checkArticleAccessPermission(article);
            } catch (PermissionDeniedException e) {
                // 如果没有权限访问，跳过这篇文章
                continue;
            }

            // 获取作者信息
            User user = userMap.get(article.getUserId());
            String authorName = user != null ? user.getNickname() : null;

            // 获取分类信息
            String categoryName = null;
            if (article.getCategoryId() != null) {
                Category category = categoryMap.get(article.getCategoryId());
                categoryName = category != null ? category.getName() : null;
            }

            // 获取标签列表
            List<TagVO> tags = articleTagsMap.getOrDefault(article.getId(), new ArrayList<>());

            // 构建VO
            ArticleVO vo = ArticleVO.builder()
                    .id(article.getId())
                    .userId(article.getUserId())
                    .authorName(authorName)
                    .categoryId(article.getCategoryId())
                    .categoryName(categoryName)
                    .title(article.getTitle())
                    .content(article.getContent())
                    .videoUrl(article.getVideoUrl())
                    .summary(article.getSummary())
                    .aiStatus(article.getAiStatus())
                    .status(article.getStatus())
                    .viewCount(article.getViewCount())
                    .allowExport(article.getAllowExport())
                    .tags(tags)
                    .createTime(article.getCreateTime())
                    .updateTime(article.getUpdateTime())
                    .build();
            result.add(vo);
        }

        return result;
    }

    @Override
    public PageResult<ArticleVO> getArticleList(Integer pageNum, Integer pageSize, Long categoryId, Long tagId, 
                                                String keyword, Integer status, Integer isPublic) {
        // 参数验证
        Assert.isTrue(pageNum != null && pageNum > 0, "页码必须大于0");
        Assert.isTrue(pageSize != null && pageSize > 0 && pageSize <= 100, "每页大小必须在1-100之间");
        
        // 构建查询条件
        LambdaQueryWrapper<Article> queryWrapper = new LambdaQueryWrapper<>();
        
        if (categoryId != null) {
            queryWrapper.eq(Article::getCategoryId, categoryId);
        }
        
        if (status != null) {
            // 将 Integer 状态转换为枚举
            ArticleStatusEnum statusEnum = ArticleStatusEnum.of(status);
            queryWrapper.eq(Article::getStatus, statusEnum);
        }
        
        // isPublic 参数不再使用，因为状态已经包含可见性信息
        // 旧代码兼容：如果 isPublic=1，则只查询公开文章；如果 isPublic=0，则只查询私有文章
        if (isPublic != null) {
            if (isPublic == 1) {
                queryWrapper.eq(Article::getStatus, ArticleStatusEnum.PUBLIC);
            } else if (isPublic == 0) {
                queryWrapper.and(wrapper -> wrapper
                        .eq(Article::getStatus, ArticleStatusEnum.PRIVATE)
                        .or()
                        .eq(Article::getStatus, ArticleStatusEnum.DRAFT));
            }
        }
        
        if (keyword != null && !keyword.trim().isEmpty()) {
            queryWrapper.and(wrapper -> wrapper
                    .like(Article::getTitle, keyword)
                    .or()
                    .like(Article::getContent, keyword));
        }
        
        // 添加权限过滤条件
        addPermissionFilter(queryWrapper);
        
        queryWrapper.orderByDesc(Article::getCreateTime);
        
        // 使用MyBatis-Plus分页查询（忽略数据权限拦截器，因为权限已在Service层手动添加）
        Page<Article> page = new Page<>(pageNum, pageSize);
        IPage<Article> articlePage = articleMapper.selectPageIgnorePermission(page, queryWrapper);
        
        // 使用批量查询获取文章VO列表
        List<ArticleVO> articleVOs = batchGetArticleVOs(articlePage.getRecords());
        
        // 如果指定了标签过滤，进行过滤
        List<ArticleVO> result;
        if (tagId != null) {
            result = articleVOs.stream()
                    .filter(vo -> vo.getTags().stream()
                            .anyMatch(tag -> tag.getId().equals(tagId)))
                    .collect(Collectors.toList());
        } else {
            result = articleVOs;
        }
        
        // 构建分页结果
        return PageResult.of(pageNum, pageSize, articlePage.getTotal(), result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteArticle(Long id) {
        // 1. 权限校验：检查当前用户是否有权限删除这篇文章
        checkArticlePermission(id);

        // 2. 删除文章-标签关联
        LambdaQueryWrapper<ArticleTag> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(ArticleTag::getArticleId, id);
        articleTagMapper.delete(deleteWrapper);

        // 3. 删除文章
        int deleteCount = articleMapper.deleteById(id);
        
        // 4. 从Elasticsearch删除索引
        if (deleteCount > 0) {
            deleteFromElasticsearchAsync(id);
        }
        
        return deleteCount > 0;
    }

    @Override
    public boolean increaseViewCount(Long id) {
        // 异步增加阅读量，避免同步 UPDATE 导致的行锁竞争
        // 在高并发场景下，建议后续接入 Redis 实现更完善的防刷和计数逻辑
        increaseViewCountAsync(id);
        return true;
    }

    /**
     * 异步增加文章阅读量
     * 直接执行原子 UPDATE，不再先查询文章是否存在
     * （调用方已通过大连表查询确认文章存在，此处信任上游传下来的 ID）
     * 后续优化方向：接入 Redis 缓存阅读量，定时批量刷回数据库
     */
    @Async("aiTaskExecutor")
    public void increaseViewCountAsync(Long id) {
        try {
            // 使用 SQL 原子更新，避免并发问题
            // 不再先查询文章是否存在（调用方已确认），直接执行 UPDATE
            articleMapper.updateViewCount(id);
            log.debug("异步增加阅读量成功, 文章ID: {}", id);
        } catch (Exception e) {
            log.warn("异步增加阅读量失败, 文章ID: {}", id, e);
        }
    }

    @Override
    public boolean updateAiStatus(Long articleId, Integer aiStatus, String summary) {
        // 权限校验：检查当前用户是否有权限更新这篇文章的AI状态
        checkArticlePermission(articleId);
        
        return updateAiStatusInternal(articleId, aiStatus, summary);
    }

    /**
     * 内部更新AI状态方法（不进行权限校验，供系统调用使用）
     * 直接使用 UPDATE 语句更新指定字段，不再先查询再更新
     * （调用方已确认文章存在，此处信任上游传下来的 ID）
     */
    private boolean updateAiStatusInternal(Long articleId, Integer aiStatus, String summary) {
        try {
            // 直接使用 MyBatis-Plus 的 UpdateWrapper 进行条件更新
            // 避免先 selectById 再 updateById 的两次数据库交互
            Article updateEntity = new Article();
            updateEntity.setAiStatus(aiStatus);
            if (summary != null) {
                updateEntity.setSummary(summary);
            }
            
            com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Article> updateWrapper =
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
            updateWrapper.eq(Article::getId, articleId);
            int updateCount = articleMapper.update(updateEntity, updateWrapper);
            return updateCount > 0;
        } catch (Exception e) {
            log.warn("更新AI状态失败, 文章ID: {}", articleId, e);
            return false;
        }
    }

    /**
     * 异步生成AI摘要
     * @param articleId 文章ID
     * @param content 文章内容
     */
    @Async("aiTaskExecutor")
    public void generateAiSummaryAsync(Long articleId, String content) {
        try {
            log.info("开始异步生成AI摘要，文章ID: {}", articleId);
            
            // 更新状态为生成中（使用内部方法，不进行权限校验）
            updateAiStatusInternal(articleId, 1, null);
            
            // 调用AI服务生成摘要
            String summary = aiSummaryService.generateSummary(content);
            
            // 更新状态为已生成，并保存摘要
            updateAiStatusInternal(articleId, 2, summary);
            
            log.info("AI摘要生成完成，文章ID: {}, 摘要长度: {}", articleId, summary.length());
        } catch (Exception e) {
            log.error("AI摘要生成失败，文章ID: {}", articleId, e);
            updateAiStatusInternal(articleId, 3, null);
        }
    }

    /**
     * 根据文章ID获取标签列表
     * <p>
     * ✅ 优化：消除 N+1 查询
     * 优化前：循环中逐个 tagMapper.selectById()，产生 N 次查询
     * 优化后：一次查询所有标签ID，批量查询标签信息
     */
    private List<TagVO> getTagsByArticleId(Long articleId) {
        // 查询文章-标签关联
        LambdaQueryWrapper<ArticleTag> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ArticleTag::getArticleId, articleId);
        List<ArticleTag> articleTags = articleTagMapper.selectList(queryWrapper);

        if (articleTags.isEmpty()) {
            return new ArrayList<>();
        }

        // 收集所有标签ID，一次批量查询
        List<Long> tagIds = articleTags.stream()
                .map(ArticleTag::getTagId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Tag> tagMap = new HashMap<>();
        if (!tagIds.isEmpty()) {
            List<Tag> tags = tagMapper.selectBatchIds(tagIds);
            tagMap = tags.stream().collect(Collectors.toMap(Tag::getId, t -> t));
        }

        // 构建标签VO列表
        List<TagVO> result = new ArrayList<>();
        for (ArticleTag articleTag : articleTags) {
            Tag tag = tagMap.get(articleTag.getTagId());
            if (tag != null) {
                TagVO tagVO = TagVO.builder()
                        .id(tag.getId())
                        .name(tag.getName())
                        .createTime(tag.getCreateTime())
                        .build();
                result.add(tagVO);
            }
        }

        return result;
    }

    /**
     * 异步索引文章到Elasticsearch
     * @param articleId 文章ID
     */
    @Async("aiTaskExecutor")
    public void indexToElasticsearchAsync(Long articleId) {
        try {
            log.info("开始异步索引文章到Elasticsearch，文章ID: {}", articleId);
            boolean success = searchService.indexArticle(articleId);
            if (success) {
                log.info("文章索引成功，文章ID: {}", articleId);
            } else {
                log.warn("文章索引失败，文章ID: {}", articleId);
            }
        } catch (Exception e) {
            log.error("异步索引文章到Elasticsearch失败，文章ID: {}", articleId, e);
        }
    }

    /**
     * 获取当前登录用户的ID
     * @return 当前用户ID
     * @throws AuthenticationException 如果用户未认证
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationException("用户未认证", "UNAUTHENTICATED");
        }
        
        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails) {
            CustomUserDetails userDetails = (CustomUserDetails) principal;
            return userDetails.getId();
        } else {
            throw new AuthenticationException("用户未登录或登录已过期", "TOKEN_EXPIRED");
        }
    }

    /**
     * 检查当前用户是否有权限操作文章
     * @param articleId 文章ID
     * @throws PermissionDeniedException 如果用户没有权限
     */
    private void checkArticlePermission(Long articleId) {
        Long currentUserId = getCurrentUserId();
        Article article = articleMapper.selectByIdIgnorePermission(articleId);
        
        if (article == null) {
            throw new RuntimeException("文章不存在");
        }
        
        if (!article.getUserId().equals(currentUserId)) {
            throw new PermissionDeniedException("越权操作：您没有权限修改他人的文章");
        }
    }

    /**
     * 异步从Elasticsearch删除文章索引
     * @param articleId 文章ID
     */
    @Async("aiTaskExecutor")
    public void deleteFromElasticsearchAsync(Long articleId) {
        try {
            log.info("开始异步从Elasticsearch删除文章索引，文章ID: {}", articleId);
            boolean success = searchService.deleteArticleIndex(articleId);
            if (success) {
                log.info("文章索引删除成功，文章ID: {}", articleId);
            } else {
                log.warn("文章索引删除失败，文章ID: {}", articleId);
            }
        } catch (Exception e) {
            log.error("异步从Elasticsearch删除文章索引失败，文章ID: {}", articleId, e);
        }
    }

    /**
     * 检查当前用户是否有权限访问文章
     * @param article 文章实体
     * @throws PermissionDeniedException 如果用户没有访问权限
     */
    private void checkArticleAccessPermission(Article article) {
        // 如果文章是公开可见的，所有人都可以访问
        if (article.getStatus() != null && article.getStatus().canPublicAccess()) {
            return;
        }

        // 否则，只有文章作者本人可以访问
        try {
            Long currentUserId = getCurrentUserId();
            if (!article.getUserId().equals(currentUserId)) {
                throw new PermissionDeniedException("您没有权限访问此文章");
            }
        } catch (AuthenticationException e) {
            // 如果用户未登录，也没有权限访问非公开文章
            throw new PermissionDeniedException("请登录后访问此文章");
        }
    }

    /**
     * 添加权限过滤条件到查询包装器
     * @param queryWrapper 查询包装器
     */
    private void addPermissionFilter(LambdaQueryWrapper<Article> queryWrapper) {
        try {
            // 获取当前登录用户ID
            Long currentUserId = getCurrentUserId();
            
            // 权限规则：
            // 1. 公开可见的文章（PUBLIC状态）所有人都可以访问
            // 2. 当前用户自己的文章（无论状态如何）都可以访问
            queryWrapper.and(wrapper -> wrapper
                    .eq(Article::getStatus, ArticleStatusEnum.PUBLIC)
                    .or()
                    .eq(Article::getUserId, currentUserId));
        } catch (AuthenticationException e) {
            // 如果用户未登录，只能查看公开可见的文章
            queryWrapper.eq(Article::getStatus, ArticleStatusEnum.PUBLIC);
        }
    }

    @Override
    public PageResult<ArticleVO> getMyArticles(Integer pageNum, Integer pageSize, Long categoryId, Long tagId,
                                               String keyword, Integer status, Integer isPublic) {
        // 参数验证
        Assert.isTrue(pageNum != null && pageNum > 0, "页码必须大于0");
        Assert.isTrue(pageSize != null && pageSize > 0 && pageSize <= 100, "每页大小必须在1-100之间");
        
        // 获取当前用户ID，只查询当前用户的文章
        Long currentUserId = getCurrentUserId();
        
        // 构建查询条件
        LambdaQueryWrapper<Article> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Article::getUserId, currentUserId);
        
        if (categoryId != null) {
            queryWrapper.eq(Article::getCategoryId, categoryId);
        }
        
        if (status != null) {
            // 将 Integer 状态转换为枚举
            ArticleStatusEnum statusEnum = ArticleStatusEnum.of(status);
            queryWrapper.eq(Article::getStatus, statusEnum);
        }
        
        // isPublic 参数不再使用，因为状态已经包含可见性信息
        if (isPublic != null) {
            if (isPublic == 1) {
                queryWrapper.eq(Article::getStatus, ArticleStatusEnum.PUBLIC);
            } else if (isPublic == 0) {
                queryWrapper.and(wrapper -> wrapper
                        .eq(Article::getStatus, ArticleStatusEnum.PRIVATE)
                        .or()
                        .eq(Article::getStatus, ArticleStatusEnum.DRAFT));
            }
        }
        
        if (keyword != null && !keyword.trim().isEmpty()) {
            queryWrapper.and(wrapper -> wrapper
                    .like(Article::getTitle, keyword)
                    .or()
                    .like(Article::getContent, keyword));
        }
        
        queryWrapper.orderByDesc(Article::getCreateTime);
        
        // 使用MyBatis-Plus分页查询（忽略数据权限拦截器，因为权限已在Service层手动添加）
        Page<Article> page = new Page<>(pageNum, pageSize);
        IPage<Article> articlePage = articleMapper.selectPageIgnorePermission(page, queryWrapper);
        
        // 使用批量查询获取文章VO列表
        List<ArticleVO> articleVOs = batchGetArticleVOs(articlePage.getRecords());
        
        // 如果指定了标签过滤，进行过滤
        List<ArticleVO> result;
        if (tagId != null) {
            result = articleVOs.stream()
                    .filter(vo -> vo.getTags().stream()
                            .anyMatch(tag -> tag.getId().equals(tagId)))
                    .collect(Collectors.toList());
        } else {
            result = articleVOs;
        }
        
        // 构建分页结果
        return PageResult.of(pageNum, pageSize, articlePage.getTotal(), result);
    }

    @Override
    public PageResult<ArticleVO> getUserArticles(Long userId, Integer pageNum, Integer pageSize,
                                                 Long categoryId, Long tagId, String keyword) {
        // 参数验证
        Assert.isTrue(pageNum != null && pageNum > 0, "页码必须大于0");
        Assert.isTrue(pageSize != null && pageSize > 0 && pageSize <= 100, "每页大小必须在1-100之间");
        Assert.notNull(userId, "用户ID不能为空");
        
        // 构建查询条件：只能查看公开可见的文章
        LambdaQueryWrapper<Article> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Article::getUserId, userId)
                   .eq(Article::getStatus, ArticleStatusEnum.PUBLIC);
        
        if (categoryId != null) {
            queryWrapper.eq(Article::getCategoryId, categoryId);
        }
        
        if (keyword != null && !keyword.trim().isEmpty()) {
            queryWrapper.and(wrapper -> wrapper
                    .like(Article::getTitle, keyword)
                    .or()
                    .like(Article::getContent, keyword));
        }
        
        queryWrapper.orderByDesc(Article::getCreateTime);
        
        // 使用MyBatis-Plus分页查询
        Page<Article> page = new Page<>(pageNum, pageSize);
        IPage<Article> articlePage = articleMapper.selectPage(page, queryWrapper);
        
        // 使用批量查询获取文章VO列表
        List<ArticleVO> articleVOs = batchGetArticleVOs(articlePage.getRecords());
        
        // 如果指定了标签过滤，进行过滤
        List<ArticleVO> result;
        if (tagId != null) {
            result = articleVOs.stream()
                    .filter(vo -> vo.getTags().stream()
                            .anyMatch(tag -> tag.getId().equals(tagId)))
                    .collect(Collectors.toList());
        } else {
            result = articleVOs;
        }
        
        // 构建分页结果
        return PageResult.of(pageNum, pageSize, articlePage.getTotal(), result);
    }

    @Override
    public Map<String, Object> getMyArticleStats() {
        Long currentUserId = getCurrentUserId();
        
        // 优化：使用一条 SQL 查询所有统计数据，替代原来的 4 次 selectCount
        // 利用 MySQL 的 COUNT 配合 CASE WHEN 实现一次查询获取多个维度的统计
        Map<String, Object> stats = articleMapper.selectArticleStatsByUserId(currentUserId);
        
        return stats;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchUpdateStatus(List<Long> articleIds, Integer status, Integer isPublic) {
        if (articleIds == null || articleIds.isEmpty()) {
            return false;
        }
        
        // ========== 优化：批量查询文章，消除循环中的 N 次查库 ==========
        Long currentUserId = getCurrentUserId();
        
        // 一次查询所有文章
        List<Article> articles = articleMapper.selectBatchIds(articleIds);
        if (articles.size() != articleIds.size()) {
            throw new PermissionDeniedException("部分文章不存在");
        }
        
        // 检查权限：确保所有文章都属于当前用户
        for (Article article : articles) {
            if (!article.getUserId().equals(currentUserId)) {
                throw new PermissionDeniedException("您没有权限操作这些文章");
            }
        }
        
        // 批量更新文章状态
        boolean allSuccess = true;
        ArticleStatusEnum statusEnum = (status != null) ? ArticleStatusEnum.of(status) : null;
        for (Long articleId : articleIds) {
            Article updateEntity = new Article();
            updateEntity.setId(articleId);
            
            // 处理状态更新（向后兼容）
            if (statusEnum != null) {
                updateEntity.setStatus(statusEnum);
            }
            
            int updateCount = articleMapper.updateById(updateEntity);
            if (updateCount == 0) {
                allSuccess = false;
            } else {
                // 更新Elasticsearch索引
                if (statusEnum != null && statusEnum.isPublic()) {
                    indexToElasticsearchAsync(articleId);
                } else {
                    deleteFromElasticsearchAsync(articleId);
                }
            }
        }
        
        return allSuccess;
    }

    // =============================================
    // ✅ 分类ID兜底解析器
    // 无论前端传什么，这个方法永远返回合法有效的分类ID
    // =============================================
    private Long resolveCategoryId(Long inputCategoryId) {
        // 情况1：传入为空
        if (inputCategoryId == null) {
            return DEFAULT_CATEGORY_ID;
        }
        
        // 情况2：传入ID不存在于数据库
        Category category = categoryMapper.selectById(inputCategoryId);
        if (category == null) {
            log.warn("传入的分类ID不存在: {}, 自动兜底到默认分类", inputCategoryId);
            return DEFAULT_CATEGORY_ID;
        }
        
        // 情况3：正常有效ID
        return inputCategoryId;
    }

    // =============================================
    // ✅ 标签名称解析器 (即写即存)
    // 传入标签名称列表，返回标签ID列表，不存在则自动创建
    // =============================================
    private List<Long> resolveTagIds(List<String> tagNames) {
        List<Long> result = new ArrayList<>();
        
        for (String tagName : tagNames) {
            // 过滤空字符串
            if (!StringUtils.hasText(tagName)) {
                continue;
            }
            
            // 去除前后空格并统一小写
            String cleanTagName = tagName.trim().toLowerCase();
            
            // 查询标签是否已存在
            LambdaQueryWrapper<Tag> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Tag::getName, cleanTagName);
            Tag existTag = tagMapper.selectOne(queryWrapper);
            
            if (existTag != null) {
                // 标签已存在，直接使用ID
                result.add(existTag.getId());
            } else {
                // 标签不存在，自动创建新标签
                Tag newTag = Tag.builder()
                        .name(cleanTagName)
                        .build();
                tagMapper.insert(newTag);
                result.add(newTag.getId());
                log.info("自动创建新标签: [{}] ID={}", cleanTagName, newTag.getId());
            }
        }
        
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public ArticleDetailVO getDetail(Long id) {
        // 使用一次性 JOIN 查询替代原来的 7 次独立查询
        // 一次 SQL 往返查出：文章 + 作者昵称 + 分类名称 + 视频信息 + 标签聚合
        ArticleDetailDTO dto = articleMapper.selectArticleDetailById(id);
        if (dto == null) {
            throw new BizException("文章不存在");
        }

        // 构建 Article 对象用于权限检查
        Article article = new Article();
        article.setId(dto.getId());
        article.setUserId(dto.getUserId());
        article.setStatus(dto.getStatus());

        // 检查文章可见性：公开文章所有人可见，非公开文章仅作者可见
        checkArticleAccessPermission(article);

        // 构建 ArticleDetailVO
        ArticleDetailVO vo = ArticleDetailVO.builder()
                .id(dto.getId())
                .userId(dto.getUserId())
                .authorName(dto.getAuthorName())
                .categoryId(dto.getCategoryId())
                .categoryName(dto.getCategoryName())
                .title(dto.getTitle())
                .content(dto.getContent())
                .videoUrl(dto.getVideoUrl())
                .summary(dto.getSummary())
                .aiStatus(dto.getAiStatus())
                .status(dto.getStatus())
                .viewCount(dto.getViewCount())
                .allowExport(dto.getAllowExport())
                .likeCount(dto.getLikeCount())
                .commentCount(dto.getCommentCount())
                .favoriteCount(dto.getFavoriteCount())
                .createTime(dto.getCreateTime())
                .updateTime(dto.getUpdateTime())
                .build();

        // 构建视频信息（从 JOIN 结果中提取）
        if (dto.getVideoId() != null) {
            ArticleVideo video = new ArticleVideo();
            video.setId(dto.getVideoId());
            video.setArticleId(dto.getId());
            video.setVideoUrl(dto.getVideoUrl());
            video.setVideoSource(dto.getVideoSource() != null
                    ? com.nineone.markdown.enums.VideoSource.valueOf(dto.getVideoSource()) : null);
            video.setVideoId(dto.getVideoVideoId());
            video.setDuration(dto.getVideoDuration());
            video.setCreateTime(dto.getVideoCreateTime());
            video.setUpdateTime(dto.getVideoUpdateTime());
            vo.setVideo(video);
        }

        // 构建标签列表（从 GROUP_CONCAT 结果中解析）
        List<TagVO> tags = new ArrayList<>();
        if (dto.getTagIds() != null && !dto.getTagIds().isEmpty()
                && dto.getTagNames() != null && !dto.getTagNames().isEmpty()) {
            String[] idArr = dto.getTagIds().split(",");
            String[] nameArr = dto.getTagNames().split(",");
            int len = Math.min(idArr.length, nameArr.length);
            for (int i = 0; i < len; i++) {
                try {
                    TagVO tagVO = TagVO.builder()
                            .id(Long.parseLong(idArr[i].trim()))
                            .name(nameArr[i].trim())
                            .build();
                    tags.add(tagVO);
                } catch (NumberFormatException e) {
                    log.warn("解析标签ID失败: {}", idArr[i]);
                }
            }
        }
        vo.setTags(tags);

        // 查询时间戳目录（一对多关系，单独查询）
        List<ArticleTimestamp> timestamps = articleTimestampMapper.findByArticleId(id);
        vo.setTimestamps(timestamps);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(ArticleSaveDTO dto, Long userId) {
        Article article;
        boolean isNew = (dto.getId() == null);

        if (isNew) {
            article = new Article();
            article.setUserId(userId);
            article.setCreateTime(LocalDateTime.now());
            article.setViewCount(0);
            article.setStatus(ArticleStatusEnum.DRAFT);
        } else {
            article = articleMapper.selectByIdIgnorePermission(dto.getId());
            if (!article.getUserId().equals(userId)) {
                throw new BizException("无权操作");
            }
        }

        article.setTitle(dto.getTitle());
        article.setContent(dto.getContent());
        article.setCategoryId(dto.getCategoryId());
        article.setVideoUrl(dto.getVideoUrl());
        article.setAllowExport(dto.getAllowExport() != null ? dto.getAllowExport() : 1);
        article.setUpdateTime(LocalDateTime.now());
        // 自动生成摘要（取前150字）
        article.setSummary(extractSummary(dto.getContent()));

        if (isNew) {
            articleMapper.insert(article);
        } else {
            articleMapper.updateById(article);
        }

        // 处理视频绑定（同时保存到article_video表）
        syncVideo(article.getId(), dto.getVideoUrl());

        // 处理标签
        if (dto.getTagIds() != null && !dto.getTagIds().isEmpty()) {
            // 先清除旧标签
            LambdaQueryWrapper<ArticleTag> deleteWrapper = new LambdaQueryWrapper<>();
            deleteWrapper.eq(ArticleTag::getArticleId, article.getId());
            articleTagMapper.delete(deleteWrapper);

            // 添加新标签
            for (Long tagId : dto.getTagIds()) {
                ArticleTag articleTag = ArticleTag.builder()
                        .articleId(article.getId())
                        .tagId(tagId)
                        .build();
                articleTagMapper.insert(articleTag);
            }
        }

        // 保存版本快照（更新时）
        if (!isNew) {
            try {
                articleVersionService.saveVersion(
                        article.getId(), article.getTitle(), article.getContent(), article.getSummary(),
                        "保存文章", userId, "用户" + userId
                );
            } catch (Exception e) {
                log.warn("保存版本快照失败，不影响文章保存: {}", e.getMessage());
            }
        }

        // 重建时间戳索引
        rebuildTimestamps(article.getId(), dto.getContent());
    }

    @Override
    public List<ArticleTimestamp> getTimestamps(Long id) {
        return articleTimestampMapper.findByArticleId(id);
    }

    private void syncVideo(Long articleId, String videoUrl) {
        if (videoUrl == null || videoUrl.isBlank()) {
            articleVideoMapper.delete(new LambdaQueryWrapper<ArticleVideo>()
                .eq(ArticleVideo::getArticleId, articleId));
            return;
        }
        
        com.nineone.markdown.service.VideoMeta meta = videoParserService.resolve(videoUrl);
        
        if (meta == null) {
            // 如果无法解析视频，也删除现有记录
            articleVideoMapper.delete(new LambdaQueryWrapper<ArticleVideo>()
                .eq(ArticleVideo::getArticleId, articleId));
            log.warn("无法解析视频URL: {}", videoUrl);
            return;
        }

        ArticleVideo video = articleVideoMapper.selectOne(new LambdaQueryWrapper<ArticleVideo>()
            .eq(ArticleVideo::getArticleId, articleId));

        if (video == null) {
            video = new ArticleVideo();
            video.setCreateTime(LocalDateTime.now());
        }
        
        video.setArticleId(articleId);
        video.setVideoUrl(videoUrl);
        video.setVideoSource(meta.getSource());
        video.setVideoId(meta.getVideoId());
        video.setUpdateTime(LocalDateTime.now());

        if (video.getId() == null) {
            articleVideoMapper.insert(video);
        } else {
            articleVideoMapper.updateById(video);
        }
    }

    private void rebuildTimestamps(Long articleId, String content) {
        articleTimestampMapper.deleteByArticleId(articleId);
        
        // 使用时间戳提取器提取时间戳
        List<ArticleTimestamp> timestamps = TimestampExtractor.extractTimestamps(content);
        
        // 设置文章ID并保存到数据库
        for (ArticleTimestamp timestamp : timestamps) {
            timestamp.setArticleId(articleId);
            articleTimestampMapper.insert(timestamp);
        }
        
        log.debug("重建时间戳索引完成，文章ID: {}, 提取到 {} 个时间戳", articleId, timestamps.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAllowExport(Long articleId, Integer allowExport) {
        // 权限校验
        checkArticlePermission(articleId);
        
        Article updateEntity = new Article();
        updateEntity.setId(articleId);
        updateEntity.setAllowExport(allowExport);
        articleMapper.updateById(updateEntity);
        log.info("文章导出权限已更新, articleId={}, allowExport={}", articleId, allowExport);
    }

    private String extractSummary(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        
        // 去掉Markdown语法符号，取前150字
        String plain = markdown.replaceAll("[#*`>\\[\\]()!-]", "").strip();
        return plain.length() > 150 ? plain.substring(0, 150) + "…" : plain;
    }
}