package com.nineone.markdown.controller;

import com.nineone.common.result.Result;
import com.nineone.markdown.entity.BackupRecord;
import com.nineone.markdown.service.ExportService;
import com.nineone.markdown.util.UserContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 数据导出控制器
 */
@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;

    @Value("${app.export.dir:exports}")
    private String exportDir;

    /**
     * 导出单篇文章为PDF
     */
    @PostMapping("/{articleId}/pdf")
    public Result<String> exportToPdf(@PathVariable("articleId") Long articleId) {
        Long userId = UserContextHolder.requireUserId();
        String filePath = exportService.exportArticleToPdf(articleId, userId);
        return Result.success("PDF导出成功", filePath);
    }

    /**
     * 导出单篇文章为Word
     */
    @PostMapping("/{articleId}/word")
    public Result<String> exportToWord(@PathVariable("articleId") Long articleId) {
        Long userId = UserContextHolder.requireUserId();
        String filePath = exportService.exportArticleToWord(articleId, userId);
        return Result.success("Word导出成功", filePath);
    }

    /**
     * 导出用户所有文章为Markdown ZIP包
     */
    @PostMapping("/all-markdown")
    public Result<String> exportAllMarkdown() {
        Long userId = UserContextHolder.requireUserId();
        String filePath = exportService.exportAllArticlesAsMarkdownZip(userId);
        return Result.success("全站Markdown导出成功", filePath);
    }

    /**
     * 下载导出文件
     */
    @GetMapping("/download")
    public ResponseEntity<Resource> downloadFile(@RequestParam(value = "filePath") String filePath) {
        // 认证检查：未登录用户无法下载导出文件
        UserContextHolder.requireUserId();

        // 路径穿越防护：禁止 ..、绝对路径、\ 路径分隔符
        if (filePath.contains("..") || filePath.startsWith("/") || filePath.contains("\\")) {
            return ResponseEntity.badRequest().build();
        }

        // 将导出目录转为绝对路径
        Path baseDir = Paths.get(exportDir).toAbsolutePath().normalize();

        // 将请求路径拼接在导出目录下并规范化
        Path resolvedPath = baseDir.resolve(filePath).normalize();

        // 确保解析后的路径仍在导出目录内
        if (!resolvedPath.startsWith(baseDir)) {
            return ResponseEntity.badRequest().build();
        }

        File file = resolvedPath.toFile();
        if (!file.exists() || !file.isFile()) {
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
        Long userId = UserContextHolder.requireUserId();
        List<BackupRecord> records = exportService.getExportRecords(userId);
        return Result.success(records);
    }

    /**
     * 删除导出记录
     */
    @DeleteMapping("/records/{recordId}")
    public Result<Void> deleteExportRecord(@PathVariable("recordId") Long recordId) {
        Long userId = UserContextHolder.requireUserId();
        exportService.deleteExportRecord(recordId, userId);
        return Result.success("删除成功", null);
    }
}
