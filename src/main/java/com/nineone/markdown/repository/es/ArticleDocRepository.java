package com.nineone.markdown.repository.es;

import com.nineone.markdown.entity.es.ArticleDoc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Elasticsearch 文章文档仓库接口
 */
@Repository
public interface ArticleDocRepository extends ElasticsearchRepository<ArticleDoc, Long> {

    /**
     * 根据标题或内容搜索文章（简单搜索）
     */
    Page<ArticleDoc> findByTitleContainingOrContentContaining(String title, String content, Pageable pageable);

    /**
     * 根据作者名称搜索文章
     */
    List<ArticleDoc> findByAuthorName(String authorName);

    /**
     * 根据分类名称搜索文章
     */
    List<ArticleDoc> findByCategoryName(String categoryName);

    /**
     * 根据标签搜索文章
     */
    List<ArticleDoc> findByTagsContaining(String tag);

    /**
     * 根据状态搜索文章
     */
    List<ArticleDoc> findByStatus(Integer status);

    /**
     * 根据公开状态搜索文章
     */
    List<ArticleDoc> findByIsPublic(Integer isPublic);

    /**
     * 使用自定义查询进行全文搜索
     * @param keyword 搜索关键词
     * @param pageable 分页信息
     * @return 搜索结果
     */
    @Query("""
        {
          "bool": {
            "must": [
              {
                "multi_match": {
                  "query": "?0",
                  "fields": ["title^3", "content^2", "summary^1.5", "tags^2"],
                  "type": "best_fields",
                  "fuzziness": "AUTO"
                }
              }
            ],
            "filter": [
              {
                "term": {
                  "status": 1
                }
              },
              {
                "term": {
                  "isPublic": 1
                }
              }
            ]
          }
        }
        """)
    Page<ArticleDoc> searchByKeyword(String keyword, Pageable pageable);

    /**
     * 根据用户ID搜索文章
     */
    List<ArticleDoc> findByUserId(Long userId);

    /**
     * 根据分类ID搜索文章
     */
    List<ArticleDoc> findByCategoryId(Long categoryId);

    /**
     * 删除指定用户的所有文章
     */
    void deleteByUserId(Long userId);

    /**
     * 删除指定分类的所有文章
     */
    void deleteByCategoryId(Long categoryId);
}