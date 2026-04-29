package com.nineone.markdown.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 文章保存请求DTO
 */
@Data
public class ArticleSaveDTO {
    /**
     * 文章ID，null表示新建
     */
    private Long id;

    /**
     * 文章标题
     */
    @NotBlank(message = "文章标题不能为空")
    private String title;

    /**
     * 文章内容
     */
    @NotBlank(message = "文章内容不能为空")
    private String content;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 视频URL（可为空）
     */
    private String videoUrl;

    /**
     * 允许他人导出：0-禁止，1-允许（默认允许）
     */
    private Integer allowExport = 1;

    /**
     * 标签ID列表
     */
    private List<Long> tagIds;
}
