package com.nineone.markdown.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nineone.common.result.PageResult;
import com.nineone.markdown.entity.*;
import com.nineone.markdown.exception.BizException;
import com.nineone.markdown.exception.PermissionDeniedException;
import com.nineone.markdown.mapper.*;
import com.nineone.markdown.service.ArticleCollaboratorService;
import com.nineone.markdown.vo.ArticleVO;
import com.nineone.markdown.vo.TagVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArticleCollaboratorServiceImpl implements ArticleCollaboratorService {

    private final ArticleCollaboratorMapper articleCollaboratorMapper;
    private final ArticleMapper articleMapper;
    private final ArticleTagMapper articleTagMapper;
    private final TagMapper tagMapper;
    private final UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addCollaborator(Long articleId, Long ownerUserId, Long collaboratorUserId, String permission) {
        Article article = articleMapper.selectByIdIgnorePermission(articleId);
        if (article == null) {
            throw new BizException("文章不存在");
        }
        if (!article.getUserId().equals(ownerUserId)) {
            throw new PermissionDeniedException("只有文章作者才能添加协作者");
        }
        if (ownerUserId.equals(collaboratorUserId)) {
            throw new BizException("不能添加自己为协作者");
        }
        if (!"EDIT".equals(permission) && !"VIEW".equals(permission)) {
            throw new BizException("权限类型无效，只能是 EDIT 或 VIEW");
        }

        LambdaQueryWrapper<ArticleCollaborator> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ArticleCollaborator::getArticleId, articleId)
                .eq(ArticleCollaborator::getUserId, collaboratorUserId);
        ArticleCollaborator existing = articleCollaboratorMapper.selectOne(wrapper);

        if (existing != null) {
            existing.setPermission(permission);
            articleCollaboratorMapper.updateById(existing);
            log.info("协作者权限已更新, articleId={}, userId={}, permission={}", articleId, collaboratorUserId, permission);
        } else {
            ArticleCollaborator collaborator = ArticleCollaborator.builder()
                    .articleId(articleId)
                    .userId(collaboratorUserId)
                    .permission(permission)
                    .build();
            articleCollaboratorMapper.insert(collaborator);
            log.info("协作者已添加, articleId={}, userId={}, permission={}", articleId, collaboratorUserId, permission);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeCollaborator(Long articleId, Long ownerUserId, Long collaboratorUserId) {
        Article article = articleMapper.selectByIdIgnorePermission(articleId);
        if (article == null) {
            throw new BizException("文章不存在");
        }
        if (!article.getUserId().equals(ownerUserId)) {
            throw new PermissionDeniedException("只有文章作者才能移除协作者");
        }

        LambdaQueryWrapper<ArticleCollaborator> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ArticleCollaborator::getArticleId, articleId)
                .eq(ArticleCollaborator::getUserId, collaboratorUserId);
        articleCollaboratorMapper.delete(wrapper);
        log.info("协作者已移除, articleId={}, userId={}", articleId, collaboratorUserId);
    }

    @Override
    public List<ArticleCollaborator> getCollaborators(Long articleId) {
        LambdaQueryWrapper<ArticleCollaborator> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ArticleCollaborator::getArticleId, articleId)
                .orderByDesc(ArticleCollaborator::getCreateTime);
        return articleCollaboratorMapper.selectList(wrapper);
    }

    @Override
    public PageResult<ArticleVO> getSharedArticles(Long userId, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<ArticleCollaborator> collabWrapper = new LambdaQueryWrapper<>();
        collabWrapper.eq(ArticleCollaborator::getUserId, userId);
        List<ArticleCollaborator> collabs = articleCollaboratorMapper.selectList(collabWrapper);
        if (collabs.isEmpty()) {
            return PageResult.of(pageNum, pageSize, 0L, new ArrayList<>());
        }

        List<Long> articleIds = collabs.stream()
                .map(ArticleCollaborator::getArticleId)
                .distinct()
                .collect(Collectors.toList());

        LambdaQueryWrapper<Article> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(Article::getId, articleIds)
                .eq(Article::getDeleted, 0)
                .orderByDesc(Article::getIsPinned)
                .orderByDesc(Article::getPinnedTime)
                .orderByDesc(Article::getCreateTime);

        Page<Article> page = new Page<>(pageNum, pageSize);
        IPage<Article> articlePage = articleMapper.selectPageIgnorePermission(page, queryWrapper);

        List<Article> articles = articlePage.getRecords();
        if (articles.isEmpty()) {
            return PageResult.of(pageNum, pageSize, articlePage.getTotal(), new ArrayList<>());
        }

        List<Long> userIds = articles.stream().map(Article::getUserId).distinct().collect(Collectors.toList());
        Map<Long, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (User u : userMapper.selectBatchIds(userIds)) userMap.put(u.getId(), u);
        }

        List<ArticleVO> result = new ArrayList<>();
        for (Article article : articles) {
            User author = userMap.get(article.getUserId());
            String authorName = author != null ? author.getNickname() : null;
            List<TagVO> tags = getTagsByArticleId(article.getId());

            result.add(ArticleVO.builder()
                    .id(article.getId())
                    .userId(article.getUserId())
                    .authorName(authorName)
                    .authorAvatar(author != null ? author.getAvatar() : null)
                    .categoryId(article.getCategoryId())
                    .title(article.getTitle())
                    .content(article.getContent())
                    .videoUrl(article.getVideoUrl())
                    .summary(article.getSummary())
                    .aiStatus(article.getAiStatus())
                    .status(article.getStatus())
                    .viewCount(article.getViewCount())
                    .allowExport(article.getAllowExport())
                    .isPinned(article.getIsPinned())
                    .pinnedTime(article.getPinnedTime())
                    .tags(tags)
                    .createTime(article.getCreateTime())
                    .updateTime(article.getUpdateTime())
                    .build());
        }

        return PageResult.of(pageNum, pageSize, articlePage.getTotal(), result);
    }

    private List<TagVO> getTagsByArticleId(Long articleId) {
        LambdaQueryWrapper<ArticleTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ArticleTag::getArticleId, articleId);
        List<ArticleTag> articleTags = articleTagMapper.selectList(wrapper);
        if (articleTags.isEmpty()) return new ArrayList<>();
        List<Long> tagIds = articleTags.stream().map(ArticleTag::getTagId).collect(Collectors.toList());
        return tagMapper.selectBatchIds(tagIds).stream()
                .map(t -> TagVO.builder().id(t.getId()).name(t.getName()).createTime(t.getCreateTime()).build())
                .collect(Collectors.toList());
    }
}
