package com.nineone.markdown.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Highlight;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nineone.markdown.entity.Article;
import com.nineone.markdown.entity.ArticleTag;
import com.nineone.markdown.entity.Tag;
import com.nineone.markdown.entity.User;
import com.nineone.markdown.entity.es.ArticleDoc;
import com.nineone.markdown.mapper.ArticleMapper;
import com.nineone.markdown.mapper.ArticleTagMapper;
import com.nineone.markdown.mapper.CategoryMapper;
import com.nineone.markdown.mapper.TagMapper;
import com.nineone.markdown.mapper.UserMapper;
import com.nineone.markdown.repository.es.ArticleDocRepository;
import com.nineone.markdown.service.SearchService;
import com.nineone.markdown.vo.SearchResultVO;
import com.nineone.markdown.vo.TagVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 搜索服务实现类
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SearchServiceImpl implements SearchService {

    private final ArticleDocRepository articleDocRepository;
    private final ArticleMapper articleMapper;
    private final UserMapper userMapper;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final ArticleTagMapper articleTagMapper;
    private final ElasticsearchClient elasticsearchClient;

    @Override
    public List<SearchResultVO> searchArticles(String keyword, Integer pageNum, Integer pageSize) {
        Assert.hasText(keyword, "搜索关键词不能为空");
        Assert.isTrue(pageNum != null && pageNum > 0, "页码必须大于0");
        Assert.isTrue(pageSize != null && pageSize > 0 && pageSize <= 100, "每页大小必须在1-100之间");

        try {
            // 构建高亮查询
            Highlight highlight = Highlight.of(h -> h
                    .fields("title", HighlightField.of(hf -> hf
                            .preTags("<em>")
                            .postTags("</em>")))
                    .fields("content", HighlightField.of(hf -> hf
                            .preTags("<em>")
                            .postTags("</em>")
                            .fragmentSize(200)
                            .numberOfFragments(3)))
                    .fields("summary", HighlightField.of(hf -> hf
                            .preTags("<em>")
                            .postTags("</em>")))
                    .fields("tags", HighlightField.of(hf -> hf
                            .preTags("<em>")
                            .postTags("</em>")))
            );

            // 构建查询条件
            Query query = Query.of(q -> q
                    .bool(b -> b
                            .must(m -> m
                                    .multiMatch(mm -> mm
                                            .query(keyword)
                                            .fields("title^3", "content^2", "summary^1.5", "tags^2")
                                            .type(TextQueryType.BestFields)
                                            .fuzziness("AUTO")
                                    )
                            )
                            .filter(f -> f
                                    .term(t -> t
                                            .field("status")
                                            .value(FieldValue.of(1))
                                    )
                            )
                            .filter(f -> f
                                    .term(t -> t
                                            .field("isPublic")
                                            .value(FieldValue.of(1))
                                    )
                            )
                    )
            );

            // 构建搜索请求
            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index("article_index")
                    .query(query)
                    .highlight(highlight)
                    .from((pageNum - 1) * pageSize)
                    .size(pageSize)
                    .sort(so -> so
                            .score(si -> si
                                    .order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)
                            )
                    )
            );

            // 执行搜索
            SearchResponse<ArticleDoc> response = elasticsearchClient.search(searchRequest, ArticleDoc.class);

            // 处理搜索结果
            List<SearchResultVO> results = new ArrayList<>();
            for (Hit<ArticleDoc> hit : response.hits().hits()) {
                ArticleDoc doc = hit.source();
                if (doc == null) continue;

                SearchResultVO result = convertToSearchResultVO(doc, hit);
                results.add(result);
            }

            return results;

        } catch (IOException e) {
            log.error("Elasticsearch搜索失败，关键词: {}", keyword, e);
            throw new RuntimeException("搜索服务暂时不可用，请稍后重试", e);
        }
    }

    @Override
    public Page<SearchResultVO> searchArticlesWithPage(String keyword, Integer pageNum, Integer pageSize) {
        List<SearchResultVO> results = searchArticles(keyword, pageNum, pageSize);
        
        // 获取总记录数（这里简化处理，实际应该从ES获取总数）
        long total = articleDocRepository.count();
        
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize);
        return new PageImpl<>(results, pageable, total);
    }

    @Override
    public List<SearchResultVO> searchByAuthor(String authorName, Integer pageNum, Integer pageSize) {
        Assert.hasText(authorName, "作者名称不能为空");
        
        // 获取所有结果，然后手动分页
        List<ArticleDoc> allDocs = articleDocRepository.findByAuthorName(authorName);
        
        // 手动分页
        int start = (pageNum - 1) * pageSize;
        int end = Math.min(start + pageSize, allDocs.size());
        
        if (start >= allDocs.size()) {
            return Collections.emptyList();
        }
        
        return allDocs.subList(start, end).stream()
                .map(this::convertToSearchResultVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<SearchResultVO> searchByCategory(String categoryName, Integer pageNum, Integer pageSize) {
        Assert.hasText(categoryName, "分类名称不能为空");
        
        // 获取所有结果，然后手动分页
        List<ArticleDoc> allDocs = articleDocRepository.findByCategoryName(categoryName);
        
        // 手动分页
        int start = (pageNum - 1) * pageSize;
        int end = Math.min(start + pageSize, allDocs.size());
        
        if (start >= allDocs.size()) {
            return Collections.emptyList();
        }
        
        return allDocs.subList(start, end).stream()
                .map(this::convertToSearchResultVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<SearchResultVO> searchByTag(String tag, Integer pageNum, Integer pageSize) {
        Assert.hasText(tag, "标签不能为空");
        
        // 获取所有结果，然后手动分页
        List<ArticleDoc> allDocs = articleDocRepository.findByTagsContaining(tag);
        
        // 手动分页
        int start = (pageNum - 1) * pageSize;
        int end = Math.min(start + pageSize, allDocs.size());
        
        if (start >= allDocs.size()) {
            return Collections.emptyList();
        }
        
        return allDocs.subList(start, end).stream()
                .map(this::convertToSearchResultVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public boolean indexArticle(Long articleId) {
        try {
            Article article = articleMapper.selectByIdIgnorePermission(articleId);
            if (article == null) {
                log.warn("文章不存在，无法索引，文章ID: {}", articleId);
                return false;
            }

            // 只索引已发布且公开的文章
            if (article.getStatus().getCode() != 2 || !article.getStatus().isPublic()) {
                log.debug("文章未发布或非公开，跳过索引，文章ID: {}", articleId);
                return false;
            }

            // 获取作者信息
            User user = userMapper.selectById(article.getUserId());
            String authorName = user != null ? user.getNickname() : "未知作者";

            // 获取分类信息
            String categoryName = null;
            if (article.getCategoryId() != null) {
                com.nineone.markdown.entity.Category category = categoryMapper.selectById(article.getCategoryId());
                categoryName = category != null ? category.getName() : null;
            }

            // 获取标签信息
            List<Tag> tags = getTagsByArticleId(articleId);
            String tagNames = tags.stream()
                    .map(Tag::getName)
                    .collect(Collectors.joining(","));

            // 构建ES文档
            ArticleDoc articleDoc = ArticleDoc.fromArticle(article, authorName, categoryName, tagNames);

            // 保存到ES
            articleDocRepository.save(articleDoc);
            
            log.info("文章索引成功，文章ID: {}", articleId);
            return true;

        } catch (Exception e) {
            log.error("文章索引失败，文章ID: {}", articleId, e);
            return false;
        }
    }

    @Override
    @Transactional
    public int batchIndexArticles(List<Long> articleIds) {
        int successCount = 0;
        for (Long articleId : articleIds) {
            if (indexArticle(articleId)) {
                successCount++;
            }
        }
        log.info("批量索引完成，总数: {}, 成功: {}", articleIds.size(), successCount);
        return successCount;
    }

    @Override
    public boolean deleteArticleIndex(Long articleId) {
        try {
            articleDocRepository.deleteById(articleId);
            log.info("文章索引删除成功，文章ID: {}", articleId);
            return true;
        } catch (Exception e) {
            log.error("文章索引删除失败，文章ID: {}", articleId, e);
            return false;
        }
    }

    @Override
    public int batchDeleteArticleIndexes(List<Long> articleIds) {
        int successCount = 0;
        for (Long articleId : articleIds) {
            if (deleteArticleIndex(articleId)) {
                successCount++;
            }
        }
        log.info("批量删除索引完成，总数: {}, 成功: {}", articleIds.size(), successCount);
        return successCount;
    }

    @Override
    @Transactional
    public int rebuildAllIndexes() {
        log.info("开始重建所有文章索引...");
        
        // 获取所有已发布且公开的文章（忽略数据权限拦截器）
        List<Article> articles = articleMapper.selectList(null);
        articles = articles.stream()
                .filter(article -> article.getStatus().getCode() == 2 && article.getStatus().isPublic())
                .collect(Collectors.toList());
        
        log.info("找到 {} 篇需要索引的文章", articles.size());
        
        int successCount = 0;
        for (Article article : articles) {
            if (indexArticle(article.getId())) {
                successCount++;
            }
            
            // 每处理100条记录打印一次进度
            if (successCount % 100 == 0) {
                log.info("已索引 {} 篇文章...", successCount);
            }
        }
        
        log.info("索引重建完成，成功索引 {} 篇文章", successCount);
        return successCount;
    }

    @Override
    public long getIndexStats() {
        return articleDocRepository.count();
    }

    @Override
    public List<String> getSearchSuggestions(String prefix, int limit) {
        if (!StringUtils.hasText(prefix) || prefix.length() < 2) {
            return Collections.emptyList();
        }

        try {
            // 构建建议查询
            Query query = Query.of(q -> q
                    .bool(b -> b
                            .should(s -> s
                                    .prefix(p -> p
                                            .field("title")
                                            .value(prefix)
                                    )
                            )
                            .should(s -> s
                                    .prefix(p -> p
                                            .field("content")
                                            .value(prefix)
                                    )
                            )
                            .minimumShouldMatch("1")
                    )
            );

            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index("article_index")
                    .query(query)
                    .size(limit)
                    .source(sc -> sc
                            .filter(f -> f
                                    .includes("title", "content")
                            )
                    )
            );

            SearchResponse<ArticleDoc> response = elasticsearchClient.search(searchRequest, ArticleDoc.class);
            
            Set<String> suggestions = new LinkedHashSet<>();
            for (Hit<ArticleDoc> hit : response.hits().hits()) {
                ArticleDoc doc = hit.source();
                if (doc == null) continue;

                // 从标题中提取建议
                if (doc.getTitle() != null && doc.getTitle().toLowerCase().contains(prefix.toLowerCase())) {
                    suggestions.add(doc.getTitle());
                }
                
                // 限制返回数量
                if (suggestions.size() >= limit) {
                    break;
                }
            }

            return new ArrayList<>(suggestions);

        } catch (IOException e) {
            log.error("获取搜索建议失败，前缀: {}", prefix, e);
            return Collections.emptyList();
        }
    }

    /**
     * 将ArticleDoc转换为SearchResultVO（不带高亮）
     */
    private SearchResultVO convertToSearchResultVO(ArticleDoc doc) {
        return SearchResultVO.builder()
                .id(doc.getId())
                .userId(doc.getUserId())
                .authorName(doc.getAuthorName())
                .categoryId(doc.getCategoryId())
                .categoryName(doc.getCategoryName())
                .title(doc.getTitle())
                .contentSnippet(extractContentSnippet(doc.getContent(), 200))
                .summary(doc.getSummary())
                .tagNames(doc.getTags())
                .viewCount(doc.getViewCount())
                .createTime(doc.getCreateTime())
                .updateTime(doc.getUpdateTime())
                .hasHighlight(false)
                .build();
    }

    /**
     * 将ArticleDoc和Hit转换为SearchResultVO（带高亮）
     */
    private SearchResultVO convertToSearchResultVO(ArticleDoc doc, Hit<ArticleDoc> hit) {
        SearchResultVO result = convertToSearchResultVO(doc);
        result.setScore(hit.score() != null ? hit.score().floatValue() : 0.0f);
        
        // 处理高亮
        Map<String, List<String>> highlightFields = hit.highlight();
        boolean hasHighlight = false;
        
        if (highlightFields != null) {
            // 处理标题高亮
            if (highlightFields.containsKey("title")) {
                List<String> titleHighlights = highlightFields.get("title");
                if (!titleHighlights.isEmpty()) {
                    result.setHighlightedTitle(titleHighlights.get(0));
                    hasHighlight = true;
                }
            }
            
            // 处理内容高亮
            if (highlightFields.containsKey("content")) {
                List<String> contentHighlights = highlightFields.get("content");
                if (!contentHighlights.isEmpty()) {
                    // 取第一个高亮片段作为内容摘要
                    result.setHighlightedContent(contentHighlights.get(0));
                    result.setContentSnippet(contentHighlights.get(0));
                    hasHighlight = true;
                }
            }
            
            // 处理摘要高亮
            if (highlightFields.containsKey("summary")) {
                List<String> summaryHighlights = highlightFields.get("summary");
                if (!summaryHighlights.isEmpty()) {
                    result.setSummary(summaryHighlights.get(0));
                    hasHighlight = true;
                }
            }
        }
        
        result.setHasHighlight(hasHighlight);
        return result;
    }

    /**
     * 提取内容摘要
     */
    private String extractContentSnippet(String content, int maxLength) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        
        // 移除Markdown标记
        String plainText = content
                .replaceAll("#+\\s*", "")  // 移除标题标记
                .replaceAll("\\*\\*|__", "")  // 移除加粗标记
                .replaceAll("\\*|_", "")  // 移除斜体标记
                .replaceAll("`", "")  // 移除代码标记
                .replaceAll("\\[.*?\\]\\(.*?\\)", "")  // 移除链接
                .replaceAll("!\\[.*?\\]\\(.*?\\)", "")  // 移除图片
                .replaceAll("\\s+", " ")  // 合并多个空格
                .trim();
        
        if (plainText.length() <= maxLength) {
            return plainText;
        }
        
        // 尝试在句子边界处截断
        int lastPeriod = plainText.lastIndexOf('.', maxLength);
        int lastExclamation = plainText.lastIndexOf('!', maxLength);
        int lastQuestion = plainText.lastIndexOf('?', maxLength);
        
        int cutPoint = Math.max(Math.max(lastPeriod, lastExclamation), lastQuestion);
        if (cutPoint > maxLength / 2) {
            return plainText.substring(0, cutPoint + 1);
        }
        
        // 如果没有合适的句子边界，就在单词边界处截断
        int lastSpace = plainText.lastIndexOf(' ', maxLength);
        if (lastSpace > maxLength / 2) {
            return plainText.substring(0, lastSpace) + "...";
        }
        
        return plainText.substring(0, maxLength) + "...";
    }

    /**
     * 异步索引文章
     */
    @Async("aiTaskExecutor")
    public void indexArticleAsync(Long articleId) {
        try {
            indexArticle(articleId);
        } catch (Exception e) {
            log.error("异步索引文章失败，文章ID: {}", articleId, e);
        }
    }

    /**
     * 异步删除文章索引
     */
    @Async("aiTaskExecutor")
    public void deleteArticleIndexAsync(Long articleId) {
        try {
            deleteArticleIndex(articleId);
        } catch (Exception e) {
            log.error("异步删除文章索引失败，文章ID: {}", articleId, e);
        }
    }

    /**
     * 根据文章ID获取标签列表
     */
    private List<Tag> getTagsByArticleId(Long articleId) {
        try {
            // 查询文章-标签关联关系
            List<ArticleTag> articleTags = articleTagMapper.selectList(
                new LambdaQueryWrapper<ArticleTag>()
                    .eq(ArticleTag::getArticleId, articleId)
            );
            
            if (articleTags.isEmpty()) {
                return Collections.emptyList();
            }
            
            // 获取标签ID列表
            List<Long> tagIds = articleTags.stream()
                    .map(ArticleTag::getTagId)
                    .collect(Collectors.toList());
            
            // 查询标签信息
            return tagMapper.selectList(
                new LambdaQueryWrapper<Tag>()
                    .in(Tag::getId, tagIds)
            );
            
        } catch (Exception e) {
            log.error("获取文章标签失败，文章ID: {}", articleId, e);
            return Collections.emptyList();
        }
    }
}