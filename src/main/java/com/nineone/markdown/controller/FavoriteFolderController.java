package com.nineone.markdown.controller;

import com.nineone.common.result.Result;
import com.nineone.markdown.service.FavoriteFolderService;
import com.nineone.markdown.util.UserContextHolder;
import com.nineone.markdown.vo.FavoriteFolderVO;
import lombok.RequiredArgsConstructor;
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
     * 创建收藏夹
     */
    @PostMapping
    public Result<FavoriteFolderVO> createFolder(@RequestBody Map<String, String> body) {
        Long userId = UserContextHolder.requireUserId();
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
        Long userId = UserContextHolder.requireUserId();
        List<FavoriteFolderVO> folders = favoriteFolderService.getMyFolders(userId);
        return Result.success(folders);
    }

    /**
     * 重命名收藏夹
     */
    @PutMapping("/{folderId}")
    public Result<FavoriteFolderVO> renameFolder(@PathVariable("folderId") Long folderId,
                                                  @RequestBody Map<String, String> body) {
        Long userId = UserContextHolder.requireUserId();
        String newName = body.get("name");
        FavoriteFolderVO folder = favoriteFolderService.renameFolder(userId, folderId, newName);
        return Result.success("收藏夹重命名成功", folder);
    }

    /**
     * 删除收藏夹
     */
    @DeleteMapping("/{folderId}")
    public Result<Void> deleteFolder(@PathVariable("folderId") Long folderId) {
        Long userId = UserContextHolder.requireUserId();
        favoriteFolderService.deleteFolder(userId, folderId);
        return Result.success("收藏夹已删除", null);
    }

    /**
     * 更新收藏夹排序
     */
    @PutMapping("/sort")
    public Result<Void> updateFolderSort(@RequestBody Map<String, List<Long>> body) {
        Long userId = UserContextHolder.requireUserId();
        List<Long> folderIds = body.get("folderIds");
        favoriteFolderService.updateFolderSort(userId, folderIds);
        return Result.success("排序更新成功", null);
    }
}
