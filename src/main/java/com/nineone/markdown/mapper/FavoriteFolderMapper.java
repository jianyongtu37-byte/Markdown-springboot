package com.nineone.markdown.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nineone.markdown.entity.FavoriteFolder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 收藏夹分类 Mapper 接口
 */
@Mapper
public interface FavoriteFolderMapper extends BaseMapper<FavoriteFolder> {

    /**
     * 查询用户的所有收藏夹及其文章数量
     * @param userId 用户ID
     * @return 收藏夹ID -> 文章数量 的映射列表
     */
    @Select("SELECT ff.id, COUNT(uf.id) AS article_count " +
            "FROM favorite_folder ff " +
            "LEFT JOIN user_favorite uf ON uf.folder_name = ff.name AND uf.user_id = ff.user_id " +
            "WHERE ff.user_id = #{userId} " +
            "GROUP BY ff.id " +
            "ORDER BY ff.sort_order ASC, ff.create_time ASC")
    List<Map<String, Object>> selectFolderArticleCounts(@Param("userId") Long userId);

    /**
     * 查询用户是否已存在同名收藏夹
     * @param userId 用户ID
     * @param name 收藏夹名称
     * @return 记录数
     */
    @Select("SELECT COUNT(*) FROM favorite_folder WHERE user_id = #{userId} AND name = #{name}")
    int countByNameAndUserId(@Param("userId") Long userId, @Param("name") String name);
}
