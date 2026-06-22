package com.nineone.markdown.service;

import com.nineone.common.result.PageResult;
import com.nineone.markdown.vo.ArticleSeriesVO;
import com.nineone.markdown.vo.SeriesSelectableArticleVO;

import java.util.List;

public interface ArticleSeriesService {
    ArticleSeriesVO createSeries(Long userId, String title, String description, Boolean isPublic, List<Long> articleIds);
    ArticleSeriesVO updateSeries(Long seriesId, Long userId, String title, String description, Boolean isPublic);
    void deleteSeries(Long seriesId, Long userId);
    ArticleSeriesVO getSeriesDetail(Long seriesId);
    PageResult<ArticleSeriesVO> getMySeries(Long userId, Integer pageNum, Integer pageSize);
    PageResult<ArticleSeriesVO> getUserPublicSeries(Long userId, Integer pageNum, Integer pageSize);
    void addArticleToSeries(Long seriesId, Long userId, Long articleId, Integer sortOrder);
    void removeArticleFromSeries(Long seriesId, Long userId, Long articleId);
    void updateArticleSort(Long seriesId, Long userId, List<Long> articleIds);
    PageResult<SeriesSelectableArticleVO> getSelectableArticles(Long seriesId, Long userId, String keyword, Integer pageNum, Integer pageSize);
}
