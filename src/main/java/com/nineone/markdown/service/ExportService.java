package com.nineone.markdown.service;

import com.nineone.markdown.entity.BackupRecord;

import java.util.List;

/**
 * 数据导出服务接口
 */
public interface ExportService {

    /**
     * 导出单篇文章为PDF
     *
     * @param articleId 文章ID
     * @param userId    当前用户ID
     * @return 导出文件的存储路径
     */
    String exportArticleToPdf(Long articleId, Long userId);

    /**
     * 导出单篇文章为Word (.docx)
     *
     * @param articleId 文章ID
     * @param userId    当前用户ID
     * @return 导出文件的存储路径
     */
    String exportArticleToWord(Long articleId, Long userId);

    /**
     * 导出用户所有文章为Markdown文件打包下载（ZIP）
     *
     * @param userId 当前用户ID
     * @return 导出ZIP文件的存储路径
     */
    String exportAllArticlesAsMarkdownZip(Long userId);

    /**
     * 获取当前用户的导出/备份记录列表
     *
     * @param userId 用户ID
     * @return 备份记录列表
     */
    List<BackupRecord> getExportRecords(Long userId);

    /**
     * 删除导出/备份记录
     *
     * @param recordId 记录ID
     * @param userId   用户ID
     */
    void deleteExportRecord(Long recordId, Long userId);
}
