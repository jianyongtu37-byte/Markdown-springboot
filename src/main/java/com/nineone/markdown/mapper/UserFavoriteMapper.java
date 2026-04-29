package com.nineone.markdown.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nineone.markdown.entity.UserFavorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户收藏 Mapper 接口
 */
@Mapper
public interface UserFavoriteMapper extends BaseMapper<UserFavorite> {

    /**
     * 查询用户是否已收藏某文章
     * @param userId 用户ID
     * @param articleId 文章ID
     * @return 收藏记录数（0或1）
     */
    @Select("SELECT COUNT(*) FROM user_favorite WHERE user_id = #{userId} AND article_id = #{articleId}")
    int countByUserIdAndArticleId(@Param("userId") Long userId, @Param("articleId") Long articleId);

    /**
     * 获取用户的所有收藏夹名称
     * @param userId 用户ID
     * @return 收藏夹名称列表
     */
    @Select("SELECT DISTINCT folder_name FROM user_favorite WHERE user_id = #{userId} ORDER BY folder_name")
    List<String> getFolderNamesByUserId(@Param("userId") Long userId);
}
