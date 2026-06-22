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
@TableName("article_collaborator")
public class ArticleCollaborator {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField(value = "article_id")
    private Long articleId;

    @TableField(value = "user_id")
    private Long userId;

    @TableField(value = "permission")
    private String permission;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
