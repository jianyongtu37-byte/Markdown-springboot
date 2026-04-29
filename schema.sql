-- Markdown 知识库数据库建表脚本
-- 适用于 MySQL 8.x
-- 字符集: utf8mb4 (支持完整 Unicode，包括 Emoji)
-- 排序规则: utf8mb4_unicode_ci
CREATE DATABASE IF NOT EXISTS `markdown_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE markdown_db;

-- 1. 用户表 (sys_user)
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名（用于登录）',
    `password` VARCHAR(100) NOT NULL COMMENT '密码（BCrypt 加密后的哈希值）',
    `nickname` VARCHAR(50) NULL COMMENT '昵称',
    `email` VARCHAR(100) NULL COMMENT '邮箱',
    `email_verified` TINYINT(1) DEFAULT 0 COMMENT '邮箱验证状态：0-未验证，1-已验证',
    `verification_token` VARCHAR(255) NULL COMMENT '邮箱验证令牌',
    `verification_token_expiry` DATETIME NULL COMMENT '验证令牌过期时间',
    `email_verified_at` DATETIME NULL COMMENT '邮箱验证时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 2. 分类表 (category)
CREATE TABLE IF NOT EXISTS `category` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '分类 ID',
    `user_id` BIGINT DEFAULT 0 COMMENT '用户ID，0表示系统默认分类',
    `name` VARCHAR(50) NOT NULL COMMENT '分类名称',
    `description` VARCHAR(200) NULL COMMENT '分类描述',
    `sort_order` INT DEFAULT 0 COMMENT '排序字段（数字越小越靠前）',
    `is_default` TINYINT(1) DEFAULT 0 COMMENT '是否为系统默认分类（1-是，0-否）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name_user` (`name`, `user_id`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分类表';

-- 3. 标签表 (tag)
CREATE TABLE IF NOT EXISTS `tag` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '标签 ID',
    `name` VARCHAR(50) NOT NULL COMMENT '标签名称',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标签表';

-- 4. 文章核心表 (article)
CREATE TABLE IF NOT EXISTS `article` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '文章 ID',
    `user_id` BIGINT NOT NULL COMMENT '作者 ID',
    `category_id` BIGINT NULL COMMENT '分类 ID',
    `title` VARCHAR(150) NOT NULL COMMENT '文章标题',
    `content` LONGTEXT NOT NULL COMMENT 'Markdown 格式的纯文本内容',
    `video_url` VARCHAR(500) NULL COMMENT '视频URL',
    `summary` VARCHAR(500) NULL COMMENT 'AI 自动生成的摘要内容',
    `ai_status` TINYINT DEFAULT 0 COMMENT 'AI 摘要生成状态: 0-未生成, 1-生成中, 2-已生成, 3-生成失败',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '文章状态：0-草稿(DRAFT), 1-仅自己可见(PRIVATE), 2-公开可见(PUBLIC)',
    `view_count` INT DEFAULT 0 COMMENT '阅读量统计',
    `like_count` INT DEFAULT 0 COMMENT '点赞数',
    `comment_count` INT DEFAULT 0 COMMENT '评论数',
    `favorite_count` INT DEFAULT 0 COMMENT '收藏数',
    `deleted` TINYINT(1) DEFAULT 0 COMMENT '软删除标记：0-未删除，1-已删除',
    `allow_export` TINYINT(1) DEFAULT 1 COMMENT '允许他人导出：0-禁止，1-允许（默认允许）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_category_id` (`category_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_create_time` (`create_time`),
    INDEX `idx_deleted` (`deleted`),
    CONSTRAINT `fk_article_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_article_category` FOREIGN KEY (`category_id`) REFERENCES `category` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章核心表';

-- 5. 文章-标签关联表 (article_tag)
CREATE TABLE IF NOT EXISTS `article_tag` (
    `article_id` BIGINT NOT NULL COMMENT '关联 article 表的主键',
    `tag_id` BIGINT NOT NULL COMMENT '关联 tag 表的主键',
    PRIMARY KEY (`article_id`, `tag_id`),
    INDEX `idx_tag_id` (`tag_id`),
    CONSTRAINT `fk_article_tag_article` FOREIGN KEY (`article_id`) REFERENCES `article` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_article_tag_tag` FOREIGN KEY (`tag_id`) REFERENCES `tag` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章-标签关联表';

-- 6. 文章视频关联表 (article_video)
CREATE TABLE IF NOT EXISTS `article_video` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `article_id` BIGINT NOT NULL COMMENT '文章 ID',
    `video_url` VARCHAR(500) NULL COMMENT '视频URL',
    `video_source` VARCHAR(20) NULL COMMENT '视频来源：YOUTUBE / BILIBILI / LOCAL',
    `video_id` VARCHAR(100) NULL COMMENT '视频ID（YouTube videoId 或 BV号）',
    `duration` INT NULL COMMENT '视频总时长（秒）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_article_id` (`article_id`),
    INDEX `idx_article_id` (`article_id`),
    CONSTRAINT `fk_article_video_article` FOREIGN KEY (`article_id`) REFERENCES `article` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章视频关联表';

-- 7. 文章时间戳目录表 (article_timestamp)
CREATE TABLE IF NOT EXISTS `article_timestamp` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `article_id` BIGINT NOT NULL COMMENT '文章 ID',
    `label` VARCHAR(10) NOT NULL COMMENT '时间标签（如 "01:27"）',
    `seconds` INT NOT NULL COMMENT '时间戳对应的秒数（如 87）',
    `excerpt` VARCHAR(500) NULL COMMENT '时间点对应的内容摘要',
    `line_no` INT NULL COMMENT '在文章内容中的行号',
    PRIMARY KEY (`id`),
    INDEX `idx_article_id` (`article_id`),
    INDEX `idx_seconds` (`seconds`),
    CONSTRAINT `fk_article_timestamp_article` FOREIGN KEY (`article_id`) REFERENCES `article` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章时间戳目录表';

-- =============================================
-- 🆕 高优先级功能新增表
-- =============================================

-- 8. 文章版本历史表 (article_version)
CREATE TABLE IF NOT EXISTS `article_version` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '版本 ID',
    `article_id` BIGINT NOT NULL COMMENT '文章 ID',
    `version` INT NOT NULL COMMENT '版本号（从1开始递增）',
    `title` VARCHAR(150) NOT NULL COMMENT '版本标题',
    `content` LONGTEXT NOT NULL COMMENT '版本内容（Markdown格式）',
    `summary` VARCHAR(500) NULL COMMENT '版本摘要',
    `change_note` VARCHAR(500) NULL COMMENT '修改备注',
    `operator_id` BIGINT NULL COMMENT '修改者ID',
    `operator_name` VARCHAR(50) NULL COMMENT '修改者名称',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '版本创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_article_id` (`article_id`),
    INDEX `idx_version` (`article_id`, `version`),
    CONSTRAINT `fk_article_version_article` FOREIGN KEY (`article_id`) REFERENCES `article` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章版本历史表';

-- 9. 文章点赞表 (article_like)
CREATE TABLE IF NOT EXISTS `article_like` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `article_id` BIGINT NOT NULL COMMENT '文章 ID',
    `user_id` BIGINT NOT NULL COMMENT '用户 ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_article_user` (`article_id`, `user_id`),
    INDEX `idx_article_id` (`article_id`),
    INDEX `idx_user_id` (`user_id`),
    CONSTRAINT `fk_like_article` FOREIGN KEY (`article_id`) REFERENCES `article` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_like_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章点赞表';

-- 10. 用户收藏表 (user_favorite)
CREATE TABLE IF NOT EXISTS `user_favorite` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `user_id` BIGINT NOT NULL COMMENT '用户 ID',
    `article_id` BIGINT NOT NULL COMMENT '文章 ID',
    `folder_name` VARCHAR(50) NULL DEFAULT '默认收藏夹' COMMENT '收藏夹名称',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_article` (`user_id`, `article_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_article_id` (`article_id`),
    INDEX `idx_folder_name` (`user_id`, `folder_name`),
    CONSTRAINT `fk_favorite_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_favorite_article` FOREIGN KEY (`article_id`) REFERENCES `article` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户收藏表';

-- 11. 评论表 (article_comment)
CREATE TABLE IF NOT EXISTS `article_comment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '评论 ID',
    `article_id` BIGINT NOT NULL COMMENT '文章 ID',
    `user_id` BIGINT NOT NULL COMMENT '评论用户 ID',
    `parent_id` BIGINT NULL COMMENT '父评论ID（支持二级回复，为NULL表示一级评论）',
    `reply_to_user_id` BIGINT NULL COMMENT '回复的目标用户ID',
    `reply_to_username` VARCHAR(50) NULL COMMENT '回复的目标用户名',
    `content` TEXT NOT NULL COMMENT '评论内容',
    `status` TINYINT DEFAULT 0 COMMENT '评论状态：0-待审核，1-已通过，2-已拒绝',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_article_id` (`article_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_parent_id` (`parent_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_create_time` (`create_time`),
    CONSTRAINT `fk_comment_article` FOREIGN KEY (`article_id`) REFERENCES `article` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_comment_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_comment_parent` FOREIGN KEY (`parent_id`) REFERENCES `article_comment` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论表';

-- 12. 图片资源表 (image)
CREATE TABLE IF NOT EXISTS `image` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '图片 ID',
    `user_id` BIGINT NOT NULL COMMENT '上传用户 ID',
    `article_id` BIGINT NULL COMMENT '关联文章 ID（可为空）',
    `original_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
    `storage_path` VARCHAR(500) NOT NULL COMMENT '存储路径（本地路径或云存储URL）',
    `thumbnail_path` VARCHAR(500) NULL COMMENT '缩略图路径',
    `file_size` BIGINT NULL COMMENT '文件大小（字节）',
    `width` INT NULL COMMENT '图片宽度',
    `height` INT NULL COMMENT '图片高度',
    `mime_type` VARCHAR(50) NULL COMMENT 'MIME类型',
    `storage_type` VARCHAR(20) DEFAULT 'local' COMMENT '存储类型：local-本地, oss-阿里云OSS, cos-腾讯云COS',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    PRIMARY KEY (`id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_article_id` (`article_id`),
    INDEX `idx_create_time` (`create_time`),
    CONSTRAINT `fk_image_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='图片资源表';

-- 13. 通知表 (notification)
CREATE TABLE IF NOT EXISTS `notification` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '通知 ID',
    `user_id` BIGINT NOT NULL COMMENT '接收通知的用户 ID',
    `type` VARCHAR(30) NOT NULL COMMENT '通知类型：COMMENT-新评论, LIKE-新点赞, FAVORITE-新收藏, SYSTEM-系统通知',
    `title` VARCHAR(200) NOT NULL COMMENT '通知标题',
    `content` VARCHAR(500) NULL COMMENT '通知内容',
    `related_article_id` BIGINT NULL COMMENT '关联文章ID',
    `related_user_id` BIGINT NULL COMMENT '触发通知的用户ID',
    `related_user_name` VARCHAR(50) NULL COMMENT '触发通知的用户名',
    `is_read` TINYINT(1) DEFAULT 0 COMMENT '是否已读：0-未读，1-已读',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '通知创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_is_read` (`user_id`, `is_read`),
    INDEX `idx_create_time` (`create_time`),
    CONSTRAINT `fk_notification_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知表';

-- 14. 收藏夹分类表 (favorite_folder)
CREATE TABLE IF NOT EXISTS `favorite_folder` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `user_id` BIGINT NOT NULL COMMENT '用户 ID',
    `name` VARCHAR(50) NOT NULL COMMENT '收藏夹名称',
    `description` VARCHAR(200) NULL COMMENT '收藏夹描述',
    `sort_order` INT DEFAULT 0 COMMENT '排序字段（数字越小越靠前）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_name` (`user_id`, `name`),
    INDEX `idx_user_id` (`user_id`),
    CONSTRAINT `fk_favorite_folder_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收藏夹分类表';

-- 15. 备份记录表 (backup_record)
CREATE TABLE IF NOT EXISTS `backup_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '备份 ID',
    `user_id` BIGINT NULL COMMENT '用户 ID（系统自动备份时为 NULL）',
    `backup_type` VARCHAR(20) NOT NULL COMMENT '备份类型：MANUAL-手动导出, AUTO-自动定时备份',
    `format` VARCHAR(20) NOT NULL COMMENT '备份格式：PDF, WORD, MARKDOWN_ZIP, ALL',
    `file_path` VARCHAR(500) NULL COMMENT '备份文件路径',
    `file_size` BIGINT NULL COMMENT '备份文件大小（字节）',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PROCESSING' COMMENT '备份状态：PROCESSING-处理中, SUCCESS-成功, FAILED-失败',
    `article_count` INT DEFAULT 0 COMMENT '包含的文章数量',
    `error_message` VARCHAR(500) NULL COMMENT '错误信息（失败时记录）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_backup_type` (`backup_type`),
    INDEX `idx_status` (`status`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='备份记录表';
