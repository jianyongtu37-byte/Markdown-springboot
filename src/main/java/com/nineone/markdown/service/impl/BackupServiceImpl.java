package com.nineone.markdown.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nineone.markdown.entity.Article;
import com.nineone.markdown.entity.BackupRecord;
import com.nineone.markdown.enums.ArticleStatusEnum;
import com.nineone.markdown.mapper.ArticleMapper;
import com.nineone.markdown.mapper.BackupRecordMapper;
import com.nineone.markdown.service.BackupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 自动定时备份服务实现类
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BackupServiceImpl implements BackupService {

    private final ArticleMapper articleMapper;
    private final BackupRecordMapper backupRecordMapper;

    @Value("${app.export.dir:exports}")
    private String exportDir;

    @Value("${app.backup.retention-days:30}")
    private int retentionDays;

    /**
     * 定时执行全站自动备份
     * 默认每天凌晨 2:00 执行
     */
    @Scheduled(cron = "${app.backup.cron:0 0 2 * * ?}")
    @Override
    public void performAutoBackup() {
        log.info("开始执行全站自动备份任务...");

        // 查询所有公开文章
        LambdaQueryWrapper<Article> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Article::getStatus, ArticleStatusEnum.PUBLIC)
                .eq(Article::getDeleted, 0)
                .orderByAsc(Article::getUserId)
                .orderByDesc(Article::getCreateTime);
        List<Article> articles = articleMapper.selectList(queryWrapper);

        if (articles.isEmpty()) {
            log.info("没有公开文章需要备份");
            return;
        }

        // 创建备份记录
        BackupRecord record = BackupRecord.builder()
                .backupType("AUTO")
                .format("ALL")
                .status("PROCESSING")
                .build();
        backupRecordMapper.insert(record);

        try {
            // 按用户分组生成备份文件
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String backupDir = exportDir + "/auto-backup/" + timestamp;
            Files.createDirectories(Paths.get(backupDir));

            // 生成全站总览ZIP
            String allZipPath = backupDir + "/full_backup_" + timestamp + ".zip";
            int totalArticles = generateFullBackupZip(articles, allZipPath);

            record.setFilePath(allZipPath);
            File zipFile = new File(allZipPath);
            record.setFileSize(zipFile.length());
            record.setArticleCount(totalArticles);
            record.setStatus("SUCCESS");

            log.info("全站自动备份完成, 共备份{}篇文章, 文件: {}", totalArticles, allZipPath);

            // 清理过期备份
            cleanExpiredBackups(retentionDays);

        } catch (Exception e) {
            record.setStatus("FAILED");
            record.setErrorMessage(e.getMessage());
            log.error("全站自动备份失败", e);
        } finally {
            backupRecordMapper.updateById(record);
        }
    }

    /**
     * 生成全站备份ZIP文件
     */
    private int generateFullBackupZip(List<Article> articles, String zipPath) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath), StandardCharsets.UTF_8)) {
            // 按用户分组
            Long currentUserId = null;
            String currentUserDir = "";

            for (Article article : articles) {
                // 按用户分目录
                if (!article.getUserId().equals(currentUserId)) {
                    currentUserId = article.getUserId();
                    currentUserDir = "user_" + currentUserId + "/";
                }

                // 构建Markdown内容
                StringBuilder mdContent = new StringBuilder();
                mdContent.append("---\n");
                mdContent.append("title: \"").append(escapeYaml(article.getTitle())).append("\"\n");
                mdContent.append("id: ").append(article.getId()).append("\n");
                mdContent.append("status: ").append(article.getStatus()).append("\n");
                mdContent.append("created: ").append(article.getCreateTime()).append("\n");
                mdContent.append("updated: ").append(article.getUpdateTime()).append("\n");
                if (article.getSummary() != null && !article.getSummary().isEmpty()) {
                    mdContent.append("summary: \"").append(escapeYaml(article.getSummary())).append("\"\n");
                }
                mdContent.append("---\n\n");
                mdContent.append("# ").append(article.getTitle()).append("\n\n");
                mdContent.append(article.getContent());

                // 安全文件名
                String safeTitle = article.getTitle().replaceAll("[\\\\/:*?\"<>|]", "_");
                if (safeTitle.length() > 100) {
                    safeTitle = safeTitle.substring(0, 100);
                }
                String entryName = currentUserDir + safeTitle + ".md";

                ZipEntry entry = new ZipEntry(entryName);
                entry.setTime(System.currentTimeMillis());
                zos.putNextEntry(entry);
                zos.write(mdContent.toString().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }

            return articles.size();
        }
    }

    /**
     * 转义YAML中的特殊字符
     */
    private String escapeYaml(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    @Override
    public List<BackupRecord> getAllBackupRecords() {
        LambdaQueryWrapper<BackupRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByDesc(BackupRecord::getCreateTime);
        return backupRecordMapper.selectList(queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cleanExpiredBackups(int retentionDays) {
        log.info("开始清理过期备份文件, 保留天数: {}", retentionDays);

        LocalDateTime expireTime = LocalDateTime.now().minusDays(retentionDays);

        // 查询过期的自动备份记录
        LambdaQueryWrapper<BackupRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(BackupRecord::getBackupType, "AUTO")
                .lt(BackupRecord::getCreateTime, expireTime);
        List<BackupRecord> expiredRecords = backupRecordMapper.selectList(queryWrapper);

        int deletedCount = 0;
        for (BackupRecord record : expiredRecords) {
            // 删除物理文件
            if (record.getFilePath() != null) {
                try {
                    File file = new File(record.getFilePath());
                    if (file.exists()) {
                        boolean deleted = file.delete();
                        if (deleted) {
                            log.debug("删除过期备份文件: {}", record.getFilePath());
                        }
                    }
                } catch (Exception e) {
                    log.warn("删除过期备份文件失败: {}", record.getFilePath(), e);
                }
            }

            // 删除数据库记录
            backupRecordMapper.deleteById(record.getId());
            deletedCount++;
        }

        log.info("过期备份清理完成, 共清理{}条记录", deletedCount);
    }
}
