package com.nineone.markdown.service.impl;

import com.nineone.markdown.enums.VideoSource;
import com.nineone.markdown.service.VideoParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * B站视频链接解析器
 */
@Component
@Slf4j
public class BilibiliParser implements VideoParser {
    // 匹配BV号的正则 - 复用VideoSourceResolver中的正则
    private static final Pattern BILIBILI_PATTERN = Pattern.compile(
            "bilibili\\.com\\/video\\/(?:BV([a-zA-Z0-9]{10})|av(\\d+))"
    );

    @Override
    public boolean supports(String url) {
        return url != null && url.toLowerCase().contains("bilibili.com");
    }

    @Override
    public String extractVideoId(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        Matcher matcher = BILIBILI_PATTERN.matcher(url);
        if (matcher.find()) {
            // 优先取BV号，没有则取av号
            String bvId = matcher.group(1);
            if (bvId != null && !bvId.isEmpty()) {
                return "BV" + bvId;
            } else {
                String avId = matcher.group(2);
                if (avId != null && !avId.isEmpty()) {
                    return "av" + avId;
                }
            }
        }

        log.warn("无法从B站URL中提取视频ID: {}", url);
        return null;
    }

    @Override
    public String getPlatformName() {
        return "哔哩哔哩";
    }

    @Override
    public VideoSource getVideoSource() {
        return VideoSource.BILIBILI;
    }
}