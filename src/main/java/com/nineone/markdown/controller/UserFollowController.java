package com.nineone.markdown.controller;

import com.nineone.common.result.PageResult;
import com.nineone.common.result.Result;
import com.nineone.markdown.service.UserFollowService;
import com.nineone.markdown.util.UserContextHolder;
import com.nineone.markdown.vo.ArticleVO;
import com.nineone.markdown.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserFollowController {

    private final UserFollowService userFollowService;

    @PostMapping("/{id}/follow")
    public Result<Boolean> follow(@PathVariable("id") Long followeeId) {
        Long userId = UserContextHolder.requireUserId();
        boolean result = userFollowService.follow(userId, followeeId);
        return Result.success(result ? "关注成功" : "已关注", result);
    }

    @PostMapping("/{id}/unfollow")
    public Result<Void> unfollow(@PathVariable("id") Long followeeId) {
        Long userId = UserContextHolder.requireUserId();
        userFollowService.unfollow(userId, followeeId);
        return Result.success("已取消关注", null);
    }

    @GetMapping("/{id}/follow/status")
    public Result<Boolean> followStatus(@PathVariable("id") Long userId) {
        Long currentUserId = UserContextHolder.requireUserId();
        boolean following = userFollowService.isFollowing(currentUserId, userId);
        return Result.success(following);
    }

    @GetMapping("/{id}/followers")
    public Result<List<UserVO>> followers(@PathVariable("id") Long userId) {
        List<UserVO> list = userFollowService.getFollowers(userId);
        return Result.success(list);
    }

    @GetMapping("/{id}/following")
    public Result<List<UserVO>> following(@PathVariable("id") Long userId) {
        List<UserVO> list = userFollowService.getFollowing(userId);
        return Result.success(list);
    }

    @GetMapping("/following/articles")
    public Result<PageResult<ArticleVO>> followingArticles(
            @RequestParam(value = "page", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "size", defaultValue = "10") Integer pageSize) {
        Long userId = UserContextHolder.requireUserId();
        PageResult<ArticleVO> result = userFollowService.getFollowingArticles(userId, pageNum, pageSize);
        return Result.success(result);
    }
}
