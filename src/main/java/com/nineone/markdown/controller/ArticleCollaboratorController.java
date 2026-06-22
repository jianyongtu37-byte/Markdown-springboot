package com.nineone.markdown.controller;

import com.nineone.common.result.PageResult;
import com.nineone.common.result.Result;
import com.nineone.markdown.entity.ArticleCollaborator;
import com.nineone.markdown.service.ArticleCollaboratorService;
import com.nineone.markdown.util.UserContextHolder;
import com.nineone.markdown.vo.ArticleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/articles")
@RequiredArgsConstructor
public class ArticleCollaboratorController {

    private final ArticleCollaboratorService articleCollaboratorService;

    @GetMapping("/{id}/collaborators")
    public Result<List<ArticleCollaborator>> getCollaborators(@PathVariable("id") Long articleId) {
        List<ArticleCollaborator> list = articleCollaboratorService.getCollaborators(articleId);
        return Result.success(list);
    }

    @PostMapping("/{id}/collaborators")
    public Result<Void> addCollaborator(@PathVariable("id") Long articleId, @RequestBody Map<String, String> body) {
        Long userId = UserContextHolder.requireUserId();
        Long collaboratorUserId = Long.parseLong(body.get("userId"));
        String permission = body.getOrDefault("permission", "VIEW");
        articleCollaboratorService.addCollaborator(articleId, userId, collaboratorUserId, permission);
        return Result.success("协作者已添加", null);
    }

    @DeleteMapping("/{id}/collaborators/{userId}")
    public Result<Void> removeCollaborator(@PathVariable("id") Long articleId,
                                            @PathVariable("userId") Long collaboratorUserId) {
        Long userId = UserContextHolder.requireUserId();
        articleCollaboratorService.removeCollaborator(articleId, userId, collaboratorUserId);
        return Result.success("协作者已移除", null);
    }

    @GetMapping("/shared")
    public Result<PageResult<ArticleVO>> sharedArticles(
            @RequestParam(value = "page", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "size", defaultValue = "10") Integer pageSize) {
        Long userId = UserContextHolder.requireUserId();
        PageResult<ArticleVO> result = articleCollaboratorService.getSharedArticles(userId, pageNum, pageSize);
        return Result.success(result);
    }
}
