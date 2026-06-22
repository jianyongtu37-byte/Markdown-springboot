package com.nineone.markdown.service;

import com.nineone.common.result.PageResult;
import com.nineone.markdown.vo.ArticleVO;
import com.nineone.markdown.vo.UserVO;

import java.util.List;

public interface UserFollowService {

    boolean follow(Long followerId, Long followeeId);

    boolean unfollow(Long followerId, Long followeeId);

    boolean isFollowing(Long followerId, Long followeeId);

    List<UserVO> getFollowers(Long userId);

    List<UserVO> getFollowing(Long userId);

    PageResult<ArticleVO> getFollowingArticles(Long userId, Integer pageNum, Integer pageSize);
}
