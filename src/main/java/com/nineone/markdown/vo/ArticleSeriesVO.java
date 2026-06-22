package com.nineone.markdown.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleSeriesVO {
    private Long id;
    private String title;
    private String description;
    private String coverImageUrl;
    private String authorName;
    private Integer articleCount;
    private Integer isPublic;
    private List<SeriesArticleItem> articles;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeriesArticleItem {
        private Long id;
        private String title;
        private Integer sortOrder;
    }
}
