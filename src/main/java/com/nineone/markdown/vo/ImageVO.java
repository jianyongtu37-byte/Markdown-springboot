package com.nineone.markdown.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 图片展示对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageVO {

    /**
     * 图片 ID
     */
    private Long id;

    /**
     * 原始文件名
     */
    private String originalName;

    /**
     * 图片访问URL
     */
    private String url;

    /**
     * 缩略图URL
     */
    private String thumbnailUrl;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 图片宽度
     */
    private Integer width;

    /**
     * 图片高度
     */
    private Integer height;

    /**
     * MIME类型
     */
    private String mimeType;

    /**
     * 上传时间
     */
    private LocalDateTime createTime;
}
