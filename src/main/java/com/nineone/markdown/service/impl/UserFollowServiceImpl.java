package com.nineone.markdown.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nineone.common.result.PageResult;
import com.nineone.markdown.entity.Article;
import com.nineone.markdown.entity.User;
import com.nineone.markdown.entity.UserFollow;
import com.nineone.markdown.enums.ArticleStatusEnum;
import com.nineone.markdown.exception.BizException;
import com.nineone.markdown.mapper.*;
import com.nineone.markdown.service.NotificationService;
import com.nineone.markdown.service.UserFollowService;
import com.nineone.markdown.util.UserContextHolder;
import com.nineone.markdown.vo.ArticleVO;
import com.nineone.markdown.vo.TagVO;
import com.nineone.markdown.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserFollowServiceImpl implements UserFollowService {

    private final UserFollowMapper userFollowMapper;
    private final UserMapper userMapper;
    private final ArticleMapper articleMapper;
    private final ArticleTagMapper articleTagMapper;
    private final TagMapper tagMapper;
    private final CategoryMapper categoryMapper;
    private final NotificationService notificationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean follow(Long followerId, Long followeeId) {
        if (followerId.equals(followeeId)) {
            throw new BizException("不能关注自己");
        }

        User followee = userMapper.selectById(followeeId);
        if (followee == null) {
            throw new BizException("用户不存在");
        }

        if (userFollowMapper.countByFollowerAndFollowee(followerId, followeeId) > 0) {
            return false;
        }

        UserFollow follow = UserFollow.builder()
                .followerId(followerId)
                .followeeId(followeeId)
                .build();
        userFollowMapper.insert(follow);

        User follower = UserContextHolder.getCurrentUser();
        String followerName = follower != null ? follower.getNickname() : null;
        if (followerName == null) {
            followerName = userMapper.selectById(followerId).getNickname();
        }
        notificationService.createNotification(followeeId, "FOLLOW",
                "新关注", followerName + " 关注了你",
                null, followerId, followerName);

        log.info("用户{}关注了用户{}", followerId, followeeId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unfollow(Long followerId, Long followeeId) {
        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFollow::getFollowerId, followerId)
                .eq(UserFollow::getFolloweeId, followeeId);
        int deleted = userFollowMapper.delete(wrapper);
        if (deleted > 0) {
            log.info("用户{}取消关注了用户{}", followerId, followeeId);
            return true;
        }
        return false;
    }

    @Override
    public boolean isFollowing(Long followerId, Long followeeId) {
        return userFollowMapper.countByFollowerAndFollowee(followerId, followeeId) > 0;
    }

    @Override
    public List<UserVO> getFollowers(Long userId) {
        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFollow::getFolloweeId, userId)
                .orderByDesc(UserFollow::getCreateTime);
        List<UserFollow> follows = userFollowMapper.selectList(wrapper);
        return buildUserVOList(follows, true);
    }

    @Override
    public List<UserVO> getFollowing(Long userId) {
        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFollow::getFollowerId, userId)
                .orderByDesc(UserFollow::getCreateTime);
        List<UserFollow> follows = userFollowMapper.selectList(wrapper);
        return buildUserVOList(follows, false);
    }

    @Override
    public PageResult<ArticleVO> getFollowingArticles(Long userId, Integer pageNum, Integer pageSize) {
        List<Long> followeeIds = userFollowMapper.selectFolloweeIds(userId);
        if (followeeIds.isEmpty()) {
            return PageResult.of(pageNum, pageSize, 0L, new ArrayList<>());
        }

        LambdaQueryWrapper<Article> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(Article::getUserId, followeeIds)
                .eq(Article::getStatus, ArticleStatusEnum.PUBLIC)
                .eq(Article::getDeleted, 0)
                .orderByDesc(Article::getIsPinned)
                .orderByDesc(Article::getPinnedTime)
                .orderByDesc(Article::getCreateTime);

        Page<Article> page = new Page<>(pageNum, pageSize);
        IPage<Article> articlePage = articleMapper.selectPageIgnorePermission(page, queryWrapper);

        List<Article> articles = articlePage.getRecords();
        if (articles.isEmpty()) {
            return PageResult.of(pageNum, pageSize, articlePage.getTotal(), new ArrayList<>());
        }

        List<Long> articleIds = articles.stream().map(Article::getId).collect(Collectors.toList());
        List<Long> userIds = articles.stream().map(Article::getUserId).distinct().collect(Collectors.toList());
        List<Long> categoryIds = articles.stream().map(Article::getCategoryId).filter(Objects::nonNull).distinct().collect(Collectors.toList());

        Map<Long, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(userIds);
            for (User u : users) userMap.put(u.getId(), u);
        }

        List<ArticleVO> result = new ArrayList<>();
        for (Article article : articles) {
            User author = userMap.get(article.getUserId());
            String authorName = author != null ? author.getNickname() : null;

            List<TagVO> tags = getTagsByArticleId(article.getId());

            ArticleVO vo = ArticleVO.builder()
                    .id(article.getId())
                    .userId(article.getUserId())
                    .authorName(authorName)
                    .authorAvatar(author != null ? author.getAvatar() : null)
                    .categoryId(article.getCategoryId())
                    .title(article.getTitle())
                    .content(article.getContent())
                    .videoUrl(article.getVideoUrl())
                    .summary(article.getSummary())
                    .aiStatus(article.getAiStatus())
                    .status(article.getStatus())
                    .viewCount(article.getViewCount())
                    .allowExport(article.getAllowExport())
                    .isPinned(article.getIsPinned())
                    .pinnedTime(article.getPinnedTime())
                    .tags(tags)
                    .createTime(article.getCreateTime())
                    .updateTime(article.getUpdateTime())
                    .build();
            result.add(vo);
        }

        return PageResult.of(pageNum, pageSize, articlePage.getTotal(), result);
    }

    private List<UserVO> buildUserVOList(List<UserFollow> follows, boolean isFollowers) {
        if (follows.isEmpty()) return new ArrayList<>();
        List<Long> userIds = follows.stream()
                .map(f -> isFollowers ? f.getFollowerId() : f.getFolloweeId())
                .distinct()
                .collect(Collectors.toList());
        List<User> users = userMapper.selectBatchIds(userIds);
        Map<Long, User> userMap = new HashMap<>();
        for (User u : users) userMap.put(u.getId(), u);

        return userIds.stream()
                .map(userMap::get)
                .filter(Objects::nonNull)
                .map(u -> UserVO.builder()
                        .id(u.getId())
                        .username(u.getUsername())
                        .nickname(u.getNickname())
                        .createTime(u.getCreateTime())
                        .updateTime(u.getUpdateTime())
                        .build())
                .collect(Collectors.toList());
    }

    private List<TagVO> getTagsByArticleId(Long articleId) {
        LambdaQueryWrapper<com.nineone.markdown.entity.ArticleTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(com.nineone.markdown.entity.ArticleTag::getArticleId, articleId);
        List<com.nineone.markdown.entity.ArticleTag> articleTags = articleTagMapper.selectList(wrapper);
        if (articleTags.isEmpty()) return new ArrayList<>();
        List<Long> tagIds = articleTags.stream()
                .map(com.nineone.markdown.entity.ArticleTag::getTagId)
                .collect(Collectors.toList());
        List<com.nineone.markdown.entity.Tag> tags = tagMapper.selectBatchIds(tagIds);
        return tags.stream()
                .map(t -> TagVO.builder().id(t.getId()).name(t.getName()).createTime(t.getCreateTime()).build())
                .collect(Collectors.toList());
    }
}
