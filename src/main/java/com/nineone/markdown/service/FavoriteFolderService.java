package com.nineone.markdown.service;

import com.nineone.markdown.vo.FavoriteFolderVO;

import java.util.List;

/**
 * 收藏夹分类服务接口
 */
public interface FavoriteFolderService {

    /**
     * 创建收藏夹
     * @param userId 用户ID
     * @param name 收藏夹名称
     * @param description 收藏夹描述（可选）
     * @return 创建的收藏夹
     */
    FavoriteFolderVO createFolder(Long userId, String name, String description);

    /**
     * 重命名收藏夹
     * @param userId 用户ID
     * @param folderId 收藏夹ID
     * @param newName 新名称
     * @return 更新后的收藏夹
     */
    FavoriteFolderVO renameFolder(Long userId, Long folderId, String newName);

    /**
     * 删除收藏夹
     * @param userId 用户ID
     * @param folderId 收藏夹ID
     */
    void deleteFolder(Long userId, Long folderId);

    /**
     * 获取用户的收藏夹列表（含文章数量）
     * @param userId 用户ID
     * @return 收藏夹列表
     */
    List<FavoriteFolderVO> getMyFolders(Long userId);

    /**
     * 更新收藏夹排序
     * @param userId 用户ID
     * @param folderIds 排序后的收藏夹ID列表
     */
    void updateFolderSort(Long userId, List<Long> folderIds);
}
