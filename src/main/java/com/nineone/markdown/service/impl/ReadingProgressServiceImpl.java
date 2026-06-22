package com.nineone.markdown.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nineone.common.result.PageResult;
import com.nineone.markdown.entity.Article;
import com.nineone.markdown.entity.ReadingProgress;
import com.nineone.markdown.entity.User;
import com.nineone.markdown.exception.BizException;
import com.nineone.markdown.mapper.ArticleMapper;
import com.nineone.markdown.mapper.ReadingProgressMapper;
import com.nineone.markdown.mapper.UserMapper;
import com.nineone.markdown.service.ReadingProgressService;
import com.nineone.markdown.vo.ReadingHistoryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReadingProgressServiceImpl implements ReadingProgressService {

    private final ReadingProgressMapper readingProgressMapper;
    private final ArticleMapper articleMapper;
    private final UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveOrUpdateProgress(Long userId, Long articleId, Integer progress, String lastPosition) {
        if (progress != null && (progress < 0 || progress > 100)) {
            throw new BizException("进度值必须在0-100之间");
        }

        ReadingProgress existing = readingProgressMapper.findByUserIdAndArticleId(userId, articleId);
        if (existing != null) {
            if (progress != null && (existing.getProgress() == null || progress >= existing.getProgress())) {
                existing.setProgress(progress);
            }
            if (lastPosition != null) existing.setLastPosition(lastPosition);
            existing.setLastReadTime(LocalDateTime.now());
            readingProgressMapper.updateById(existing);
        } else {
            ReadingProgress rp = ReadingProgress.builder()
                    .userId(userId)
                    .articleId(articleId)
                    .progress(progress != null ? progress : 0)
                    .lastPosition(lastPosition)
                    .lastReadTime(LocalDateTime.now())
                    .build();
            readingProgressMapper.insert(rp);
        }
    }

    @Override
    public ReadingProgress getProgress(Long userId, Long articleId) {
        return readingProgressMapper.findByUserIdAndArticleId(userId, articleId);
    }

    @Override
    public List<ReadingProgress> listProgress(Long userId) {
        LambdaQueryWrapper<ReadingProgress> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReadingProgress::getUserId, userId)
                .orderByDesc(ReadingProgress::getLastReadTime);
        return readingProgressMapper.selectList(wrapper);
    }

    @Override
    public PageResult<ReadingHistoryVO> getReadingHistory(Long userId, Integer pageNum, Integer pageSize) {
        Page<ReadingProgress> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ReadingProgress> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReadingProgress::getUserId, userId)
                .orderByDesc(ReadingProgress::getLastReadTime);
        IPage<ReadingProgress> result = readingProgressMapper.selectPage(page, wrapper);

        List<Long> articleIds = result.getRecords().stream()
                .map(ReadingProgress::getArticleId)
                .collect(Collectors.toList());
        final Map<Long, Article> articleMap;
        final Map<Long, User> userMap;
        if (!articleIds.isEmpty()) {
            List<Article> articles = articleMapper.selectBatchIds(articleIds);
            articleMap = articles.stream().collect(Collectors.toMap(Article::getId, a -> a));
            List<Long> authorIds = articles.stream().map(Article::getUserId).distinct().collect(Collectors.toList());
            if (!authorIds.isEmpty()) {
                List<User> users = userMapper.selectBatchIds(authorIds);
                userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u));
            } else {
                userMap = Collections.emptyMap();
            }
        } else {
            articleMap = Collections.emptyMap();
            userMap = Collections.emptyMap();
        }

        List<ReadingHistoryVO> vos = result.getRecords().stream()
                .map(rp -> {
                    Article article = articleMap.get(rp.getArticleId());
                    User author = article != null ? userMap.get(article.getUserId()) : null;
                    return ReadingHistoryVO.builder()
                            .articleId(rp.getArticleId())
                            .title(article != null ? article.getTitle() : "已删除的文章")
                            .authorName(author != null ? author.getNickname() : null)
                            .progress(rp.getProgress())
                            .lastPosition(rp.getLastPosition())
                            .lastReadTime(rp.getLastReadTime())
                            .build();
                })
                .collect(Collectors.toList());

        return PageResult.of(pageNum, pageSize, result.getTotal(), vos);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteHistoryRecord(Long userId, Long id) {
        ReadingProgress record = readingProgressMapper.selectById(id);
        if (record == null || !record.getUserId().equals(userId)) {
            throw new BizException("记录不存在或无权删除");
        }
        readingProgressMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearHistory(Long userId) {
        LambdaQueryWrapper<ReadingProgress> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReadingProgress::getUserId, userId);
        readingProgressMapper.delete(wrapper);
    }
}
