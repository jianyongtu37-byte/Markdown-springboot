package com.nineone.markdown.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.nineone.markdown.entity.Article;
import com.nineone.markdown.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 管理员专用Mapper
 * 用于演示如何绕过数据权限拦截器
 */
@Mapper
public interface AdminMapper extends BaseMapper<Article> {

    /**
     * 获取所有文章（忽略数据权限）
     * 使用 @InterceptorIgnore 注解跳过数据权限检查
     * 
     * 注意：需要 MyBatis-Plus 版本 >= 3.5.5
     * 添加依赖：mybatis-plus-extension
     */
    @com.baomidou.mybatisplus.annotation.InterceptorIgnore(dataPermission = "true")
    List<Article> selectAllArticlesIgnorePermission();

    /**
     * 获取所有分类（忽略数据权限）
     */
    @com.baomidou.mybatisplus.annotation.InterceptorIgnore(dataPermission = "true")
    List<Category> selectAllCategoriesIgnorePermission();

    /**
     * 获取用户数量统计（忽略数据权限）
     * 使用原生SQL查询示例
     */
    @Select("SELECT COUNT(*) as total, " +
            "SUM(CASE WHEN email_verified = 1 THEN 1 ELSE 0 END) as verified " +
            "FROM sys_user")
    @com.baomidou.mybatisplus.annotation.InterceptorIgnore(dataPermission = "true")
    UserStats getUserStats();

    /**
     * 内部类：用户统计结果
     */
    class UserStats {
        private Long total;
        private Long verified;

        public Long getTotal() {
            return total;
        }

        public void setTotal(Long total) {
            this.total = total;
        }

        public Long getVerified() {
            return verified;
        }

        public void setVerified(Long verified) {
            this.verified = verified;
        }

        public Double getVerifiedRate() {
            if (total == null || total == 0) {
                return 0.0;
            }
            return verified * 100.0 / total;
        }
    }
}