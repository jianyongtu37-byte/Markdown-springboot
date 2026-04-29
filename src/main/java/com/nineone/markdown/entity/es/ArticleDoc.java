package com.nineone.markdown.entity.es;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

/**
 * Elasticsearch 文章文档实体类
 * 用于全文检索和高亮显示
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "article_index")
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArticleDoc {

    /**
     * 文章ID，与MySQL中的id对应
     */
    @Id
    private Long id;

    /**
     * 作者ID
     */
    @Field(type = FieldType.Long)
    private Long userId;

    /**
     * 分类ID
     */
    @Field(type = FieldType.Long)
    private Long categoryId;

    /**
     * 文章标题 - 使用IK分词器进行深度分词
     * ik_max_word: 会将文本做最细粒度的拆分（适合索引）
     * ik_smart: 会将文本做最粗粒度的拆分（适合搜索）
     */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String title;

    /**
     * 文章内容 - 使用IK分词器进行深度分词
     */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String content;

    /**
     * AI生成的摘要
     */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String summary;

    /**
     * 作者名称 - 使用keyword类型，不进行分词，精确匹配
     */
    @Field(type = FieldType.Keyword)
    private String authorName;

    /**
     * 分类名称 - 使用keyword类型，不进行分词，精确匹配
     */
    @Field(type = FieldType.Keyword)
    private String categoryName;

    /**
     * 文章状态：0-草稿箱，1-已发布
     */
    @Field(type = FieldType.Integer)
    private Integer status;

    /**
     * 权限：0-私密，1-公开分享
     */
    @Field(type = FieldType.Integer)
    private Integer isPublic;

    /**
     * 阅读量统计
     */
    @Field(type = FieldType.Integer)
    private Integer viewCount;

    /**
     * 创建时间
     */
    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updateTime;

    /**
     * 标签列表，用逗号分隔 - 使用IK分词器进行分词
     */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String tags;

    /**
     * 从Article实体和相关信息构建ArticleDoc
     */
    public static ArticleDoc fromArticle(com.nineone.markdown.entity.Article article, 
                                         String authorName, 
                                         String categoryName, 
                                         String tags) {
        // 映射状态：草稿为0，已发布（私有或公开）为1
        Integer statusValue = article.getStatus().isDraft() ? 0 : 1;
        // 映射是否公开：只有公开文章为1
        Integer isPublicValue = article.getStatus().isPublic() ? 1 : 0;
        
        return ArticleDoc.builder()
                .id(article.getId())
                .userId(article.getUserId())
                .categoryId(article.getCategoryId())
                .title(article.getTitle())
                .content(article.getContent())
                .summary(article.getSummary())
                .authorName(authorName)
                .categoryName(categoryName)
                .status(statusValue)
                .isPublic(isPublicValue)
                .viewCount(article.getViewCount())
                .createTime(article.getCreateTime())
                .updateTime(article.getUpdateTime())
                .tags(tags)
                .build();
    }
}