package com.nineone.markdown.service.impl;

import com.nineone.markdown.enums.VideoSource;
import com.nineone.markdown.service.VideoParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * YouTube视频链接解析器
 */
@Component
@Slf4j
public class YouTubeParser implements VideoParser {
    // 匹配YouTube视频ID的正则 - 复用VideoSourceResolver中的正则
    private static final Pattern YOUTUBE_PATTERN = Pattern.compile(
            "(?:youtube\\.com\\/(?:[^\\/\\n\\s]+\\/\\S+\\/|(?:v|e(?:mbed)?)\\/|.*[?&]v=)|youtu\\.be\\/)([a-zA-Z0-9_-]{11})"
    );

    @Override
    public boolean supports(String url) {
        return url != null && (url.toLowerCase().contains("youtube.com") || url.toLowerCase().contains("youtu.be"));
    }

    @Override
    public String extractVideoId(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        Matcher matcher = YOUTUBE_PATTERN.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }

        log.warn("无法从YouTube URL中提取视频ID: {}", url);
        return null;
    }

    @Override
    public String getPlatformName() {
        return "YouTube";
    }

    @Override
    public VideoSource getVideoSource() {
        return VideoSource.YOUTUBE;
    }
}