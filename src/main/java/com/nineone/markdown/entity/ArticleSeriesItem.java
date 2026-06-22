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
@TableName("article_series_item")
public class ArticleSeriesItem {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField(value = "series_id")
    private Long seriesId;

    @TableField(value = "article_id")
    private Long articleId;

    @TableField(value = "sort_order")
    private Integer sortOrder;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
