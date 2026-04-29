package com.nineone.markdown.controller;

import com.nineone.markdown.common.Result;
import com.nineone.markdown.exception.AuthenticationException;
import com.nineone.markdown.security.CustomUserDetails;
import com.nineone.markdown.service.FavoriteFolderService;
import com.nineone.markdown.vo.FavoriteFolderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 收藏夹分类控制器
 */
@RestController
@RequestMapping("/api/favorites/folders")
@RequiredArgsConstructor
public class FavoriteFolderController {

    private final FavoriteFolderService favoriteFolderService;

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
            return ((CustomUserDetails) principal).getId();
        }
        throw new AuthenticationException("用户未登录或登录已过期", "TOKEN_EXPIRED");
    }

    /**
     * 创建收藏夹
     */
    @PostMapping
    public Result<FavoriteFolderVO> createFolder(@RequestBody Map<String, String> body) {
        Long userId = getCurrentUserId();
        String name = body.get("name");
        String description = body.get("description");
        FavoriteFolderVO folder = favoriteFolderService.createFolder(userId, name, description);
        return Result.success("收藏夹创建成功", folder);
    }

    /**
     * 获取我的收藏夹列表（含文章数量）
     */
    @GetMapping
    public Result<List<FavoriteFolderVO>> getMyFolders() {
        Long userId = getCurrentUserId();
        List<FavoriteFolderVO> folders = favoriteFolderService.getMyFolders(userId);
        return Result.success(folders);
    }

    /**
     * 重命名收藏夹
     */
    @PutMapping("/{folderId}")
    public Result<FavoriteFolderVO> renameFolder(@PathVariable Long folderId,
                                                  @RequestBody Map<String, String> body) {
        Long userId = getCurrentUserId();
        String newName = body.get("name");
        FavoriteFolderVO folder = favoriteFolderService.renameFolder(userId, folderId, newName);
        return Result.success("收藏夹重命名成功", folder);
    }

    /**
     * 删除收藏夹
     */
    @DeleteMapping("/{folderId}")
    public Result<Void> deleteFolder(@PathVariable Long folderId) {
        Long userId = getCurrentUserId();
        favoriteFolderService.deleteFolder(userId, folderId);
        return Result.success("收藏夹已删除", null);
    }

    /**
     * 更新收藏夹排序
     */
    @PutMapping("/sort")
    public Result<Void> updateFolderSort(@RequestBody Map<String, List<Long>> body) {
        Long userId = getCurrentUserId();
        List<Long> folderIds = body.get("folderIds");
        favoriteFolderService.updateFolderSort(userId, folderIds);
        return Result.success("排序更新成功", null);
    }
}
