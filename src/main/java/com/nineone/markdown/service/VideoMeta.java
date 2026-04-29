package com.nineone.markdown.service;

import com.nineone.markdown.enums.VideoSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 视频元数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoMeta {
    /**
     * 视频来源
     */
    private VideoSource source;

    /**
     * 视频ID（YouTube videoId 或 B站 BV号）
     */
    private String videoId;

    /**
     * 视频时长（秒）
     */
    private Integer duration;
}