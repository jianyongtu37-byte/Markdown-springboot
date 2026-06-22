package com.nineone.markdown.controller;

import com.nineone.common.result.PageResult;
import com.nineone.common.result.Result;
import com.nineone.markdown.dto.ArticleCreateDTO;
import com.nineone.markdown.dto.ArticleSaveDTO;
import com.nineone.markdown.entity.Article;
import com.nineone.markdown.entity.ArticleTimestamp;
import com.nineone.markdown.enums.ArticleStatusEnum;
import com.nineone.markdown.service.ArticleService;
import com.nineone.markdown.service.ReadingProgressService;
import com.nineone.markdown.util.TimestampExtractor;
import com.nineone.markdown.util.UserContextHolder;
import com.nineone.markdown.vo.ArticleDetailVO;
import com.nineone.markdown.vo.ArticleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * 文章控制器
 */
@RestController
@RequestMapping("/api/articles")
@RequiredArgsConstructor
@Validated
public class ArticleController {

    private final ArticleService articleService;
    private final ReadingProgressService readingProgressService;

    /**
     * 创建文章
     */
    @PostMapping
    public Result<Long> createArticle(@Valid @RequestBody ArticleCreateDTO dto) {
        Long currentUserId = UserContextHolder.requireUserId();
        
        Article article = Article.builder()
                .userId(currentUserId)
                .categoryId(dto.getCategoryId())
                .title(dto.getTitle())
                .content(dto.getContent())
                .videoUrl(dto.getVideoUrl())
                .aiStatus(dto.getAiStatus())
                .status(dto.getStatus() != null ? dto.getStatus() : ArticleStatusEnum.DRAFT)
                .viewCount(0)
                .allowExport(dto.getAllowExport() != null ? dto.getAllowExport() : 1)
                .build();

        Long articleId = articleService.createArticle(article, dto.getTagNames());
        return Result.success("文章创建成功", articleId);
    }

    /**
     * 获取文章详情
     */
    @GetMapping("/{id}")
    public Result<ArticleVO> getArticle(@PathVariable("id") Long id) {
        ArticleVO article = articleService.getArticleDetail(id);
        if (article == null) {
            return Result.notFound("文章不存在");
        }
        recordReadingHistory(id);
        return Result.success(article);
    }

    /**
     * 更新文章
     */
    @PutMapping("/{id}")
    public Result<Void> updateArticle(@PathVariable("id") Long id, @Valid @RequestBody ArticleCreateDTO dto) {
        Long currentUserId = UserContextHolder.requireUserId();

        Article article = Article.builder()
                .id(id)
                .userId(currentUserId)
                .categoryId(dto.getCategoryId())
                .title(dto.getTitle())
                .content(dto.getContent())
                .videoUrl(dto.getVideoUrl())
                .aiStatus(dto.getAiStatus())
                .status(dto.getStatus() != null ? dto.getStatus() : ArticleStatusEnum.DRAFT)
                .build();

        boolean success = articleService.updateArticle(article, dto.getTagNames());
        if (!success) {
            return Result.notFound("文章不存在，更新失败");
        }
        return Result.success("文章更新成功", null);
    }

