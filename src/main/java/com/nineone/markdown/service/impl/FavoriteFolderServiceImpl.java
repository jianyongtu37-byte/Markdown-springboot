package com.nineone.markdown.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nineone.markdown.entity.FavoriteFolder;
import com.nineone.markdown.exception.BizException;
import com.nineone.markdown.mapper.FavoriteFolderMapper;
import com.nineone.markdown.service.FavoriteFolderService;
import com.nineone.markdown.vo.FavoriteFolderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 收藏夹分类服务实现类
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FavoriteFolderServiceImpl implements FavoriteFolderService {

    private final FavoriteFolderMapper favoriteFolderMapper;

    /**
     * 收藏夹名称最大长度
     */
    private static final int MAX_NAME_LENGTH = 50;

    /**
     * 收藏夹描述最大长度
     */
    private static final int MAX_DESCRIPTION_LENGTH = 200;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FavoriteFolderVO createFolder(Long userId, String name, String description) {
        // 参数校验
        if (name == null || name.isBlank()) {
            throw new BizException("收藏夹名称不能为空");
        }
        name = name.trim();
        if (name.length() > MAX_NAME_LENGTH) {
            throw new BizException("收藏夹名称不能超过" + MAX_NAME_LENGTH + "个字符");
        }

        // 检查是否已存在同名收藏夹
        if (favoriteFolderMapper.countByNameAndUserId(userId, name) > 0) {
            throw new BizException("已存在同名收藏夹");
        }

        // 处理描述
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            description = description.substring(0, MAX_DESCRIPTION_LENGTH);
        }

        // 获取当前最大排序值
        LambdaQueryWrapper<FavoriteFolder> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FavoriteFolder::getUserId, userId)
                   .orderByDesc(FavoriteFolder::getSortOrder)
                   .last("LIMIT 1");
        FavoriteFolder lastFolder = favoriteFolderMapper.selectOne(queryWrapper);
        int sortOrder = (lastFolder != null && lastFolder.getSortOrder() != null)
                ? lastFolder.getSortOrder() + 1 : 0;

        // 创建收藏夹
        FavoriteFolder folder = FavoriteFolder.builder()
                .userId(userId)
                .name(name)
                .description(description)
                .sortOrder(sortOrder)
                .build();
        favoriteFolderMapper.insert(folder);

        log.info("用户{}创建收藏夹: {} (ID: {})", userId, name, folder.getId());
        return convertToVO(folder, 0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FavoriteFolderVO renameFolder(Long userId, Long folderId, String newName) {
        // 参数校验
        if (newName == null || newName.isBlank()) {
            throw new BizException("收藏夹名称不能为空");
        }
        newName = newName.trim();
        if (newName.length() > MAX_NAME_LENGTH) {
            throw new BizException("收藏夹名称不能超过" + MAX_NAME_LENGTH + "个字符");
        }

        // 查询收藏夹
        FavoriteFolder folder = favoriteFolderMapper.selectById(folderId);
        if (folder == null) {
            throw new BizException("收藏夹不存在");
        }
        if (!folder.getUserId().equals(userId)) {
            throw new BizException("无权操作此收藏夹");
        }

        // 检查新名称是否与已有收藏夹重名（排除自身）
        if (!folder.getName().equals(newName) &&
                favoriteFolderMapper.countByNameAndUserId(userId, newName) > 0) {
            throw new BizException("已存在同名收藏夹");
        }

        // 更新名称
        folder.setName(newName);
        favoriteFolderMapper.updateById(folder);

        log.info("用户{}重命名收藏夹: {} -> {} (ID: {})", userId, folder.getName(), newName, folderId);
        return convertToVO(folder, 0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFolder(Long userId, Long folderId) {
        // 查询收藏夹
        FavoriteFolder folder = favoriteFolderMapper.selectById(folderId);
        if (folder == null) {
            throw new BizException("收藏夹不存在");
        }
        if (!folder.getUserId().equals(userId)) {
            throw new BizException("无权操作此收藏夹");
        }

        // 删除收藏夹（user_favorite 表中的关联记录保留，但 folder_name 字段仍然存在）
        // 注意：删除收藏夹不会删除 user_favorite 中的收藏记录，只是收藏夹分类被移除
        favoriteFolderMapper.deleteById(folderId);

        log.info("用户{}删除收藏夹: {} (ID: {})", userId, folder.getName(), folderId);
    }

    @Override
    public List<FavoriteFolderVO> getMyFolders(Long userId) {
        // 查询用户的所有收藏夹
        LambdaQueryWrapper<FavoriteFolder> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FavoriteFolder::getUserId, userId)
                   .orderByAsc(FavoriteFolder::getSortOrder)
                   .orderByAsc(FavoriteFolder::getCreateTime);
        List<FavoriteFolder> folders = favoriteFolderMapper.selectList(queryWrapper);

        if (folders.isEmpty()) {
            return new ArrayList<>();
        }

        // 批量查询每个收藏夹的文章数量
        List<Map<String, Object>> countMaps = favoriteFolderMapper.selectFolderArticleCounts(userId);
        Map<Long, Integer> countMap = countMaps.stream()
                .collect(Collectors.toMap(
                        m -> (Long) m.get("id"),
                        m -> ((Number) m.get("article_count")).intValue()
                ));

        // 构建 VO
        return folders.stream()
                .map(folder -> convertToVO(folder, countMap.getOrDefault(folder.getId(), 0)))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateFolderSort(Long userId, List<Long> folderIds) {
        if (folderIds == null || folderIds.isEmpty()) {
            return;
        }

        // 查询用户的所有收藏夹
        LambdaQueryWrapper<FavoriteFolder> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FavoriteFolder::getUserId, userId);
        List<FavoriteFolder> folders = favoriteFolderMapper.selectList(queryWrapper);
        Map<Long, FavoriteFolder> folderMap = folders.stream()
                .collect(Collectors.toMap(FavoriteFolder::getId, f -> f));

        // 更新排序
        for (int i = 0; i < folderIds.size(); i++) {
            Long folderId = folderIds.get(i);
            FavoriteFolder folder = folderMap.get(folderId);
            if (folder != null && folder.getUserId().equals(userId)) {
                folder.setSortOrder(i);
                favoriteFolderMapper.updateById(folder);
            }
        }

        log.info("用户{}更新收藏夹排序", userId);
    }

    /**
     * 将实体转换为 VO
     */
    private FavoriteFolderVO convertToVO(FavoriteFolder folder, int articleCount) {
        return FavoriteFolderVO.builder()
                .id(folder.getId())
                .name(folder.getName())
                .description(folder.getDescription())
                .sortOrder(folder.getSortOrder())
                .articleCount(articleCount)
                .createTime(folder.getCreateTime())
                .updateTime(folder.getUpdateTime())
                .build();
    }
}
