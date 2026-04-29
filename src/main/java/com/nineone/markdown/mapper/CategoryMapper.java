package com.nineone.markdown.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nineone.markdown.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 分类表 Mapper 接口
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
    
    /**
     * 批量查询分类（忽略数据权限检查）
     * 用于解决 DataPermissionInterceptor 在处理 selectBatchIds 时的bug
     */
    @Select("<script>" +
            "SELECT * FROM category WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    @com.baomidou.mybatisplus.annotation.InterceptorIgnore(dataPermission = "true")
    List<Category> selectBatchIdsIgnorePermission(@Param("ids") List<Long> ids);

    /**
     * 检查分类名称是否存在（忽略数据权限检查）
     * 用于创建/更新分类时检查名称重复，需要查看所有用户的分类
     */
    @Select("SELECT * FROM category WHERE name = #{name} LIMIT 1")
    @com.baomidou.mybatisplus.annotation.InterceptorIgnore(dataPermission = "true")
    Category selectByNameIgnorePermission(@Param("name") String name);

    /**
     * 获取用户最大排序值（包含数据权限检查）
     * 用于获取当前用户的最大排序值，使用原生SQL避免MyBatis-Plus拦截器bug
     */
    @Select("SELECT * FROM category WHERE (user_id = 0 OR user_id = #{userId}) ORDER BY sort_order DESC LIMIT 1")
    Category selectMaxSortOrderForUser(@Param("userId") Long userId);
}
