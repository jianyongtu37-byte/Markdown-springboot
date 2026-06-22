package com.nineone.markdown.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_follow")
public class UserFollow {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField(value = "follower_id")
    private Long followerId;

    @TableField(value = "followee_id")
    private Long followeeId;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
