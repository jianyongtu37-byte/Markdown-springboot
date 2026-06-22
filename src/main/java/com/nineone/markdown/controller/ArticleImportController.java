package com.nineone.markdown.controller;

import com.nineone.common.result.Result;
import com.nineone.markdown.service.ArticleImportService;
import com.nineone.markdown.util.UserContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/articles/import")
@RequiredArgsConstructor
public class ArticleImportController {

    private final ArticleImportService articleImportService;

    @PostMapping("/file")
    public Result<List<Long>> importFromFiles(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "categoryId", required = false) Long categoryId) {
        Long userId = UserContextHolder.requireUserId();
        List<Long> articleIds = articleImportService.importFromFiles(files, userId, categoryId);
        return Result.success("导入成功", articleIds);
    }

    @PostMapping("/url")
    public Result<Long> importFromUrl(@RequestBody Map<String, String> body) {
        Long userId = UserContextHolder.requireUserId();
        String url = body.get("url");
        if (url == null || url.isBlank()) {
            return Result.badRequest("URL不能为空");
        }
        Long categoryId = body.containsKey("categoryId") ? Long.parseLong(body.get("categoryId")) : null;
        Long articleId = articleImportService.importFromUrl(url, userId, categoryId);
        return Result.success("导入成功", articleId);
    }
}
