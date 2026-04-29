package com.nineone.markdown.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 备份记录实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("backup_record")
public class BackupRecord {

    /**
     * 备份 ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户 ID（系统自动备份时为 null）
     */
    @TableField(value = "user_id")
    private Long userId;

    /**
     * 备份类型：MANUAL-手动导出, AUTO-自动定时备份
     */
    @TableField(value = "backup_type")
    private String backupType;

    /**
     * 备份格式：PDF, WORD, MARKDOWN_ZIP, ALL
     */
    @TableField(value = "format")
    private String format;

    /**
     * 备份文件路径
     */
    @TableField(value = "file_path")
    private String filePath;

    /**
     * 备份文件大小（字节）
     */
    @TableField(value = "file_size")
    private Long fileSize;

    /**
     * 备份状态：PROCESSING-处理中, SUCCESS-成功, FAILED-失败
     */
    @TableField(value = "status")
    private String status;

    /**
     * 包含的文章数量
     */
    @TableField(value = "article_count")
    private Integer articleCount;

    /**
     * 错误信息（失败时记录）
     */
    @TableField(value = "error_message")
    private String errorMessage;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
