package com.nineone.markdown.dto;

import com.nineone.markdown.enums.ArticleStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * 创建文章请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleCreateDTO {

    /**
     * 分类 ID（可选，传错会自动兜底到未分类）
     */
    private Long categoryId;

    /**
     * 文章标题
     */
    @NotBlank(message = "文章标题不能为空")
    private String title;

    /**
     * Markdown 内容
     */
    @NotBlank(message = "文章内容不能为空")
    private String content;

    /**
     * 视频URL（可为空）
     */
    private String videoUrl;

    /**
     * AI 摘要状态（默认0-未生成）
     */
    private Integer aiStatus = 0;

    /**
     * 文章状态枚举：0-草稿(DRAFT), 1-仅自己可见(PRIVATE), 2-公开可见(PUBLIC)（默认0-草稿）
     */
    private ArticleStatusEnum status = ArticleStatusEnum.DRAFT;

    /**
     * 允许他人导出：0-禁止，1-允许（默认允许）
     */
    private Integer allowExport = 1;

    /**
     * ✅ 改为接收标签名称列表（前端直接传字符串数组）
     * 无需提前创建标签，系统自动即写即存
     */
    private List<String> tagNames;
}