    /**
     * 删除文章
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteArticle(@PathVariable("id") Long id) {
        boolean success = articleService.deleteArticle(id);
        if (!success) {
            return Result.notFound("文章不存在，删除失败");
        }
        return Result.success("文章删除成功", null);
    }

    /**
     * 获取文章列表（分页）
     */
    @GetMapping
    public Result<PageResult<ArticleVO>> getArticleList(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "tagId", required = false) Long tagId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "isPublic", required = false) Integer isPublic) {
        
        PageResult<ArticleVO> pageResult = articleService.getArticleList(pageNum, pageSize, categoryId, tagId, keyword, status, isPublic);
        return Result.success(pageResult);
    }

    /**
     * 增加文章阅读量
     */
    @PostMapping("/{id}/view")
    public Result<Void> increaseViewCount(@PathVariable("id") Long id) {
        boolean success = articleService.increaseViewCount(id);
        if (!success) {
            return Result.notFound("文章不存在，阅读量增加失败");
        }
        return Result.success("阅读量增加成功", null);
    }

    /**
     * 更新AI摘要状态
     */
    @PostMapping("/{id}/ai-status")
    public Result<Void> updateAiStatus(
            @PathVariable("id") Long id,
            @RequestParam("aiStatus") @NotNull Integer aiStatus,
            @RequestParam(required = false) String summary) {
        
        boolean success = articleService.updateAiStatus(id, aiStatus, summary);
        if (!success) {
            return Result.notFound("文章不存在，更新失败");
        }
        return Result.success("AI摘要状态更新成功", null);
    }

    /**
     * 获取我的文章列表（包括草稿和私密文章）
     */
    @GetMapping("/my")
    public Result<PageResult<ArticleVO>> getMyArticles(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "tagId", required = false) Long tagId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "isPublic", required = false) Integer isPublic) {
        
        PageResult<ArticleVO> pageResult = articleService.getMyArticles(pageNum, pageSize, categoryId, tagId, keyword, status, isPublic);
        return Result.success(pageResult);
    }

    /**
     * 获取指定用户的公开文章列表
     */
    @GetMapping("/user/{userId}")
    public Result<PageResult<ArticleVO>> getUserArticles(
            @PathVariable("userId") Long userId,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "tagId", required = false) Long tagId,
            @RequestParam(value = "keyword", required = false) String keyword) {
        
        PageResult<ArticleVO> pageResult = articleService.getUserArticles(userId, pageNum, pageSize, categoryId, tagId, keyword);
        return Result.success(pageResult);
    }

    /**
     * 获取我的文章统计信息
     */
    @GetMapping("/my/stats")
    public Result<Map<String, Object>> getMyArticleStats() {
        Map<String, Object> stats = articleService.getMyArticleStats();
        return Result.success(stats);
    }

    /**
     * 批量更新文章状态
     */
    @PutMapping("/batch-status")
    public Result<Void> batchUpdateStatus(
            @RequestBody List<Long> articleIds,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "isPublic", required = false) Integer isPublic) {
        
        boolean success = articleService.batchUpdateStatus(articleIds, status, isPublic);
        if (!success) {
            return Result.badRequest("批量更新失败");
        }
        return Result.success("批量更新成功", null);
    }

    /**
     * 文章详情（含视频信息和时间戳目录）
     */
    @GetMapping("/{id}/detail")
    public Result<ArticleDetailVO> detail(@PathVariable("id") Long id) {
        ArticleDetailVO detail = articleService.getDetail(id);
        recordReadingHistory(id);
        return Result.success(detail);
    }

    /**
     * 保存/更新文章（支持视频绑定和时间戳重建）
     */
    @PostMapping("/save")
    public Result<Void> save(@RequestBody @Valid ArticleSaveDTO dto) {
        Long currentUserId = UserContextHolder.requireUserId();
        articleService.save(dto, currentUserId);
        return Result.success("文章保存成功", null);
    }

    /**
     * 获取文章的时间戳目录
     */
    @GetMapping("/{id}/timestamps")
    public Result<List<ArticleTimestamp>> timestamps(@PathVariable("id") Long id) {
        List<ArticleTimestamp> timestamps = articleService.getTimestamps(id);
        return Result.success(timestamps);
    }

    /**
     * 从文章内容中提取时间戳
     * 前端发送文章内容，后端使用 TimestampExtractor 解析并返回时间戳列表
     */
    @PostMapping("/{id}/extract-timestamps")
    public Result<List<ArticleTimestamp>> extractTimestamps(
            @PathVariable("id") Long id,
            @RequestBody Map<String, String> request) {
        String content = request.get("content");
        if (content == null || content.trim().isEmpty()) {
            return Result.badRequest("文章内容不能为空");
        }
        List<ArticleTimestamp> timestamps = TimestampExtractor.extractTimestamps(content);
        return Result.success(timestamps);
    }

    /**
     * 更新文章的导出权限设置
     * @param id 文章ID
     * @param allowExport 是否允许导出（1-允许，0-禁止）
     */
    @PutMapping("/{id}/allow-export")
    public Result<Void> updateAllowExport(
            @PathVariable("id") Long id,
            @RequestParam(value = "allowExport") @NotNull Integer allowExport) {
        articleService.updateAllowExport(id, allowExport);
        return Result.success("导出权限设置已更新", null);
    }

    @PutMapping("/{id}/pin")
    public Result<Void> pinArticle(@PathVariable("id") Long id) {
        articleService.pinArticle(id);
        return Result.success("文章已置顶", null);
    }

    @PutMapping("/{id}/unpin")
    public Result<Void> unpinArticle(@PathVariable("id") Long id) {
        articleService.unpinArticle(id);
        return Result.success("已取消置顶", null);
    }

    private void recordReadingHistory(Long articleId) {
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return;
        }
        try {
            readingProgressService.saveOrUpdateProgress(userId, articleId, null, null);
        } catch (Exception e) {
            // 阅读历史记录失败不影响文章查看
        }
    }
}
