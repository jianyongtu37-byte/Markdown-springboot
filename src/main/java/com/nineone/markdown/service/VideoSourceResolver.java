package com.nineone.markdown.service;

import com.nineone.markdown.enums.VideoSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 视频URL解析器
 */
@Component
@Slf4j
public class VideoSourceResolver {

    private static final Pattern YOUTUBE_PATTERN = Pattern.compile(
            "(?:youtube\\.com\\/(?:[^\\/\\n\\s]+\\/\\S+\\/|(?:v|e(?:mbed)?)\\/|.*[?&]v=)|youtu\\.be\\/)([a-zA-Z0-9_-]{11})"
    );
    
    private static final Pattern BILIBILI_PATTERN = Pattern.compile(
            "bilibili\\.com\\/video\\/(?:BV([a-zA-Z0-9]{10})|av(\\d+))"
    );

    /**
     * 解析视频URL，获取视频元数据
     */
    public VideoMeta resolve(String videoUrl) {
        if (videoUrl == null || videoUrl.isBlank()) {
            return null;
        }

        VideoSource source = VideoSource.fromUrl(videoUrl);
        String videoId = extractVideoId(videoUrl, source);
        
        // 目前仅解析视频ID，时长需要后续通过API获取
        return VideoMeta.builder()
                .source(source)
                .videoId(videoId)
                .duration(null) // 留空，后续可通过API获取
                .build();
    }

    /**
     * 根据视频来源提取视频ID
     */
    private String extractVideoId(String url, VideoSource source) {
        if (source == null) {
            return null;
        }

        switch (source) {
            case YOUTUBE:
                Matcher youtubeMatcher = YOUTUBE_PATTERN.matcher(url);
                if (youtubeMatcher.find()) {
                    return youtubeMatcher.group(1);
                }
                break;
            case BILIBILI:
                Matcher bilibiliMatcher = BILIBILI_PATTERN.matcher(url);
                if (bilibiliMatcher.find()) {
                    // 优先取BV号，没有则取av号
                    String bvId = bilibiliMatcher.group(1);
                    if (bvId != null && !bvId.isEmpty()) {
                        return "BV" + bvId;
                    } else {
                        String avId = bilibiliMatcher.group(2);
                        if (avId != null && !avId.isEmpty()) {
                            return "av" + avId;
                        }
                    }
                }
                break;
            case LOCAL:
                // 本地视频使用文件名作为ID
                int lastSlash = url.lastIndexOf('/');
                int lastQuestion = url.lastIndexOf('?');
                if (lastQuestion > lastSlash) {
                    return url.substring(lastSlash + 1, lastQuestion);
                }
                return url.substring(lastSlash + 1);
            default:
                break;
        }
        
        log.warn("无法从URL中提取视频ID: {}", url);
        return null;
    }
}