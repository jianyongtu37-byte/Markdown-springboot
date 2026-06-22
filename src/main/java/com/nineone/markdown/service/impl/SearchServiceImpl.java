package com.nineone.markdown.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.ScrollRequest;
import co.elastic.clients.elasticsearch.core.ScrollResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.Highlight;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.nineone.markdown.entity.Article;
import com.nineone.markdown.entity.ArticleTag;
import com.nineone.markdown.entity.Category;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * 搜索服务实现类（Elasticsearch 版本）
 * 仅在 spring.data.elasticsearch.repositories.enabled=true 时加载
 */
@Service
@ConditionalOnProperty(name = "spring.data.elasticsearch.repositories.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class SearchServiceImpl implements SearchService {

    private static final String INDEX_NAME = "article_index";

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
            SearchResponse<ArticleDoc> response = executeSearch(keyword, pageNum, pageSize);
            return extractResults(response);
        } catch (IOException e) {
            log.error("Elasticsearch搜索失败，关键词: {}", keyword, e);
            throw new RuntimeException("搜索服务暂时不可用，请稍后重试", e);
        }
    }

    @Override
    public Page<SearchResultVO> searchArticlesWithPage(String keyword, Integer pageNum, Integer pageSize) {
        Assert.hasText(keyword, "搜索关键词不能为空");
        Assert.isTrue(pageNum != null && pageNum > 0, "页码必须大于0");
        Assert.isTrue(pageSize != null && pageSize > 0 && pageSize <= 100, "每页大小必须在1-100之间");

        try {
            SearchResponse<ArticleDoc> response = executeSearch(keyword, pageNum, pageSize);
            List<SearchResultVO> results = extractResults(response);

            // 从 ES 响应中获取真实的匹配总数
            long total = response.hits().total() != null ? response.hits().total().value() : 0;

            Pageable pageable = PageRequest.of(pageNum - 1, pageSize);
            return new PageImpl<>(results, pageable, total);
        } catch (IOException e) {
            log.error("Elasticsearch搜索失败，关键词: {}", keyword, e);
            throw new RuntimeException("搜索服务暂时不可用，请稍后重试", e);
        }
    }

    /**
     * 执行 ES 搜索，返回原始响应（供 searchArticles 和 searchArticlesWithPage 共用）
     */
    private SearchResponse<ArticleDoc> executeSearch(String keyword, Integer pageNum, Integer pageSize) throws IOException {
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

        SearchRequest searchRequest = SearchRequest.of(s -> s
                .index(INDEX_NAME)
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

        return elasticsearchClient.search(searchRequest, ArticleDoc.class);
    }

    /**
     * 从 ES 搜索响应中提取结果列表
     */
    private List<SearchResultVO> extractResults(SearchResponse<ArticleDoc> response) {
        List<SearchResultVO> results = new ArrayList<>();
        for (Hit<ArticleDoc> hit : response.hits().hits()) {
            ArticleDoc doc = hit.source();
            if (doc == null) continue;
            SearchResultVO result = convertToSearchResultVO(doc, hit);
            results.add(result);
        }
        return results;
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
        if (articleIds == null || articleIds.isEmpty()) {
            return 0;
        }

        try {
            int totalSuccess = 0;
            int batchSize = 200;

            // 分批处理，避免 SQL IN 子句和 ES Bulk 请求过大
            for (int i = 0; i < articleIds.size(); i += batchSize) {
                List<Long> batchIds = articleIds.subList(i, Math.min(i + batchSize, articleIds.size()));

                // 批量查询文章
                List<Article> articles = articleMapper.selectBatchIds(batchIds);
                if (articles.isEmpty()) {
                    continue;
                }

                // 过滤：只索引已发布且公开的文章
                articles = articles.stream()
                        .filter(a -> a.getStatus().getCode() == 2 && a.getStatus().isPublic())
                        .collect(Collectors.toList());

                if (articles.isEmpty()) {
                    continue;
                }

                // 批量构建 ArticleDoc 并写入 ES
                int batchSuccess = bulkIndexToES(articles);
                totalSuccess += batchSuccess;
                log.debug("批量索引进度: 已处理 {}/{}, 本批成功 {}", Math.min(i + batchSize, articleIds.size()), articleIds.size(), batchSuccess);
            }

            log.info("批量索引完成，总数: {}, 成功: {}", articleIds.size(), totalSuccess);
            return totalSuccess;
        } catch (Exception e) {
            log.error("批量索引失败", e);
            return 0;
        }
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

        int totalSuccess = 0;
        int pageNum = 1;
        int batchSize = 100;

        // 分页查询已发布且公开的文章，避免一次性加载全量数据
        while (true) {
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<Article> page =
                    new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, batchSize);
            LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Article::getStatus, com.nineone.markdown.enums.ArticleStatusEnum.PUBLIC);
            IPage<Article> articlePage = articleMapper.selectPageIgnorePermission(page, wrapper);

            List<Article> articles = articlePage.getRecords();
            if (articles.isEmpty()) {
                break;
            }

            // 批量写入 ES
            try {
                int batchSuccess = bulkIndexToES(articles);
                totalSuccess += batchSuccess;
                log.info("已索引 {} 篇文章（本批 {}/{}）...", totalSuccess, batchSuccess, articles.size());
            } catch (IOException e) {
                log.error("本批索引失败，跳过 {} 篇文章", articles.size(), e);
            }

            // 如果已经是最后一页，退出
            if (articles.size() < batchSize) {
                break;
            }
            pageNum++;
        }

        log.info("索引重建完成，成功索引 {} 篇文章", totalSuccess);
        return totalSuccess;
    }

    /**
     * 批量构建 ArticleDoc 并通过 Bulk API 写入 ES
     * 批量查询用户/分类/标签信息，避免 N+1 查询
     */
    private int bulkIndexToES(List<Article> articles) throws IOException {
        // 批量查询用户信息
        List<Long> userIds = articles.stream()
                .map(Article::getUserId).distinct().collect(Collectors.toList());
        Map<Long, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            userMapper.selectBatchIds(userIds).forEach(u -> userMap.put(u.getId(), u));
        }

        // 批量查询分类信息
        List<Long> categoryIds = articles.stream()
                .map(Article::getCategoryId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, Category> categoryMap = new HashMap<>();
        if (!categoryIds.isEmpty()) {
            categoryMapper.selectBatchIdsIgnorePermission(categoryIds).forEach(c -> categoryMap.put(c.getId(), c));
        }

        // 批量查询标签关联
        List<Long> articleIds = articles.stream().map(Article::getId).collect(Collectors.toList());
        Map<Long, String> articleTagsMap = new HashMap<>();
        if (!articleIds.isEmpty()) {
            LambdaQueryWrapper<ArticleTag> tagWrapper = new LambdaQueryWrapper<>();
            tagWrapper.in(ArticleTag::getArticleId, articleIds);
            List<ArticleTag> allTags = articleTagMapper.selectList(tagWrapper);

            if (!allTags.isEmpty()) {
                List<Long> tagIds = allTags.stream().map(ArticleTag::getTagId).distinct().collect(Collectors.toList());
                Map<Long, Tag> tagMap = new HashMap<>();
                tagMapper.selectBatchIds(tagIds).forEach(t -> tagMap.put(t.getId(), t));

                // 按 articleId 分组拼接标签名
                Map<Long, List<String>> grouped = new HashMap<>();
                for (ArticleTag at : allTags) {
                    Tag tag = tagMap.get(at.getTagId());
                    if (tag != null) {
                        grouped.computeIfAbsent(at.getArticleId(), k -> new ArrayList<>()).add(tag.getName());
                    }
                }
                grouped.forEach((aid, names) -> articleTagsMap.put(aid, String.join(",", names)));
            }
        }

        // 构建 ArticleDoc 列表
        List<ArticleDoc> docs = new ArrayList<>();
        for (Article article : articles) {
            String authorName = Optional.ofNullable(userMap.get(article.getUserId()))
                    .map(User::getNickname).orElse("未知作者");
            String categoryName = Optional.ofNullable(article.getCategoryId())
                    .map(categoryMap::get).map(Category::getName).orElse(null);
            String tags = articleTagsMap.getOrDefault(article.getId(), "");
            docs.add(ArticleDoc.fromArticle(article, authorName, categoryName, tags));
        }

        // 使用 Bulk API 批量写入
        BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
        for (ArticleDoc doc : docs) {
            bulkBuilder.operations(op -> op
                    .index(idx -> idx
                            .index(INDEX_NAME)
                            .id(String.valueOf(doc.getId()))
                            .document(doc)
                    )
            );
        }

        BulkResponse bulkResponse = elasticsearchClient.bulk(bulkBuilder.build());

        // 统计成功数
        int successCount = 0;
        if (bulkResponse.errors()) {
            for (BulkResponseItem item : bulkResponse.items()) {
                if (item.error() != null) {
                    log.warn("ES 批量索引单条失败: id={}, error={}", item.id(), item.error().reason());
                } else {
                    successCount++;
                }
            }
        } else {
            successCount = docs.size();
        }

        return successCount;
    }

    @Override
    public long getIndexStats() {
        return articleDocRepository.count();
    }

    @Override
    public List<Long> getAllIndexedArticleIds() {
        List<Long> ids = new ArrayList<>();
        try {
            SearchRequest initialRequest = SearchRequest.of(s -> s
                    .index(INDEX_NAME)
                    .size(1000)
                    .source(sc -> sc.filter(f -> f.includes(Collections.emptyList())))
            );
            SearchResponse<Void> response = elasticsearchClient.search(initialRequest, Void.class);

            while (true) {
                List<Hit<Void>> hits = response.hits().hits();
                if (hits.isEmpty()) {
                    break;
                }
                for (Hit<Void> hit : hits) {
                    if (hit.id() != null) {
                        try {
                            ids.add(Long.parseLong(hit.id()));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
                String scrollId = response.scrollId();
                if (scrollId == null || hits.size() < 1000) {
                    break;
                }
                ScrollRequest scrollRequest = ScrollRequest.of(s -> s
                        .scrollId(scrollId).scroll(t -> t.time("60s")));
                ScrollResponse<Void> scrollResponse = elasticsearchClient.scroll(scrollRequest, Void.class);
                response = scrollResponseToSearchResponse(scrollResponse);
            }

            String finalScrollId = response.scrollId();
            if (finalScrollId != null) {
                elasticsearchClient.clearScroll(c -> c.scrollId(finalScrollId));
            }
        } catch (IOException e) {
            log.error("获取 ES 索引文章 ID 列表失败", e);
        }
        return ids;
    }

    /**
     * 将 ScrollResponse 转为 SearchResponse（保持代码结构一致）
     */
    private SearchResponse<Void> scrollResponseToSearchResponse(ScrollResponse<Void> scrollResponse) {
        return SearchResponse.of(sr -> sr
                .hits(scrollResponse.hits())
                .scrollId(scrollResponse.scrollId()));
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
                    .index(INDEX_NAME)
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
    @Async("esExecutor")
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
    @Async("esExecutor")
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