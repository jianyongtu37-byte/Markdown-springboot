package com.nineone.markdown.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nineone.markdown.entity.Article;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.nineone.markdown.dto.ArticleDetailDTO;
import java.util.Map;

/**
 * 文章核心表 Mapper 接口
 */
@Mapper
public interface ArticleMapper extends BaseMapper<Article> {

    /**
     * 分页查询文章（忽略数据权限拦截器）
     * 用于解决分页插件与数据权限拦截器冲突导致的参数绑定问题
     * @param page 分页参数
     * @param queryWrapper 查询条件（在Service层已经包含了权限过滤）
     * @return 分页结果
     */
    @Select("SELECT * FROM article ${ew.customSqlSegment}")
    @com.baomidou.mybatisplus.annotation.InterceptorIgnore(dataPermission = "true")
    IPage<Article> selectPageIgnorePermission(@Param("page") Page<Article> page, @Param("ew") Wrapper<Article> queryWrapper);

    /**
     * 根据 ID 查询文章（忽略数据权限拦截器）
     * 用于公开文章详情查看，避免数据权限拦截器强制注入 user_id 条件
     * 注意：逻辑删除条件仍然生效（deleted=0），需要在业务层自行判断可见性
     * @param id 文章ID
     * @return 文章实体
     */
    @Select("SELECT * FROM article WHERE id = #{id} AND deleted = 0")
    @com.baomidou.mybatisplus.annotation.InterceptorIgnore(dataPermission = "true")
    Article selectByIdIgnorePermission(@Param("id") Long id);

    /**
     * 原子更新文章阅读量（view_count = view_count + 1）
     * 使用 SQL 层面的原子操作，避免并发问题
     * @param id 文章ID
     */
    @Update("UPDATE article SET view_count = view_count + 1 WHERE id = #{id} AND deleted = 0")
    @com.baomidou.mybatisplus.annotation.InterceptorIgnore(dataPermission = "true")
    void updateViewCount(@Param("id") Long id);

    /**
     * 原子更新文章点赞数（like_count = like_count + delta）
     * 使用 SQL 层面的原子操作，避免先查后改的并发问题
     * @param id 文章ID
     * @param delta 变化量（+1 或 -1）
     */
    @Update("UPDATE article SET like_count = GREATEST(0, like_count + #{delta}) WHERE id = #{id} AND deleted = 0")
    @com.baomidou.mybatisplus.annotation.InterceptorIgnore(dataPermission = "true")
    void updateLikeCount(@Param("id") Long id, @Param("delta") int delta);

    /**
     * 原子更新文章收藏数（favorite_count = favorite_count + delta）
     * 使用 SQL 层面的原子操作，避免先查后改的并发问题
     * @param id 文章ID
     * @param delta 变化量（+1 或 -1）
     */
    @Update("UPDATE article SET favorite_count = GREATEST(0, favorite_count + #{delta}) WHERE id = #{id} AND deleted = 0")
    @com.baomidou.mybatisplus.annotation.InterceptorIgnore(dataPermission = "true")
    void updateFavoriteCount(@Param("id") Long id, @Param("delta") int delta);

    /**
     * 原子更新文章评论数（comment_count = comment_count + delta）
     * 使用 SQL 层面的原子操作，避免先查后改的并发问题
     * @param id 文章ID
     * @param delta 变化量（+1 或 -1）
     */
    @Update("UPDATE article SET comment_count = GREATEST(0, comment_count + #{delta}) WHERE id = #{id} AND deleted = 0")
    @com.baomidou.mybatisplus.annotation.InterceptorIgnore(dataPermission = "true")
    void updateCommentCount(@Param("id") Long id, @Param("delta") int delta);

    /**
     * 仅查询文章的点赞数字段，避免查询整行数据
     * 用于 getLikeCount 等只需要计数的方法
     * @param id 文章ID
     * @return 点赞数
     */
    @Select("SELECT like_count FROM article WHERE id = #{id} AND deleted = 0")
    @com.baomidou.mybatisplus.annotation.InterceptorIgnore(dataPermission = "true")
    Integer selectLikeCountById(@Param("id") Long id);

    /**
     * 查询指定用户的文章统计数据（一次查询获取多个维度的统计）
     * 替代原来的 4 次 selectCount 调用，大幅减少数据库交互
     * @param userId 用户ID
     * @return 包含 total, draft, private, public 计数的 Map
     */
    @Select("SELECT " +
            "COUNT(*) AS total, " +
            "SUM(CASE WHEN `status` = 0 THEN 1 ELSE 0 END) AS draft, " +
            "SUM(CASE WHEN `status` = 1 THEN 1 ELSE 0 END) AS `private`, " +
            "SUM(CASE WHEN `status` = 2 THEN 1 ELSE 0 END) AS `public` " +
            "FROM article WHERE user_id = #{userId} AND deleted = 0")
    @com.baomidou.mybatisplus.annotation.InterceptorIgnore(dataPermission = "true")
    Map<String, Object> selectArticleStatsByUserId(@Param("userId") Long userId);

    /**
     * 一次性 JOIN 查询文章详情（替代原来的 7 次独立查询）
     * 通过 LEFT JOIN 一次查出：文章 + 作者昵称 + 分类名称 + 视频信息 + 标签聚合
     * 数据库层面仅 1 次网络往返，大幅减少 IO 开销
     *
     * @param id 文章ID
     * @return 文章详情 DTO（包含所有关联信息）
     */
    @Select("SELECT " +
            "a.id, a.user_id, a.category_id, a.title, a.content, a.video_url, " +
            "a.summary, a.ai_status, a.`status`, a.view_count, a.allow_export, " +
            "a.like_count, a.comment_count, a.favorite_count, " +
            "a.create_time, a.update_time, " +
            "u.nickname AS author_name, " +
            "c.name AS category_name, " +
            "v.id AS video_id, v.video_source, v.video_id AS video_video_id, " +
            "v.duration AS video_duration, v.create_time AS video_create_time, " +
            "v.update_time AS video_update_time, " +
            "GROUP_CONCAT(DISTINCT t.id ORDER BY t.id SEPARATOR ',') AS tag_ids, " +
            "GROUP_CONCAT(DISTINCT t.name ORDER BY t.id SEPARATOR ',') AS tag_names " +
            "FROM article a " +
            "LEFT JOIN sys_user u ON a.user_id = u.id " +
            "LEFT JOIN category c ON a.category_id = c.id " +
            "LEFT JOIN article_video v ON v.article_id = a.id " +
            "LEFT JOIN article_tag at ON at.article_id = a.id " +
            "LEFT JOIN tag t ON t.id = at.tag_id " +
            "WHERE a.id = #{id} AND a.deleted = 0 " +
            "GROUP BY a.id")
    @com.baomidou.mybatisplus.annotation.InterceptorIgnore(dataPermission = "true")
    ArticleDetailDTO selectArticleDetailById(@Param("id") Long id);
}
