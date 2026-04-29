package com.nineone.markdown.dto;

import com.nineone.markdown.enums.ArticleStatusEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章详情 JOIN 查询结果 DTO
 * 用于接收 ArticleMapper.selectArticleDetailById() 的 LEFT JOIN 结果
 * 一次性查出文章 + 作者昵称 + 分类名称 + 视频信息 + 标签聚合
 */
@Data
public class ArticleDetailDTO {

    // ====== article 表字段 ======
    private Long id;
    private Long userId;
    private Long categoryId;
    private String title;
    private String content;
    private String videoUrl;
    private String summary;
    private Integer aiStatus;
    private ArticleStatusEnum status;
    private Integer viewCount;
    private Integer allowExport;
    private Integer likeCount;
    private Integer commentCount;
    private Integer favoriteCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // ====== sys_user 表字段（LEFT JOIN） ======
    private String authorName;

    // ====== category 表字段（LEFT JOIN） ======
    private String categoryName;

    // ====== article_video 表字段（LEFT JOIN） ======
    private Long videoId;
    private String videoSource;
    private String videoVideoId;
    private Integer videoDuration;
    private LocalDateTime videoCreateTime;
    private LocalDateTime videoUpdateTime;

    // ====== 标签聚合（子查询/GROUP_CONCAT） ======
    private String tagIds;
    private String tagNames;
}
