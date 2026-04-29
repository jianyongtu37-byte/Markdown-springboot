package com.nineone.markdown.controller;

import com.nineone.markdown.common.Result;
import com.nineone.markdown.entity.BackupRecord;
import com.nineone.markdown.exception.AuthenticationException;
import com.nineone.markdown.security.CustomUserDetails;
import com.nineone.markdown.service.ExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 数据导出控制器
 */
@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;

    /**
     * 获取当前登录用户的ID
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationException("用户未认证", "UNAUTHENTICATED");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails) {
            CustomUserDetails userDetails = (CustomUserDetails) principal;
            return userDetails.getId();
        } else {
            throw new AuthenticationException("用户未登录或登录已过期", "TOKEN_EXPIRED");
        }
    }

    /**
     * 导出单篇文章为PDF
     */
    @PostMapping("/{articleId}/pdf")
    public Result<String> exportToPdf(@PathVariable Long articleId) {
        Long userId = getCurrentUserId();
        String filePath = exportService.exportArticleToPdf(articleId, userId);
        return Result.success("PDF导出成功", filePath);
    }

    /**
     * 导出单篇文章为Word
     */
    @PostMapping("/{articleId}/word")
    public Result<String> exportToWord(@PathVariable Long articleId) {
        Long userId = getCurrentUserId();
        String filePath = exportService.exportArticleToWord(articleId, userId);
        return Result.success("Word导出成功", filePath);
    }

    /**
     * 导出用户所有文章为Markdown ZIP包
     */
    @PostMapping("/all-markdown")
    public Result<String> exportAllMarkdown() {
        Long userId = getCurrentUserId();
        String filePath = exportService.exportAllArticlesAsMarkdownZip(userId);
        return Result.success("全站Markdown导出成功", filePath);
    }

    /**
     * 下载导出文件
     */
    @GetMapping("/download")
    public ResponseEntity<Resource> downloadFile(@RequestParam String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file);

        // 获取文件名用于 Content-Disposition
        String filename = file.getName();
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8)
                .replace("+", "%20");

        // 根据文件扩展名设置 Content-Type
        MediaType mediaType;
        if (filename.endsWith(".pdf")) {
            mediaType = MediaType.APPLICATION_PDF;
        } else if (filename.endsWith(".docx")) {
            mediaType = MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        } else if (filename.endsWith(".zip")) {
            mediaType = MediaType.parseMediaType("application/zip");
        } else {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + encodedFilename + "\"")
                .body(resource);
    }

    /**
     * 获取当前用户的导出记录列表
     */
    @GetMapping("/records")
    public Result<List<BackupRecord>> getExportRecords() {
        Long userId = getCurrentUserId();
        List<BackupRecord> records = exportService.getExportRecords(userId);
        return Result.success(records);
    }

    /**
     * 删除导出记录
     */
    @DeleteMapping("/records/{recordId}")
    public Result<Void> deleteExportRecord(@PathVariable Long recordId) {
        Long userId = getCurrentUserId();
        exportService.deleteExportRecord(recordId, userId);
        return Result.success("删除成功", null);
    }
}
