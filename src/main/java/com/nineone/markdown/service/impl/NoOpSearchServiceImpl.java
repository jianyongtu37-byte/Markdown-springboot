package com.nineone.markdown.service.impl;

import com.nineone.markdown.service.SearchService;
import com.nineone.markdown.vo.SearchResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 搜索服务空实现（Elasticsearch 不可用时的降级方案）
 * 仅在 spring.data.elasticsearch.repositories.enabled=false 时加载
 */
@Service
@ConditionalOnProperty(name = "spring.data.elasticsearch.repositories.enabled", havingValue = "false")
@Slf4j
public class NoOpSearchServiceImpl implements SearchService {

    @Override
    public List<SearchResultVO> searchArticles(String keyword, Integer pageNum, Integer pageSize) {
        log.warn("Elasticsearch 未启用，搜索功能不可用");
        return Collections.emptyList();
    }

    @Override
    public Page<SearchResultVO> searchArticlesWithPage(String keyword, Integer pageNum, Integer pageSize) {
        log.warn("Elasticsearch 未启用，搜索功能不可用");
        return new PageImpl<>(Collections.emptyList(), PageRequest.of(pageNum - 1, pageSize), 0);
    }

    @Override
    public List<SearchResultVO> searchByAuthor(String authorName, Integer pageNum, Integer pageSize) {
        log.warn("Elasticsearch 未启用，搜索功能不可用");
        return Collections.emptyList();
    }

    @Override
    public List<SearchResultVO> searchByCategory(String categoryName, Integer pageNum, Integer pageSize) {
        log.warn("Elasticsearch 未启用，搜索功能不可用");
        return Collections.emptyList();
    }

    @Override
    public List<SearchResultVO> searchByTag(String tag, Integer pageNum, Integer pageSize) {
        log.warn("Elasticsearch 未启用，搜索功能不可用");
        return Collections.emptyList();
    }

    @Override
    public boolean indexArticle(Long articleId) {
        log.debug("Elasticsearch 未启用，跳过索引操作");
        return false;
    }

    @Override
    public int batchIndexArticles(List<Long> articleIds) {
        log.debug("Elasticsearch 未启用，跳过批量索引操作");
        return 0;
    }

    @Override
    public boolean deleteArticleIndex(Long articleId) {
        log.debug("Elasticsearch 未启用，跳过删除索引操作");
        return false;
    }

    @Override
    public int batchDeleteArticleIndexes(List<Long> articleIds) {
        log.debug("Elasticsearch 未启用，跳过批量删除索引操作");
        return 0;
    }

    @Override
    public int rebuildAllIndexes() {
        log.warn("Elasticsearch 未启用，无法重建索引");
        return 0;
    }

    @Override
    public long getIndexStats() {
        return 0;
    }

    @Override
    public List<String> getSearchSuggestions(String prefix, int limit) {
        return Collections.emptyList();
    }

    @Override
    public List<Long> getAllIndexedArticleIds() {
        return Collections.emptyList();
    }
}
