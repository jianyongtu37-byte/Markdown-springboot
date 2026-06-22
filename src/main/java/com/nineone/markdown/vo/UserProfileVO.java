package com.nineone.markdown.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileVO {
    private Long id;
    private String username;
    private String nickname;
    private Integer articleCount;
    private Long totalLikes;
    private Integer followerCount;
    private Integer followingCount;
    private LocalDateTime createTime;
}
