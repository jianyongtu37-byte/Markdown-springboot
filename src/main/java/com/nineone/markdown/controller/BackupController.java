package com.nineone.markdown.controller;

import com.nineone.markdown.common.Result;
import com.nineone.markdown.entity.BackupRecord;
import com.nineone.markdown.service.BackupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 自动备份管理控制器
 */
@RestController
@RequestMapping("/api/backup")
@RequiredArgsConstructor
public class BackupController {

    private final BackupService backupService;

    /**
     * 手动触发全站自动备份（管理员功能）
     */
    @PostMapping("/trigger")
    public Result<String> triggerBackup() {
        backupService.performAutoBackup();
        return Result.success("备份任务已触发");
    }

    /**
     * 获取所有备份记录（管理员功能）
     */
    @GetMapping("/records")
    public Result<List<BackupRecord>> getAllBackupRecords() {
        List<BackupRecord> records = backupService.getAllBackupRecords();
        return Result.success(records);
    }

    /**
     * 清理过期备份（管理员功能）
     *
     * @param retentionDays 保留天数
     */
    @DeleteMapping("/clean")
    public Result<String> cleanExpiredBackups(@RequestParam(defaultValue = "30") int retentionDays) {
        backupService.cleanExpiredBackups(retentionDays);
        return Result.success("过期备份清理完成");
    }
}
