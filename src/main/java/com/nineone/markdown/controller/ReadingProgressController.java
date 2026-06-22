package com.nineone.markdown.controller;

import com.nineone.common.result.PageResult;
import com.nineone.common.result.Result;
import com.nineone.markdown.entity.ReadingProgress;
import com.nineone.markdown.service.ReadingProgressService;
import com.nineone.markdown.util.UserContextHolder;
import com.nineone.markdown.vo.ReadingHistoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReadingProgressController {

    private final ReadingProgressService readingProgressService;

    @PostMapping("/reading-progress")
    public Result<Void> saveProgress(@RequestBody Map<String, String> body) {
        Long userId = UserContextHolder.requireUserId();
        Long articleId = Long.parseLong(body.get("articleId"));
        Integer progress = body.containsKey("progress") ? Integer.parseInt(body.get("progress")) : null;
        String lastPosition = body.get("lastPosition");
        readingProgressService.saveOrUpdateProgress(userId, articleId, progress, lastPosition);
        return Result.success("进度已保存", null);
    }

    @GetMapping("/reading-progress/{articleId}")
    public Result<ReadingProgress> getProgress(@PathVariable("articleId") Long articleId) {
        Long userId = UserContextHolder.requireUserId();
        ReadingProgress progress = readingProgressService.getProgress(userId, articleId);
        return Result.success(progress);
    }

    @GetMapping("/reading-progress")
    public Result<List<ReadingProgress>> listProgress() {
        Long userId = UserContextHolder.requireUserId();
        List<ReadingProgress> list = readingProgressService.listProgress(userId);
        return Result.success(list);
    }

    /**
     * 获取阅读历史（带文章信息的分页列表）
     */
    @GetMapping("/reading-history")
    public Result<PageResult<ReadingHistoryVO>> getReadingHistory(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
        Long userId = UserContextHolder.requireUserId();
        PageResult<ReadingHistoryVO> result = readingProgressService.getReadingHistory(userId, pageNum, pageSize);
        return Result.success(result);
    }

    /**
     * 删除单条阅读历史
     */
    @DeleteMapping("/reading-history/{id}")
    public Result<Void> deleteHistoryRecord(@PathVariable("id") Long id) {
        Long userId = UserContextHolder.requireUserId();
        readingProgressService.deleteHistoryRecord(userId, id);
        return Result.success("记录已删除", null);
    }

    /**
     * 清空所有阅读历史
     */
    @DeleteMapping("/reading-history")
    public Result<Void> clearHistory() {
        Long userId = UserContextHolder.requireUserId();
        readingProgressService.clearHistory(userId);
        return Result.success("历史已清空", null);
    }
}
