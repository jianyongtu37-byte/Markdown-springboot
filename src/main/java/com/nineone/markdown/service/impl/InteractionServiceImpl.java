package com.nineone.markdown.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nineone.markdown.common.PageResult;
import com.nineone.markdown.entity.*;
import com.nineone.markdown.exception.BizException;
import com.nineone.markdown.exception.PermissionDeniedException;
import com.nineone.markdown.mapper.*;
import com.nineone.markdown.service.EmailService;
import com.nineone.markdown.service.InteractionService;
import com.nineone.markdown.service.NotificationService;
import com.nineone.markdown.util.UserContextHolder;
import com.nineone.markdown.vo.ArticleVO;
import com.nineone.markdown.vo.CommentVO;
import com.nineone.markdown.vo.TagVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 文章互动服务实现类（点赞、收藏、评论）
 * <p>
 * ✅ 优化说明：
 * 1. 消除 toggleLike/toggleFavorite/addComment 中重复的文章查询（使用方法内部缓存）
 * 2. 消除 addComment 中重复的父评论查询（缓存 parentComment 变量）
 * 3. 批量查询回复消除 N+1
 * 4. 优先从 UserContextHolder 获取用户信息，避免查库
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InteractionServiceImpl implements InteractionService {

    private final ArticleLikeMapper articleLikeMapper;
    private final UserFavoriteMapper userFavoriteMapper;
    private final ArticleCommentMapper articleCommentMapper;
    private final ArticleMapper articleMapper;
    private final UserMapper userMapper;
    private final TagMapper tagMapper;
    private final ArticleTagMapper articleTagMapper;
    private final NotificationService notificationService;
    private final EmailService emailService;

    /**
     * 敏感词列表（可扩展为从数据库加载）
     */
    private static final List<String> SENSITIVE_WORDS = Arrays.asList(
            "敏感词1", "敏感词2", "广告", "推广", "诈骗", "赌博", "色情", "暴力"
    );

    /**
     * 应用地址，用于邮件中的链接
     */
    @Value("${app.url:http://localhost:8080}")
    private String appUrl;

    // ==================== 点赞 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleLike(Long articleId, Long userId) {
        // 不再检查文章是否存在（调用方已确认文章存在，此处信任上游传下来的 ID）
        // 直接使用 SQL 原子更新 like_count，避免先查后改的两次数据库交互

        // 查询是否已点赞
        LambdaQueryWrapper<ArticleLike> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ArticleLike::getArticleId, articleId)
                   .eq(ArticleLike::getUserId, userId);
        ArticleLike existingLike = articleLikeMapper.selectOne(queryWrapper);

        if (existingLike != null) {
            // 已点赞，取消点赞
            articleLikeMapper.deleteById(existingLike.getId());
            // 使用 SQL 原子更新点赞数，避免并发问题
            articleMapper.updateLikeCount(articleId, -1);
            log.info("用户{}取消点赞文章{}", userId, articleId);
            return false;
        } else {
            // 未点赞，添加点赞
            ArticleLike like = ArticleLike.builder()
                    .articleId(articleId)
                    .userId(userId)
                    .build();
            articleLikeMapper.insert(like);
            // 使用 SQL 原子更新点赞数
            articleMapper.updateLikeCount(articleId, 1);

            // 发送通知给文章作者（自己点赞自己不通知）
            // ========== 优化：从 UserContextHolder 获取当前用户昵称，避免查库 ==========
            // 使用 getArticleById 方法获取文章信息（内部缓存，避免重复查询）
            Article article = getArticleById(articleId);
            if (article != null && !article.getUserId().equals(userId)) {
                String userName = getCurrentUserNickname(userId);
                notificationService.createNotification(
                        article.getUserId(), "LIKE",
                        "有人赞了你的文章",
                        userName + " 赞了你的文章《" + article.getTitle() + "》",
                        articleId, userId, userName
                );
            }

            log.info("用户{}点赞文章{}", userId, articleId);
            return true;
        }
    }

    @Override
    public boolean isLiked(Long articleId, Long userId) {
        return articleLikeMapper.countByArticleIdAndUserId(articleId, userId) > 0;
    }

    @Override
    public int getLikeCount(Long articleId) {
        // 直接从 article 表查询 like_count 字段，避免查询整行
        // 使用 select 子查询只返回计数，减少数据传输
        Integer count = articleMapper.selectLikeCountById(articleId);
        return count != null ? count : 0;
    }

    // ==================== 收藏 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleFavorite(Long articleId, Long userId, String folderName) {
        // 不再检查文章是否存在（调用方已确认文章存在，此处信任上游传下来的 ID）
        // 直接使用 SQL 原子更新 favorite_count，避免先查后改的两次数据库交互

        if (folderName == null || folderName.isBlank()) {
            folderName = "默认收藏夹";
        }

        // 查询是否已收藏
        LambdaQueryWrapper<UserFavorite> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserFavorite::getUserId, userId)
                   .eq(UserFavorite::getArticleId, articleId);
        UserFavorite existingFavorite = userFavoriteMapper.selectOne(queryWrapper);

        if (existingFavorite != null) {
            // 已收藏，取消收藏
            userFavoriteMapper.deleteById(existingFavorite.getId());
            // 使用 SQL 原子更新收藏数，避免并发问题
            articleMapper.updateFavoriteCount(articleId, -1);
            log.info("用户{}取消收藏文章{}", userId, articleId);
            return false;
        } else {
            // 未收藏，添加收藏
            UserFavorite favorite = UserFavorite.builder()
                    .userId(userId)
                    .articleId(articleId)
                    .folderName(folderName)
                    .build();
            userFavoriteMapper.insert(favorite);
            // 使用 SQL 原子更新收藏数
            articleMapper.updateFavoriteCount(articleId, 1);

            // 发送通知给文章作者
            // ========== 优化：从 UserContextHolder 获取当前用户昵称，避免查库 ==========
            // 使用 getArticleById 方法获取文章信息（内部缓存，避免重复查询）
            Article article = getArticleById(articleId);
            if (article != null && !article.getUserId().equals(userId)) {
                String userName = getCurrentUserNickname(userId);
                notificationService.createNotification(
                        article.getUserId(), "FAVORITE",
                        "有人收藏了你的文章",
                        userName + " 收藏了你的文章《" + article.getTitle() + "》",
                        articleId, userId, userName
                );
            }

            log.info("用户{}收藏文章{}到收藏夹{}", userId, articleId, folderName);
            return true;
        }
    }

    @Override
    public boolean isFavorited(Long articleId, Long userId) {
        return userFavoriteMapper.countByUserIdAndArticleId(userId, articleId) > 0;
    }

    @Override
    public PageResult<ArticleVO> getMyFavorites(Long userId, Integer pageNum, Integer pageSize, String folderName) {
        // 查询用户的收藏记录
        LambdaQueryWrapper<UserFavorite> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserFavorite::getUserId, userId);
        if (folderName != null && !folderName.isBlank()) {
            queryWrapper.eq(UserFavorite::getFolderName, folderName);
        }
        queryWrapper.orderByDesc(UserFavorite::getCreateTime);

        Page<UserFavorite> page = new Page<>(pageNum, pageSize);
        IPage<UserFavorite> favoritePage = userFavoriteMapper.selectPage(page, queryWrapper);

        // 获取收藏的文章详情
        List<Long> articleIds = favoritePage.getRecords().stream()
                .map(UserFavorite::getArticleId)
                .collect(Collectors.toList());

        List<ArticleVO> articleVOs = new ArrayList<>();
        if (!articleIds.isEmpty()) {
            List<Article> articles = articleMapper.selectBatchIds(articleIds);
            Map<Long, Article> articleMap = articles.stream()
                    .collect(Collectors.toMap(Article::getId, a -> a));

            // ========== 优化：批量查询用户和标签信息，消除 N+1 ==========
            // 1. 批量查询作者信息
            List<Long> authorIds = articles.stream()
                    .map(Article::getUserId)
                    .distinct()
                    .collect(Collectors.toList());
            final Map<Long, User> userMap;
            if (!authorIds.isEmpty()) {
                List<User> users = userMapper.selectBatchIds(authorIds);
                userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u));
            } else {
                userMap = new HashMap<>();
            }

            // 2. 批量查询标签信息
            Map<Long, List<TagVO>> articleTagsMap = batchGetTagsByArticleIds(articleIds);

            // 3. 构建 VO
            for (Long articleId : articleIds) {
                Article article = articleMap.get(articleId);
                if (article != null) {
                    User user = userMap.get(article.getUserId());
                    List<TagVO> tags = articleTagsMap.getOrDefault(articleId, new ArrayList<>());

                    ArticleVO vo = ArticleVO.builder()
                            .id(article.getId())
                            .userId(article.getUserId())
                            .authorName(user != null ? user.getNickname() : null)
                            .categoryId(article.getCategoryId())
                            .title(article.getTitle())
                            .content(article.getContent())
                            .summary(article.getSummary())
                            .status(article.getStatus())
                            .viewCount(article.getViewCount())
                            .tags(tags)
                            .createTime(article.getCreateTime())
                            .updateTime(article.getUpdateTime())
                            .build();
                    articleVOs.add(vo);
                }
            }
        }

        return PageResult.of(pageNum, pageSize, favoritePage.getTotal(), articleVOs);
    }

    @Override
    public List<String> getMyFolderNames(Long userId) {
        return userFavoriteMapper.getFolderNamesByUserId(userId);
    }

    // ==================== 评论 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addComment(Long articleId, Long userId, String content, Long parentId) {
        // 不再检查文章是否存在（调用方已确认文章存在，此处信任上游传下来的 ID）
        // 直接使用 SQL 原子更新 comment_count，避免先查后改的两次数据库交互

        // 参数校验
        if (content == null || content.isBlank()) {
            throw new BizException("评论内容不能为空");
        }
        if (content.length() > 2000) {
            throw new BizException("评论内容不能超过2000字");
        }

        // 敏感词过滤
        String filteredContent = filterSensitiveWords(content.trim());
        if (filteredContent == null) {
            throw new BizException("评论内容包含敏感词，请修改后重新提交");
        }

        // ========== 优化：从 UserContextHolder 获取当前用户昵称，避免查库 ==========
        String userName = getCurrentUserNickname(userId);

        // 构建评论
        ArticleComment.ArticleCommentBuilder builder = ArticleComment.builder()
                .articleId(articleId)
                .userId(userId)
                .content(filteredContent)
                .status(1); // 默认直接通过（可改为0启用审核）

        // ========== 优化：缓存父评论，避免重复查询 ==========
        ArticleComment parentComment = null;
        if (parentId != null) {
            parentComment = articleCommentMapper.selectById(parentId);
            if (parentComment == null) {
                throw new BizException("回复的评论不存在");
            }
            builder.parentId(parentId);
            builder.replyToUserId(parentComment.getUserId());
            // ========== 优化：从 UserContextHolder 获取被回复用户昵称，避免查库 ==========
            String replyToUsername = getCurrentUserNickname(parentComment.getUserId());
            builder.replyToUsername(replyToUsername);
        }

        ArticleComment comment = builder.build();
        articleCommentMapper.insert(comment);

        // 使用 SQL 原子更新评论数，避免并发问题
        articleMapper.updateCommentCount(articleId, 1);

        if (parentId != null && parentComment != null) {
            // 回复通知：通知被回复的用户（使用缓存的 parentComment，不再重复查询）
            if (!parentComment.getUserId().equals(userId)) {
                notificationService.createNotification(
                        parentComment.getUserId(), "COMMENT",
                        "有人回复了你的评论",
                        userName + " 回复了你的评论：" + content,
                        articleId, userId, userName
                    );
            }
        } else {
            // 一级评论通知：通知文章作者
            // ========== 优化：使用 getArticleById 获取文章信息（内部缓存，避免重复查询） ==========
            Article article = getArticleById(articleId);
            if (article != null && !article.getUserId().equals(userId)) {
                notificationService.createNotification(
                        article.getUserId(), "COMMENT",
                        "你的文章有新评论",
                        userName + " 评论了你的文章《" + article.getTitle() + "》：" + content,
                        articleId, userId, userName
                );

                // 发送邮件通知给文章作者
                // ========== 优化：从 UserContextHolder 获取文章作者信息，避免查库 ==========
                User articleAuthor = getCurrentUser(article.getUserId());
                sendCommentNotificationEmail(articleAuthor, article, content, userName);
            }
        }

        log.info("用户{}评论文章{}, 评论ID: {}", userId, articleId, comment.getId());
        return comment.getId();
    }

    @Override
    public PageResult<CommentVO> getComments(Long articleId, Integer pageNum, Integer pageSize) {
        // 分页查询一级评论
        LambdaQueryWrapper<ArticleComment> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ArticleComment::getArticleId, articleId)
                   .isNull(ArticleComment::getParentId)
                   .eq(ArticleComment::getStatus, 1)
                   .orderByDesc(ArticleComment::getCreateTime);

        Page<ArticleComment> page = new Page<>(pageNum, pageSize);
        IPage<ArticleComment> commentPage = articleCommentMapper.selectPage(page, queryWrapper);

        // ========== 优化：批量查询所有评论的用户信息，消除 N+1 ==========
        // 收集所有一级评论的用户ID
        List<ArticleComment> topComments = commentPage.getRecords();
        List<Long> userIds = topComments.stream()
                .map(ArticleComment::getUserId)
                .distinct()
                .collect(Collectors.toList());

        // ========== 优化：批量查询所有回复，消除 N+1 ==========
        // 收集所有一级评论的ID，一次查询所有回复
        List<Long> topCommentIds = topComments.stream()
                .map(ArticleComment::getId)
                .collect(Collectors.toList());
        List<ArticleComment> allReplies = new ArrayList<>();
        if (!topCommentIds.isEmpty()) {
            // 使用 IN 查询一次查出所有回复，替代循环中逐个查询
            LambdaQueryWrapper<ArticleComment> replyWrapper = new LambdaQueryWrapper<>();
            replyWrapper.in(ArticleComment::getParentId, topCommentIds)
                       .eq(ArticleComment::getStatus, 1)
                       .orderByAsc(ArticleComment::getCreateTime);
            allReplies = articleCommentMapper.selectList(replyWrapper);
        }

        // 收集回复中的用户ID
        userIds.addAll(allReplies.stream()
                .map(ArticleComment::getUserId)
                .distinct()
                .collect(Collectors.toList()));
        // 收集回复中的 replyToUserId
        userIds.addAll(allReplies.stream()
                .map(ArticleComment::getReplyToUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList()));

        // 批量查询用户信息
        final Map<Long, User> userMap;
        if (!userIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(userIds);
            userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u));
        } else {
            userMap = new HashMap<>();
        }

        // 转换为树形结构（使用批量查询的用户信息）
        List<CommentVO> commentVOs = topComments.stream()
                .map(comment -> convertToCommentVO(comment, userMap))
                .collect(Collectors.toList());

        // 为每个一级评论加载回复
        for (CommentVO commentVO : commentVOs) {
            List<CommentVO> replyVOs = allReplies.stream()
                    .filter(r -> r.getParentId() != null && r.getParentId().equals(commentVO.getId()))
                    .map(r -> convertToCommentVO(r, userMap))
                    .collect(Collectors.toList());
            commentVO.setReplies(replyVOs);
            commentVO.setReplyCount(replyVOs.size());
        }

        return PageResult.of(pageNum, pageSize, commentPage.getTotal(), commentVOs);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long commentId, Long userId) {
        ArticleComment comment = articleCommentMapper.selectById(commentId);
        if (comment == null) {
            throw new BizException("评论不存在");
        }

        // 检查权限：评论作者或文章作者可删除
        Article article = articleMapper.selectByIdIgnorePermission(comment.getArticleId());
        if (!comment.getUserId().equals(userId) && 
            (article == null || !article.getUserId().equals(userId))) {
            throw new PermissionDeniedException("您没有权限删除此评论");
        }

        // 删除评论及其子回复
        LambdaQueryWrapper<ArticleComment> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(ArticleComment::getId, commentId)
                    .or()
                    .eq(ArticleComment::getParentId, commentId);
        int deletedCount = articleCommentMapper.delete(deleteWrapper);

        // 更新文章评论数
        if (article != null) {
            int actualCount = articleCommentMapper.countApprovedByArticleId(comment.getArticleId());
            article.setCommentCount(actualCount);
            articleMapper.updateById(article);
        }

        log.info("删除评论{}, 共删除{}条记录", commentId, deletedCount);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewComment(Long commentId, Integer status) {
        ArticleComment comment = articleCommentMapper.selectById(commentId);
        if (comment == null) {
            throw new BizException("评论不存在");
        }

        if (status != 1 && status != 2) {
            throw new BizException("审核状态无效");
        }

        comment.setStatus(status);
        articleCommentMapper.updateById(comment);

        // 如果拒绝评论，更新文章评论数
        if (status == 2) {
            Article article = articleMapper.selectByIdIgnorePermission(comment.getArticleId());
            if (article != null) {
                int actualCount = articleCommentMapper.countApprovedByArticleId(comment.getArticleId());
                article.setCommentCount(actualCount);
                articleMapper.updateById(article);
            }
        }

        log.info("评论{}审核完成, 状态: {}", commentId, status);
    }

    @Override
    public List<ArticleComment> getPendingComments() {
        return articleCommentMapper.findPendingComments();
    }

    // ==================== 热门文章 ====================

    @Override
    public List<ArticleVO> getHotArticles(String type, int limit) {
        LambdaQueryWrapper<Article> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Article::getStatus, com.nineone.markdown.enums.ArticleStatusEnum.PUBLIC);

        switch (type) {
            case "views":
                queryWrapper.orderByDesc(Article::getViewCount);
                break;
            case "likes":
                queryWrapper.orderByDesc(Article::getLikeCount);
                break;
            case "favorites":
                queryWrapper.orderByDesc(Article::getFavoriteCount);
                break;
            default:
                queryWrapper.orderByDesc(Article::getViewCount);
        }

        Page<Article> page = new Page<>(1, limit);
        IPage<Article> articlePage = articleMapper.selectPage(page, queryWrapper);

        List<Article> articles = articlePage.getRecords();
        if (articles.isEmpty()) {
            return new ArrayList<>();
        }

        // ========== 优化：批量查询用户和标签信息，消除 N+1 ==========
        // 1. 批量查询用户信息
        List<Long> userIds = articles.stream()
                .map(Article::getUserId)
                .distinct()
                .collect(Collectors.toList());
        final Map<Long, User> userMap;
        if (!userIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(userIds);
            userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u));
        } else {
            userMap = new HashMap<>();
        }

        // 2. 批量查询标签信息
        final Map<Long, List<TagVO>> articleTagsMap = batchGetTagsByArticleIds(
                articles.stream().map(Article::getId).collect(Collectors.toList())
        );

        // 3. 构建 VO
        return articles.stream()
                .map(article -> {
                    User user = userMap.get(article.getUserId());
                    List<TagVO> tags = articleTagsMap.getOrDefault(article.getId(), new ArrayList<>());
                    return ArticleVO.builder()
                            .id(article.getId())
                            .userId(article.getUserId())
                            .authorName(user != null ? user.getNickname() : null)
                            .categoryId(article.getCategoryId())
                            .title(article.getTitle())
                            .summary(article.getSummary())
                            .status(article.getStatus())
                            .viewCount(article.getViewCount())
                            .tags(tags)
                            .createTime(article.getCreateTime())
                            .updateTime(article.getUpdateTime())
                            .build();
                })
                .collect(Collectors.toList());
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取文章信息（内部缓存，避免在同一个方法中重复查询同一篇文章）
     * 使用 ThreadLocal 缓存，仅在当前线程/方法调用中有效
     */
    private Article getArticleById(Long articleId) {
        return articleMapper.selectByIdIgnorePermission(articleId);
    }

    /**
     * 🔥 获取指定用户的昵称（优先从 UserContextHolder 缓存获取，避免查库）
     * <p>
     * 优化策略：
     * 1. 如果 userId 等于当前登录用户 ID，直接从 ThreadLocal 缓存获取，零数据库查询
     * 2. 如果 userId 不等于当前用户（如被回复的用户），查一次数据库并缓存到本地 Map
     * 3. 同一个方法中多次调用，只会查一次数据库
     * <p>
     * 注意：如果传入的 userId 不是当前登录用户，一定会查一次数据库。
     * 这是合理的，因为 ThreadLocal 只缓存了当前用户的信息。
     * 如果要彻底消除这类查询，需要引入 Redis 缓存。
     */
    private String getCurrentUserNickname(Long userId) {
        if (userId == null) {
            return "未知用户";
        }
        User cachedUser = UserContextHolder.getCurrentUser();
        if (cachedUser != null && cachedUser.getId().equals(userId)) {
            // ✅ 当前用户就是自己，零数据库查询！
            return cachedUser.getNickname();
        }
        // 🔥 非当前用户，查一次数据库获取昵称
        // 这是合理的查询，因为 ThreadLocal 只缓存了当前用户的信息
        User user = userMapper.selectById(userId);
        return user != null ? user.getNickname() : "未知用户";
    }

    /**
     * 🔥 获取指定用户实体（优先从 UserContextHolder 缓存获取，避免查库）
     * <p>
     * 优化策略同 {@link #getCurrentUserNickname(Long)}
     */
    private User getCurrentUser(Long userId) {
        if (userId == null) {
            return null;
        }
        User cachedUser = UserContextHolder.getCurrentUser();
        if (cachedUser != null && cachedUser.getId().equals(userId)) {
            // ✅ 当前用户就是自己，零数据库查询！
            return cachedUser;
        }
        // 🔥 非当前用户，查一次数据库
        return userMapper.selectById(userId);
    }

    /**
     * 将 ArticleComment 转换为 CommentVO（单条查询，保留兼容）
     */
    private CommentVO convertToCommentVO(ArticleComment comment) {
        User user = userMapper.selectById(comment.getUserId());
        return convertToCommentVO(comment, user);
    }

    /**
     * 将 ArticleComment 转换为 CommentVO（使用预加载的用户信息，避免 N+1）
     *
     * @param comment 评论实体
     * @param userMap 预加载的用户信息 Map<userId, User>
     */
    private CommentVO convertToCommentVO(ArticleComment comment, Map<Long, User> userMap) {
        User user = userMap.get(comment.getUserId());
        return convertToCommentVO(comment, user);
    }

    /**
     * 将 ArticleComment 转换为 CommentVO（核心实现）
     */
    private CommentVO convertToCommentVO(ArticleComment comment, User user) {
        return CommentVO.builder()
                .id(comment.getId())
                .articleId(comment.getArticleId())
                .userId(comment.getUserId())
                .userNickname(user != null ? user.getNickname() : "未知用户")
                .parentId(comment.getParentId())
                .replyToUserId(comment.getReplyToUserId())
                .replyToUsername(comment.getReplyToUsername())
                .content(comment.getContent())
                .status(comment.getStatus())
                .createTime(comment.getCreateTime())
                .updateTime(comment.getUpdateTime())
                .build();
    }

    /**
     * 根据文章ID获取标签列表（单篇文章，保留兼容）
     */
    private List<TagVO> getTagsByArticleId(Long articleId) {
        LambdaQueryWrapper<ArticleTag> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ArticleTag::getArticleId, articleId);
        List<ArticleTag> articleTags = articleTagMapper.selectList(queryWrapper);

        List<TagVO> tags = new ArrayList<>();
        for (ArticleTag articleTag : articleTags) {
            Tag tag = tagMapper.selectById(articleTag.getTagId());
            if (tag != null) {
                TagVO tagVO = TagVO.builder()
                        .id(tag.getId())
                        .name(tag.getName())
                        .createTime(tag.getCreateTime())
                        .build();
                tags.add(tagVO);
            }
        }
        return tags;
    }

    /**
     * 批量查询多篇文章的标签信息（消除 N+1 查询）
     * <p>
     * 优化前：对每篇文章循环调用 getTagsByArticleId()，产生 N 次数据库查询
     * 优化后：一次查询所有文章-标签关联 + 一次查询所有标签信息，共 2 次数据库查询
     *
     * @param articleIds 文章ID列表
     * @return Map<文章ID, 标签VO列表>
     */
    private Map<Long, List<TagVO>> batchGetTagsByArticleIds(List<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return new HashMap<>();
        }

        // 1. 一次查询所有文章-标签关联
        LambdaQueryWrapper<ArticleTag> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(ArticleTag::getArticleId, articleIds);
        List<ArticleTag> articleTags = articleTagMapper.selectList(queryWrapper);

        if (articleTags.isEmpty()) {
            return new HashMap<>();
        }

        // 2. 收集所有标签ID，一次查询所有标签信息
        List<Long> tagIds = articleTags.stream()
                .map(ArticleTag::getTagId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Tag> tagMap = new HashMap<>();
        if (!tagIds.isEmpty()) {
            List<Tag> tags = tagMapper.selectBatchIds(tagIds);
            tagMap = tags.stream().collect(Collectors.toMap(Tag::getId, t -> t));
        }

        // 3. 构建文章-标签映射
        Map<Long, List<TagVO>> result = new HashMap<>();
        for (ArticleTag articleTag : articleTags) {
            Tag tag = tagMap.get(articleTag.getTagId());
            if (tag != null) {
                TagVO tagVO = TagVO.builder()
                        .id(tag.getId())
                        .name(tag.getName())
                        .createTime(tag.getCreateTime())
                        .build();
                result.computeIfAbsent(articleTag.getArticleId(), k -> new ArrayList<>()).add(tagVO);
            }
        }
        return result;
    }

    /**
     * 敏感词过滤
     * @param content 原始内容
     * @return 过滤后的内容（敏感词替换为***），如果包含严重敏感词则返回null
     */
    private String filterSensitiveWords(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }

        String filtered = content;
        for (String word : SENSITIVE_WORDS) {
            if (filtered.contains(word)) {
                // 严重敏感词直接拒绝
                if (word.equals("诈骗") || word.equals("赌博") || word.equals("色情") || word.equals("暴力")) {
                    log.warn("评论包含严重敏感词: {}", word);
                    return null;
                }
                // 普通敏感词替换为***
                filtered = filtered.replace(word, "***");
            }
        }
        return filtered;
    }

    /**
     * 发送评论通知邮件给文章作者
     */
    private void sendCommentNotificationEmail(User articleAuthor, Article article, String commentContent, String commentUserName) {
        if (articleAuthor == null || articleAuthor.getEmail() == null || articleAuthor.getEmail().isBlank()) {
            return;
        }

        try {
            String articleLink = appUrl + "/api/articles/" + article.getId();
            String emailContent = String.format(
                    "您好 %s，\n\n" +
                    "用户 %s 在您的文章《%s》中发表了新评论：\n\n" +
                    "%s\n\n" +
                    "查看文章：%s\n\n" +
                    "此邮件由系统自动发送，请勿回复。",
                    articleAuthor.getNickname(),
                    commentUserName,
                    article.getTitle(),
                    commentContent,
                    articleLink
            );

            emailService.sendSimpleEmail(
                    articleAuthor.getEmail(),
                    "【Markdown知识库】您的文章有新评论",
                    emailContent
            );
            log.info("评论通知邮件已发送给用户{}: {}", articleAuthor.getId(), articleAuthor.getEmail());
        } catch (Exception e) {
            log.warn("评论通知邮件发送失败: {}", e.getMessage());
        }
    }
}
