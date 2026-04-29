package com.nineone.markdown.service;

import com.nineone.markdown.entity.es.ArticleDoc;
import com.nineone.markdown.vo.SearchResultVO;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 搜索服务接口
 * 提供 Elasticsearch 全文检索功能
 */
public interface SearchService {

    /**
     * 全文搜索文章（带高亮）
     * @param keyword 搜索关键词
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 搜索结果列表（带高亮）
     */
    List<SearchResultVO> searchArticles(String keyword, Integer pageNum, Integer pageSize);

    /**
     * 全文搜索文章（带分页和高亮）
     * @param keyword 搜索关键词
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 分页搜索结果
     */
    Page<SearchResultVO> searchArticlesWithPage(String keyword, Integer pageNum, Integer pageSize);

    /**
     * 根据作者搜索文章
     * @param authorName 作者名称
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 搜索结果列表
     */
    List<SearchResultVO> searchByAuthor(String authorName, Integer pageNum, Integer pageSize);

    /**
     * 根据分类搜索文章
     * @param categoryName 分类名称
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 搜索结果列表
     */
    List<SearchResultVO> searchByCategory(String categoryName, Integer pageNum, Integer pageSize);

    /**
     * 根据标签搜索文章
     * @param tag 标签名称
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 搜索结果列表
     */
    List<SearchResultVO> searchByTag(String tag, Integer pageNum, Integer pageSize);

    /**
     * 索引单篇文章到 Elasticsearch
     * @param articleId 文章ID
     * @return 是否索引成功
     */
    boolean indexArticle(Long articleId);

    /**
     * 批量索引文章到 Elasticsearch
     * @param articleIds 文章ID列表
     * @return 成功索引的数量
     */
    int batchIndexArticles(List<Long> articleIds);

    /**
     * 从 Elasticsearch 删除文章索引
     * @param articleId 文章ID
     * @return 是否删除成功
     */
    boolean deleteArticleIndex(Long articleId);

    /**
     * 批量从 Elasticsearch 删除文章索引
     * @param articleIds 文章ID列表
     * @return 成功删除的数量
     */
    int batchDeleteArticleIndexes(List<Long> articleIds);

    /**
     * 重建所有文章索引
     * @return 成功索引的数量
     */
    int rebuildAllIndexes();

    /**
     * 获取索引统计信息
     * @return 索引文档数量
     */
    long getIndexStats();

    /**
     * 搜索建议（自动补全）
     * @param prefix 前缀
     * @param limit 返回数量限制
     * @return 建议列表
     */
    List<String> getSearchSuggestions(String prefix, int limit);
}