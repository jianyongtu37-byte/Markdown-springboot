package com.nineone.markdown.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nineone.markdown.entity.User;
import com.nineone.markdown.vo.UserVO;

import java.util.List;

/**
 * 用户服务接口
 */
public interface UserService extends IService<User> {

    /**
     * 根据ID获取用户详情
     * @param id 用户ID
     * @return 用户实体
     */
    User getUserById(Long id);

    /**
     * 根据用户名获取用户
     * @param username 用户名
     * @return 用户实体
     */
    User getUserByUsername(String username);

    /**
     * 根据邮箱获取用户
     * @param email 邮箱
     * @return 用户实体
     */
    User getUserByEmail(String email);

    /**
     * 更新用户信息
     * @param user 用户实体
     * @return 是否更新成功
     */
    boolean updateUser(User user);

    /**
     * 更新用户密码
     * @param userId 用户ID
     * @param oldPassword 原密码
     * @param newPassword 新密码
     * @return 是否更新成功
     */
    boolean updatePassword(Long userId, String oldPassword, String newPassword);

    /**
     * 重置用户密码（管理员功能）
     * @param userId 用户ID
     * @param newPassword 新密码
     * @return 是否重置成功
     */
    boolean resetPassword(Long userId, String newPassword);

    /**
     * 获取所有用户列表
     * @return 用户列表
     */
    List<UserVO> getAllUsers();

    /**
     * 搜索用户
     * @param keyword 关键词（用户名、昵称、邮箱）
     * @return 匹配的用户列表
     */
    List<UserVO> searchUsers(String keyword);

    /**
     * 获取用户统计信息
     * @return 用户数量
     */
    Long getUserCount();
}