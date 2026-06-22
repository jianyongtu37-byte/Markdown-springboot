package com.nineone.markdown.service;

import com.nineone.markdown.vo.ArticleVO;

import java.util.List;

/**
 * 文章推荐服务接口
 * 基于标签相似度的文章推荐
 */
public interface RecommendationService {

    /**
     * 使用 AI 从文章内容中生成标签
     * @param content 文章内容
     * @return 标签名称列表
     */
    List<String> generateTags(String content);

    /**
     * 根据文章ID推荐相关文章
     * @param articleId 当前文章ID
     * @param limit 推荐数量
     * @return 推荐文章列表
     */
    List<ArticleVO> getRecommendationsByArticleId(Long articleId, int limit);

    /**
     * 根据标签列表推荐相关文章
     * @param tagNames 标签名称列表
     * @param limit 推荐数量
     * @return 推荐文章列表
     */
    List<ArticleVO> getRecommendationsByTags(List<String> tagNames, int limit);
}
