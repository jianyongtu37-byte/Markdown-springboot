package com.nineone.markdown.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nineone.markdown.entity.Article;
import com.nineone.markdown.entity.User;
import com.nineone.markdown.mapper.ArticleMapper;
import com.nineone.markdown.mapper.UserMapper;
import com.nineone.markdown.service.UserService;
import com.nineone.markdown.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户服务实现类
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final UserMapper userMapper;
    private final ArticleMapper articleMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public User getUserById(Long id) {
        Assert.notNull(id, "用户ID不能为空");
        return userMapper.selectById(id);
    }

    @Override
    public User getUserByUsername(String username) {
        Assert.hasText(username, "用户名不能为空");
        
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, username);
        return userMapper.selectOne(queryWrapper);
    }

    @Override
    public User getUserByEmail(String email) {
        Assert.hasText(email, "邮箱不能为空");
        
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getEmail, email);
        return userMapper.selectOne(queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateUser(User user) {
        Assert.notNull(user, "用户不能为空");
        Assert.notNull(user.getId(), "用户ID不能为空");
        
        // 检查用户是否存在
        User existingUser = userMapper.selectById(user.getId());
        if (existingUser == null) {
            return false;
        }
        
        // 如果邮箱有变化，检查新邮箱是否已被使用
        if (user.getEmail() != null && !user.getEmail().equals(existingUser.getEmail())) {
            User emailUser = getUserByEmail(user.getEmail());
            if (emailUser != null) {
                throw new IllegalArgumentException("邮箱已被使用");
            }
        }
        
        // 保留用户名和密码字段（不能通过此接口修改）
        user.setUsername(existingUser.getUsername());
        user.setPassword(existingUser.getPassword());
        
        // 更新用户信息
        int updateCount = userMapper.updateById(user);
        return updateCount > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updatePassword(Long userId, String oldPassword, String newPassword) {
        Assert.notNull(userId, "用户ID不能为空");
        Assert.hasText(oldPassword, "原密码不能为空");
        Assert.hasText(newPassword, "新密码不能为空");
        
        // 获取用户信息
        User user = userMapper.selectById(userId);
        if (user == null) {
            return false;
        }
        
        // 验证原密码
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            return false;
        }
        
        // 更新密码
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdateTime(LocalDateTime.now());
        
        int updateCount = userMapper.updateById(user);
        return updateCount > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean resetPassword(Long userId, String newPassword) {
        Assert.notNull(userId, "用户ID不能为空");
        Assert.hasText(newPassword, "新密码不能为空");
        
        // 获取用户信息
        User user = userMapper.selectById(userId);
        if (user == null) {
            return false;
        }
        
        // 重置密码
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdateTime(LocalDateTime.now());
        
        int updateCount = userMapper.updateById(user);
        return updateCount > 0;
    }

    @Override
    public List<UserVO> getAllUsers() {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByDesc(User::getCreateTime);
        List<User> users = userMapper.selectList(queryWrapper);
        
        // 转换为VO
        return users.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    public List<UserVO> searchUsers(String keyword) {
        Assert.hasText(keyword, "搜索关键词不能为空");
        
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.and(wrapper -> wrapper
                .like(User::getUsername, keyword)
                .or()
                .like(User::getNickname, keyword)
                .or()
                .like(User::getEmail, keyword));
        queryWrapper.orderByDesc(User::getCreateTime);
        List<User> users = userMapper.selectList(queryWrapper);
        
        // 转换为VO
        return users.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    public Long getUserCount() {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        return userMapper.selectCount(queryWrapper);
    }

    /**
     * 将User实体转换为UserVO
     */
    private UserVO convertToVO(User user) {
        if (user == null) {
            return null;
        }
        
        // 统计用户文章数量
        LambdaQueryWrapper<Article> articleQuery = new LambdaQueryWrapper<>();
        articleQuery.eq(Article::getUserId, user.getId());
        Long articleCount = articleMapper.selectCount(articleQuery);
        
        // 假设最近活动时间为更新时间（实际项目中可能需要单独记录）
        LocalDateTime lastActiveTime = user.getUpdateTime();
        
        return UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .createTime(user.getCreateTime())
                .updateTime(user.getUpdateTime())
                .articleCount(articleCount != null ? articleCount.intValue() : 0)
                .lastActiveTime(lastActiveTime)
                .build();
    }
}