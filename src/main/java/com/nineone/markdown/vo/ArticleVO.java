package com.nineone.markdown.vo;

import com.nineone.markdown.enums.ArticleStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章详情展示对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleVO {

    /**
     * 文章ID
     */
    private Long id;

    /**
     * 作者ID
     */
    private Long userId;

    /**
     * 作者昵称
     */
    private String authorName;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 分类名称
     */
    private String categoryName;

    /**
     * 文章标题
     */
    private String title;

    /**
     * Markdown内容
     */
    private String content;

    /**
     * 视频URL
     */
    private String videoUrl;

    /**
     * AI摘要
     */
    private String summary;

    /**
     * AI摘要状态: 0-未生成, 1-生成中, 2-已生成, 3-生成失败
     */
    private Integer aiStatus;

    /**
     * 文章状态：使用枚举 DRAFT(0), PRIVATE(1), PUBLIC(2)
     */
    private ArticleStatusEnum status;

    /**
     * 阅读量
     */
    private Integer viewCount;

    /**
     * 允许他人导出：0-禁止，1-允许
     */
    private Integer allowExport;

    /**
     * 标签列表
     */
    private List<TagVO> tags;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
