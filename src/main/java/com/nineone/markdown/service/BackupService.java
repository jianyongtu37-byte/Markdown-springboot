package com.nineone.markdown.service;

import com.nineone.markdown.entity.BackupRecord;

import java.util.List;

/**
 * 自动定时备份服务接口
 */
public interface BackupService {

    /**
     * 执行全站自动备份（系统定时任务调用）
     * 备份所有用户的公开文章为Markdown ZIP包
     */
    void performAutoBackup();

    /**
     * 获取所有备份记录（管理员）
     *
     * @return 备份记录列表
     */
    List<BackupRecord> getAllBackupRecords();

    /**
     * 清理过期的备份文件
     *
     * @param retentionDays 保留天数
     */
    void cleanExpiredBackups(int retentionDays);
}
