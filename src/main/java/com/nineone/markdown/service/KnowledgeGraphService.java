package com.nineone.markdown.service;

import com.nineone.markdown.vo.GlobalKnowledgeGraphVO;
import com.nineone.markdown.vo.KnowledgeGraphVO;

/**
 * 知识图谱服务接口
 */
public interface KnowledgeGraphService {

    /**
     * 为指定文章生成知识图谱
     */
    KnowledgeGraphVO generateGraph(Long articleId);

    /**
     * 获取指定文章的知识图谱
     */
    KnowledgeGraphVO getGraphByArticleId(Long articleId);

    /**
     * 获取全局知识图谱（跨文章合并）
     */
    GlobalKnowledgeGraphVO getGlobalGraph();

    /**
     * 删除指定文章的知识图谱
     */
    void deleteGraph(Long articleId);

    /**
     * 重新生成知识图谱（先删后建）
     */
    KnowledgeGraphVO regenerateGraph(Long articleId);
}
