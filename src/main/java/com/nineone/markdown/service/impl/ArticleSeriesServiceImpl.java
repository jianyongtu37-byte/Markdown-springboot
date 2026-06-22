package com.nineone.markdown.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nineone.common.result.PageResult;
import com.nineone.markdown.entity.Article;
import com.nineone.markdown.entity.ArticleSeries;
import com.nineone.markdown.entity.ArticleSeriesItem;
import com.nineone.markdown.entity.User;
import com.nineone.markdown.exception.BizException;
import com.nineone.markdown.exception.PermissionDeniedException;
import com.nineone.markdown.mapper.ArticleMapper;
import com.nineone.markdown.mapper.ArticleSeriesItemMapper;
import com.nineone.markdown.mapper.ArticleSeriesMapper;
import com.nineone.markdown.mapper.UserMapper;
import com.nineone.markdown.service.ArticleSeriesService;
import com.nineone.markdown.vo.ArticleSeriesVO;
import com.nineone.markdown.vo.SeriesSelectableArticleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArticleSeriesServiceImpl implements ArticleSeriesService {

    private final ArticleSeriesMapper seriesMapper;
    private final ArticleSeriesItemMapper itemMapper;
    private final ArticleMapper articleMapper;
    private final UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ArticleSeriesVO createSeries(Long userId, String title, String description, Boolean isPublic, List<Long> articleIds) {
        Assert.hasText(title, "系列标题不能为空");
        Assert.notNull(userId, "用户ID不能为空");

        ArticleSeries series = ArticleSeries.builder()
                .userId(userId)
                .title(title)
                .description(description)
                .isPublic(isPublic != null && isPublic ? 1 : 0)
                .articleCount(articleIds != null ? articleIds.size() : 0)
                .sortOrder(0)
                .build();
        seriesMapper.insert(series);

        if (articleIds != null && !articleIds.isEmpty()) {
            for (int i = 0; i < articleIds.size(); i++) {
                ArticleSeriesItem item = ArticleSeriesItem.builder()
                        .seriesId(series.getId())
                        .articleId(articleIds.get(i))
                        .sortOrder(i + 1)
                        .build();
                itemMapper.insert(item);
            }
        }

        return buildVO(series, articleIds != null ? articleIds : List.of());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ArticleSeriesVO updateSeries(Long seriesId, Long userId, String title, String description, Boolean isPublic) {
        ArticleSeries series = seriesMapper.selectById(seriesId);
        if (series == null) {
            throw new BizException("系列不存在");
        }
        if (!series.getUserId().equals(userId)) {
            throw new PermissionDeniedException("无权修改此系列");
        }

        if (title != null) series.setTitle(title);
        if (description != null) series.setDescription(description);
        if (isPublic != null) series.setIsPublic(isPublic ? 1 : 0);
        seriesMapper.updateById(series);

        List<Long> articleIds = getArticleIds(seriesId);
        return buildVO(series, articleIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSeries(Long seriesId, Long userId) {
        ArticleSeries series = seriesMapper.selectById(seriesId);
        if (series == null) {
            throw new BizException("系列不存在");
        }
        if (!series.getUserId().equals(userId)) {
            throw new PermissionDeniedException("无权删除此系列");
        }
        // 删除系列下的所有文章关联
        LambdaQueryWrapper<ArticleSeriesItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ArticleSeriesItem::getSeriesId, seriesId);
        itemMapper.delete(wrapper);
        seriesMapper.deleteById(seriesId);
    }

    @Override
    public ArticleSeriesVO getSeriesDetail(Long seriesId) {
        ArticleSeries series = seriesMapper.selectById(seriesId);
        if (series == null) {
            throw new BizException("系列不存在");
        }

        LambdaQueryWrapper<ArticleSeriesItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ArticleSeriesItem::getSeriesId, seriesId)
                .orderByAsc(ArticleSeriesItem::getSortOrder);
        List<ArticleSeriesItem> items = itemMapper.selectList(wrapper);
        List<Long> articleIds = items.stream().map(ArticleSeriesItem::getArticleId).collect(Collectors.toList());

        return buildVO(series, articleIds);
    }

    @Override
    public PageResult<ArticleSeriesVO> getMySeries(Long userId, Integer pageNum, Integer pageSize) {
        Page<ArticleSeries> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ArticleSeries> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ArticleSeries::getUserId, userId)
                .orderByDesc(ArticleSeries::getUpdateTime);
        IPage<ArticleSeries> result = seriesMapper.selectPage(page, wrapper);

        List<ArticleSeriesVO> vos = result.getRecords().stream()
                .map(s -> buildVO(s, getArticleIds(s.getId())))
                .collect(Collectors.toList());
        return PageResult.of(pageNum, pageSize, result.getTotal(), vos);
    }

    @Override
    public PageResult<ArticleSeriesVO> getUserPublicSeries(Long userId, Integer pageNum, Integer pageSize) {
        Page<ArticleSeries> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ArticleSeries> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ArticleSeries::getUserId, userId)
                .eq(ArticleSeries::getIsPublic, 1)
                .orderByDesc(ArticleSeries::getUpdateTime);
        IPage<ArticleSeries> result = seriesMapper.selectPage(page, wrapper);

        List<ArticleSeriesVO> vos = result.getRecords().stream()
                .map(s -> buildVO(s, getArticleIds(s.getId())))
                .collect(Collectors.toList());
        return PageResult.of(pageNum, pageSize, result.getTotal(), vos);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addArticleToSeries(Long seriesId, Long userId, Long articleId, Integer sortOrder) {
        ArticleSeries series = seriesMapper.selectById(seriesId);
        if (series == null) {
            throw new BizException("系列不存在");
        }
        if (!series.getUserId().equals(userId)) {
            throw new PermissionDeniedException("无权修改此系列");
        }

        // 检查是否已存在
        LambdaQueryWrapper<ArticleSeriesItem> existingWrapper = new LambdaQueryWrapper<>();
        existingWrapper.eq(ArticleSeriesItem::getSeriesId, seriesId)
                .eq(ArticleSeriesItem::getArticleId, articleId);
        if (itemMapper.selectCount(existingWrapper) > 0) {
            throw new BizException("文章已在系列中");
        }

        ArticleSeriesItem item = ArticleSeriesItem.builder()
                .seriesId(seriesId)
                .articleId(articleId)
                .sortOrder(sortOrder != null ? sortOrder : 999)
                .build();
        itemMapper.insert(item);

        // 更新文章计数
        series.setArticleCount(getArticleIds(seriesId).size() + 1);
        seriesMapper.updateById(series);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeArticleFromSeries(Long seriesId, Long userId, Long articleId) {
        ArticleSeries series = seriesMapper.selectById(seriesId);
        if (series == null) {
            throw new BizException("系列不存在");
        }
        if (!series.getUserId().equals(userId)) {
            throw new PermissionDeniedException("无权修改此系列");
        }

        LambdaQueryWrapper<ArticleSeriesItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ArticleSeriesItem::getSeriesId, seriesId)
                .eq(ArticleSeriesItem::getArticleId, articleId);
        itemMapper.delete(wrapper);

        series.setArticleCount(Math.max(0, getArticleIds(seriesId).size()));
        seriesMapper.updateById(series);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateArticleSort(Long seriesId, Long userId, List<Long> articleIds) {
        ArticleSeries series = seriesMapper.selectById(seriesId);
        if (series == null) {
            throw new BizException("系列不存在");
        }
        if (!series.getUserId().equals(userId)) {
            throw new PermissionDeniedException("无权修改此系列");
        }

        for (int i = 0; i < articleIds.size(); i++) {
            LambdaQueryWrapper<ArticleSeriesItem> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ArticleSeriesItem::getSeriesId, seriesId)
                    .eq(ArticleSeriesItem::getArticleId, articleIds.get(i));
            ArticleSeriesItem item = itemMapper.selectOne(wrapper);
            if (item != null) {
                item.setSortOrder(i + 1);
                itemMapper.updateById(item);
            }
        }
    }

    @Override
    public PageResult<SeriesSelectableArticleVO> getSelectableArticles(Long seriesId, Long userId, String keyword, Integer pageNum, Integer pageSize) {
        ArticleSeries series = seriesMapper.selectById(seriesId);
        if (series == null) {
            throw new BizException("系列不存在");
        }
        if (!series.getUserId().equals(userId)) {
            throw new PermissionDeniedException("无权访问此系列");
        }

        // 查出该系列已有的文章 ID
        LambdaQueryWrapper<ArticleSeriesItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(ArticleSeriesItem::getSeriesId, seriesId);
        List<Long> existingArticleIds = itemMapper.selectList(itemWrapper).stream()
                .map(ArticleSeriesItem::getArticleId)
                .collect(Collectors.toList());

        // 查询用户的文章，排除已在系列中的
        Page<Article> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Article> articleWrapper = new LambdaQueryWrapper<>();
        articleWrapper.eq(Article::getUserId, userId)
                .eq(Article::getDeleted, 0);
        if (!existingArticleIds.isEmpty()) {
            articleWrapper.notIn(Article::getId, existingArticleIds);
        }
        if (StringUtils.hasText(keyword)) {
            articleWrapper.like(Article::getTitle, keyword);
        }
        articleWrapper.orderByDesc(Article::getUpdateTime);

        IPage<Article> result = articleMapper.selectPage(page, articleWrapper);

        List<SeriesSelectableArticleVO> vos = result.getRecords().stream()
                .map(a -> SeriesSelectableArticleVO.builder()
                        .id(a.getId())
                        .title(a.getTitle())
                        .status(a.getStatus())
                        .createTime(a.getCreateTime())
                        .build())
                .collect(Collectors.toList());

        return PageResult.of(pageNum, pageSize, result.getTotal(), vos);
    }

    private List<Long> getArticleIds(Long seriesId) {
        LambdaQueryWrapper<ArticleSeriesItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ArticleSeriesItem::getSeriesId, seriesId)
                .orderByAsc(ArticleSeriesItem::getSortOrder);
        return itemMapper.selectList(wrapper).stream()
                .map(ArticleSeriesItem::getArticleId)
                .collect(Collectors.toList());
    }

    private ArticleSeriesVO buildVO(ArticleSeries series, List<Long> articleIds) {
        User user = userMapper.selectById(series.getUserId());
        List<ArticleSeriesVO.SeriesArticleItem> articles = new ArrayList<>();
        if (!articleIds.isEmpty()) {
            List<Article> articleList = articleMapper.selectBatchIds(articleIds);
            Map<Long, Article> articleMap = articleList.stream()
                    .collect(Collectors.toMap(Article::getId, a -> a));
            for (int i = 0; i < articleIds.size(); i++) {
                Article a = articleMap.get(articleIds.get(i));
                if (a != null) {
                    articles.add(ArticleSeriesVO.SeriesArticleItem.builder()
                            .id(a.getId())
                            .title(a.getTitle())
                            .sortOrder(i + 1)
                            .build());
                }
            }
        }

        return ArticleSeriesVO.builder()
                .id(series.getId())
                .title(series.getTitle())
                .description(series.getDescription())
                .coverImageUrl(series.getCoverImageUrl())
                .authorName(user != null ? user.getNickname() : null)
                .articleCount(series.getArticleCount())
                .isPublic(series.getIsPublic())
                .articles(articles)
                .createTime(series.getCreateTime())
                .updateTime(series.getUpdateTime())
                .build();
    }
}
