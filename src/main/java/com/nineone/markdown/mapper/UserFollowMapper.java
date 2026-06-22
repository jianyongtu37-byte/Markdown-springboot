package com.nineone.markdown.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nineone.markdown.entity.UserFollow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserFollowMapper extends BaseMapper<UserFollow> {

    @Select("SELECT COUNT(*) FROM user_follow WHERE follower_id = #{followerId} AND followee_id = #{followeeId}")
    int countByFollowerAndFollowee(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    @Select("SELECT COUNT(*) FROM user_follow WHERE follower_id = #{userId}")
    int countFollowing(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM user_follow WHERE followee_id = #{userId}")
    int countFollowers(@Param("userId") Long userId);

    @Select("SELECT followee_id FROM user_follow WHERE follower_id = #{followerId}")
    List<Long> selectFolloweeIds(@Param("followerId") Long followerId);
}
