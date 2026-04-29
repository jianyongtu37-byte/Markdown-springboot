package com.nineone.markdown.repository.es;

import com.nineone.markdown.entity.es.ArticleDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

/**
 * Elasticsearch 文章搜索仓库接口
 */
public interface ArticleSearchRepository extends ElasticsearchRepository<ArticleDocument, Long> {

    /**
     * 根据标题或内容搜索文章
     * @param title 标题关键词
     * @param content 内容关键词
     * @param pageable 分页参数
     * @return 搜索结果分页
     */
    Page<ArticleDocument> findByTitleContainingOrContentContaining(String title, String content, Pageable pageable);

    /**
     * 根据用户ID搜索文章
     * @param userId 用户ID
     * @param pageable 分页参数
     * @return 用户文章分页
     */
    Page<ArticleDocument> findByUserId(Long userId, Pageable pageable);

    /**
     * 根据分类ID搜索文章
     * @param categoryId 分类ID
     * @param pageable 分页参数
     * @return 分类文章分页
     */
    Page<ArticleDocument> findByCategoryId(Long categoryId, Pageable pageable);

    /**
     * 根据标签搜索文章
     * @param tag 标签名称
     * @param pageable 分页参数
     * @return 标签文章分页
     */
    Page<ArticleDocument> findByTagsContaining(String tag, Pageable pageable);

    /**
     * 使用自定义查询进行全文搜索
     * @param keyword 搜索关键词
     * @param pageable 分页参数
     * @return 搜索结果分页
     */
    @Query("{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"title^2\", \"content\", \"summary\"]}}")
    Page<ArticleDocument> fullTextSearch(String keyword, Pageable pageable);

    /**
     * 搜索公开文章
     * @param isPublic 是否公开
     * @param pageable 分页参数
     * @return 公开文章分页
     */
    Page<ArticleDocument> findByIsPublic(Integer isPublic, Pageable pageable);
}