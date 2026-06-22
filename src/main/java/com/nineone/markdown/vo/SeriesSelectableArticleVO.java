package com.nineone.markdown.vo;

import com.nineone.markdown.enums.ArticleStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeriesSelectableArticleVO {

    private Long id;

    private String title;

    private ArticleStatusEnum status;

    private LocalDateTime createTime;
}
