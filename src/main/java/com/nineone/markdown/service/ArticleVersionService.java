package com.nineone.markdown.service;

import com.nineone.markdown.entity.ArticleVersion;

import java.util.List;

/**
 * 文章版本历史服务接口
 */
public interface ArticleVersionService {

    /**
     * 保存文章版本快照
     * @param articleId 文章ID
     * @param title 标题
     * @param content 内容
     * @param summary 摘要
     * @param changeNote 修改备注
     * @param operatorId 操作者ID
     * @param operatorName 操作者名称
     */
    void saveVersion(Long articleId, String title, String content, String summary, String changeNote, Long operatorId, String operatorName);

    /**
     * 获取文章的所有版本列表
     * @param articleId 文章ID
     * @return 版本列表
     */
    List<ArticleVersion> getVersionsByArticleId(Long articleId);

    /**
     * 获取指定版本的详情
     * @param versionId 版本ID
     * @return 版本详情
     */
    ArticleVersion getVersionById(Long versionId);

    /**
     * 回滚到指定版本
     * @param articleId 文章ID
     * @param versionId 版本ID
     * @param operatorId 操作者ID
     * @param operatorName 操作者名称
     */
    void rollbackToVersion(Long articleId, Long versionId, Long operatorId, String operatorName);

    /**
     * 比较两个版本的差异
     * @param versionId1 版本1 ID
     * @param versionId2 版本2 ID
     * @return 差异信息
     */
    String diffVersions(Long versionId1, Long versionId2);
}
