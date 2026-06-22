package com.nineone.markdown.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReadingHistoryVO {
    private Long articleId;
    private String title;
    private String authorName;
    private Integer progress;
    private String lastPosition;
    private LocalDateTime lastReadTime;
}
