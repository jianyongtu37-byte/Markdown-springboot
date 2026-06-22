package com.nineone.markdown.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nineone.markdown.client.AiServiceClient;
import com.nineone.markdown.entity.Article;
import com.nineone.markdown.entity.ArticleTag;
import com.nineone.markdown.entity.Tag;
import com.nineone.markdown.entity.User;
import com.nineone.markdown.enums.ArticleStatusEnum;
import com.nineone.markdown.mapper.ArticleMapper;
import com.nineone.markdown.mapper.ArticleTagMapper;
import com.nineone.markdown.mapper.TagMapper;
import com.nineone.markdown.mapper.UserMapper;
import com.nineone.markdown.service.RecommendationService;
import com.nineone.markdown.vo.ArticleVO;
import com.nineone.markdown.vo.TagVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 文章推荐服务实现
 * 基于标签相似度的文章推荐
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationServiceImpl implements RecommendationService {

    private final AiServiceClient aiServiceClient;
    private final ArticleMapper articleMapper;
    private final ArticleTagMapper articleTagMapper;
    private final TagMapper tagMapper;
    private final UserMapper userMapper;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<String> generateTags(String content) {
        try {
            Map<String, String> request = new HashMap<>();
            request.put("content", content);
            String tagsJson = aiServiceClient.generateTags(request).getData();
            return parseTagsJson(tagsJson);
        } catch (Exception e) {
            log.warn("AI 标签生成失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<ArticleVO> getRecommendationsByArticleId(Long articleId, int limit) {
        // 查询当前文章的标签
        List<String> tagNames = getTagNamesByArticleId(articleId);
        if (tagNames.isEmpty()) {
            return Collections.emptyList();
        }

        // 根据标签推荐文章，排除当前文章
        List<ArticleVO> recommendations = getRecommendationsByTags(tagNames, limit + 1);
        return recommendations.stream()
                .filter(vo -> !vo.getId().equals(articleId))
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public List<ArticleVO> getRecommendationsByTags(List<String> tagNames, int limit) {
        if (tagNames == null || tagNames.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. 根据标签名查找 Tag 实体
        LambdaQueryWrapper<Tag> tagQuery = new LambdaQueryWrapper<>();
        tagQuery.in(Tag::getName, tagNames);
        List<Tag> tags = tagMapper.selectList(tagQuery);
        if (tags.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> tagIds = tags.stream().map(Tag::getId).collect(Collectors.toList());

        // 2. 查询 article_tag 关联，获取文章ID及其匹配的标签数量
        LambdaQueryWrapper<ArticleTag> atQuery = new LambdaQueryWrapper<>();
        atQuery.in(ArticleTag::getTagId, tagIds);
        List<ArticleTag> articleTags = articleTagMapper.selectList(atQuery);
        if (articleTags.isEmpty()) {
            return Collections.emptyList();
        }

        // 统计每个文章匹配的标签数量，按匹配数降序排序
        Map<Long, Long> articleMatchCount = articleTags.stream()
                .collect(Collectors.groupingBy(ArticleTag::getArticleId, Collectors.counting()));

        List<Long> sortedArticleIds = articleMatchCount.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // 3. 批量查询文章信息
        List<Article> articles = articleMapper.selectBatchIds(sortedArticleIds);
        // 只返回公开状态的文章
        Map<Long, Article> articleMap = articles.stream()
                .filter(a -> a.getStatus() == ArticleStatusEnum.PUBLIC && a.getDeleted() == 0)
                .collect(Collectors.toMap(Article::getId, a -> a));

        // 按匹配数排序保持顺序
        List<Article> sortedArticles = sortedArticleIds.stream()
                .filter(articleMap::containsKey)
                .map(articleMap::get)
                .collect(Collectors.toList());

        if (sortedArticles.isEmpty()) {
            return Collections.emptyList();
        }

        // 4. 批量查询用户信息
        List<Long> userIds = sortedArticles.stream()
                .map(Article::getUserId).distinct().collect(Collectors.toList());
        Map<Long, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(userIds);
            userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u));
        }

        // 5. 批量查询标签信息
        Map<Long, List<TagVO>> articleTagsMap = batchGetTagsByArticleIds(
                sortedArticles.stream().map(Article::getId).collect(Collectors.toList()));

        // 6. 构建 ArticleVO
        Map<Long, User> finalUserMap = userMap;
        return sortedArticles.stream()
                .map(article -> {
                    User user = finalUserMap.get(article.getUserId());
                    List<TagVO> tagVOs = articleTagsMap.getOrDefault(article.getId(), new ArrayList<>());
                    return ArticleVO.builder()
                            .id(article.getId())
                            .userId(article.getUserId())
                            .authorName(user != null ? user.getNickname() : null)
                            .authorAvatar(user != null ? user.getAvatar() : null)
                            .categoryId(article.getCategoryId())
                            .title(article.getTitle())
                            .summary(article.getSummary())
                            .status(article.getStatus())
                            .viewCount(article.getViewCount())
                            .likeCount(article.getLikeCount())
                            .tags(tagVOs)
                            .createTime(article.getCreateTime())
                            .updateTime(article.getUpdateTime())
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取文章的标签名称列表
     */
    private List<String> getTagNamesByArticleId(Long articleId) {
        LambdaQueryWrapper<ArticleTag> atQuery = new LambdaQueryWrapper<>();
        atQuery.eq(ArticleTag::getArticleId, articleId);
        List<ArticleTag> articleTags = articleTagMapper.selectList(atQuery);
        if (articleTags.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> tagIds = articleTags.stream()
                .map(ArticleTag::getTagId).collect(Collectors.toList());
        List<Tag> tags = tagMapper.selectBatchIds(tagIds);
        return tags.stream().map(Tag::getName).collect(Collectors.toList());
    }

    /**
     * 批量查询多篇文章的标签信息
     */
    private Map<Long, List<TagVO>> batchGetTagsByArticleIds(List<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return new HashMap<>();
        }

        LambdaQueryWrapper<ArticleTag> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(ArticleTag::getArticleId, articleIds);
        List<ArticleTag> articleTags = articleTagMapper.selectList(queryWrapper);

        if (articleTags.isEmpty()) {
            return new HashMap<>();
        }

        List<Long> tagIds = articleTags.stream()
                .map(ArticleTag::getTagId).distinct().collect(Collectors.toList());
        Map<Long, Tag> tagMap = new HashMap<>();
        if (!tagIds.isEmpty()) {
            List<Tag> tags = tagMapper.selectBatchIds(tagIds);
            tagMap = tags.stream().collect(Collectors.toMap(Tag::getId, t -> t));
        }

        Map<Long, List<TagVO>> result = new HashMap<>();
        for (ArticleTag at : articleTags) {
            Tag tag = tagMap.get(at.getTagId());
            if (tag != null) {
                result.computeIfAbsent(at.getArticleId(), k -> new ArrayList<>())
                        .add(TagVO.builder()
                                .id(tag.getId())
                                .name(tag.getName())
                                .createTime(tag.getCreateTime())
                                .build());
            }
        }
        return result;
    }

    /**
     * 解析 AI 返回的标签 JSON 数组
     */
    private List<String> parseTagsJson(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            String trimmed = tagsJson.trim();
            // 提取 JSON 数组部分
            int start = trimmed.indexOf('[');
            int end = trimmed.lastIndexOf(']');
            if (start >= 0 && end > start) {
                trimmed = trimmed.substring(start, end + 1);
            }
            List<String> tags = objectMapper.readValue(trimmed, new TypeReference<List<String>>() {});
            return tags.stream()
                    .filter(t -> t != null && !t.isBlank())
                    .map(String::trim)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("解析标签 JSON 失败: {}, 原始内容: {}", e.getMessage(), tagsJson);
            return Collections.emptyList();
        }
    }
}
