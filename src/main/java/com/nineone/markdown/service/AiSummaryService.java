package com.nineone.markdown.service;

/**
 * AI摘要生成服务接口
 */
public interface AiSummaryService {
    
    /**
     * 生成文章摘要
     * @param content 文章内容
     * @return 生成的摘要
     */
    String generateSummary(String content);
    
    /**
     * 生成文章标题
     * @param content 文章内容
     * @return 生成的标题
     */
    String generateTitle(String content);
    
    /**
     * 获取服务名称
     * @return 服务名称
     */
    String getServiceName();

    /**
     * 文本润色
     * @param content 原始文本内容
     * @param style 润色风格 (可选)
     * @param tone 语气风格 (可选)
     * @return 润色后的文本
     */
    String polishText(String content, String style, String tone);
}
