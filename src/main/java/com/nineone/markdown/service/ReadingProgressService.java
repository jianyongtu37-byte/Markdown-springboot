package com.nineone.markdown.service;

import com.nineone.common.result.PageResult;
import com.nineone.markdown.entity.ReadingProgress;
import com.nineone.markdown.vo.ReadingHistoryVO;

import java.util.List;

public interface ReadingProgressService {

    void saveOrUpdateProgress(Long userId, Long articleId, Integer progress, String lastPosition);

    ReadingProgress getProgress(Long userId, Long articleId);

    List<ReadingProgress> listProgress(Long userId);

    PageResult<ReadingHistoryVO> getReadingHistory(Long userId, Integer pageNum, Integer pageSize);

    void deleteHistoryRecord(Long userId, Long id);

    void clearHistory(Long userId);
}
