package com.nineone.markdown.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 图片资源实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("image")
public class Image {

    /**
     * 图片 ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 上传用户 ID
     */
    @TableField(value = "user_id")
    private Long userId;

    /**
     * 关联文章 ID（可为空）
     */
    @TableField(value = "article_id")
    private Long articleId;

    /**
     * 原始文件名
     */
    @TableField(value = "original_name")
    private String originalName;

    /**
     * 存储路径（本地路径或云存储URL）
     */
    @TableField(value = "storage_path")
    private String storagePath;

    /**
     * 缩略图路径
     */
    @TableField(value = "thumbnail_path")
    private String thumbnailPath;

    /**
     * 文件大小（字节）
     */
    @TableField(value = "file_size")
    private Long fileSize;

    /**
     * 图片宽度
     */
    @TableField(value = "width")
    private Integer width;

    /**
     * 图片高度
     */
    @TableField(value = "height")
    private Integer height;

    /**
     * MIME类型
     */
    @TableField(value = "mime_type")
    private String mimeType;

    /**
     * 存储类型：local-本地, oss-阿里云OSS, cos-腾讯云COS
     */
    @TableField(value = "storage_type")
    private String storageType;

    /**
     * 上传时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
