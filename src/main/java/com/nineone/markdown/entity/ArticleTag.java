package com.nineone.markdown.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文章-标签关联表实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("article_tag")
public class ArticleTag {

    /**
     * 关联 article 表的主键
     */
    @TableId(value = "article_id")
    private Long articleId;

    /**
     * 关联 tag 表的主键
     */
    @TableField(value = "tag_id")
    private Long tagId;
}
