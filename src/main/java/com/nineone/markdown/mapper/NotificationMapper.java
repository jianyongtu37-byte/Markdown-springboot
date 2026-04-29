package com.nineone.markdown.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nineone.markdown.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 通知 Mapper 接口
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    /**
     * 获取用户的未读通知列表
     * @param userId 用户ID
     * @return 未读通知列表
     */
    @Select("SELECT * FROM notification WHERE user_id = #{userId} AND is_read = 0 ORDER BY create_time DESC")
    List<Notification> findUnreadByUserId(@Param("userId") Long userId);

    /**
     * 获取用户的所有通知列表
     * @param userId 用户ID
     * @return 通知列表
     */
    @Select("SELECT * FROM notification WHERE user_id = #{userId} ORDER BY create_time DESC")
    List<Notification> findByUserId(@Param("userId") Long userId);

    /**
     * 获取用户的未读通知数量
     * @param userId 用户ID
     * @return 未读通知数量
     */
    @Select("SELECT COUNT(*) FROM notification WHERE user_id = #{userId} AND is_read = 0")
    int countUnreadByUserId(@Param("userId") Long userId);

    /**
     * 将用户的所有通知标记为已读
     * @param userId 用户ID
     */
    @Update("UPDATE notification SET is_read = 1 WHERE user_id = #{userId} AND is_read = 0")
    void markAllAsRead(@Param("userId") Long userId);
}
