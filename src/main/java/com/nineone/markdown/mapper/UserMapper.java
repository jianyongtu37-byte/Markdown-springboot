package com.nineone.markdown.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nineone.markdown.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 用户表 Mapper 接口
 * <p>
 * 🔥 调试说明：
 * 如果遇到"满屏重复查询 sys_user"的问题，请取消下面 {@link #selectByIdWithTrace(Long)} 的注释，
 * 并将 {@link #selectById(Long)} 注释掉，然后重新请求接口。
 * 控制台会打印每次查询的调用栈，精准定位"是谁在反复查用户"。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 🔥【抓鬼行动】带调用栈追踪的 selectById
     * <p>
     * 使用方法：
     * 1. 取消本方法的 @Select 注释
     * 2. 注释掉父类的 selectById（通过覆盖实现）
     * 3. 重新请求接口，查看控制台输出的调用栈
     * 4. 根据调用栈前几行，找到具体是哪个类、哪行代码在反复查用户
     * <p>
     * 常见"内鬼"位置：
     * - JwtAuthenticationFilter.doFilterInternal() — Filter 层查用户
     * - ArticleServiceImpl.getArticleDetail() — 查作者信息
     * - InteractionServiceImpl.getCurrentUserNickname() — 查用户昵称
     * - NotificationServiceImpl 中的各种查询
     * - 各种 Controller 中的 getCurrentUserId() 调用链
     */
    /*
    @Select("SELECT * FROM sys_user WHERE id = #{id}")
    User selectByIdWithTrace(Long id);

    @Override
    default User selectById(Long id) {
        if (id == null) return null;
        // 🔥 打印调用栈，精准定位调用方
        System.err.println("========================================");
        System.err.println("🔥【抓鬼行动】正在查询用户 ID=" + id + "，调用栈如下：");
        new RuntimeException("调用栈追踪").printStackTrace();
        System.err.println("========================================");
        return selectByIdWithTrace(id);
    }
    */
}
