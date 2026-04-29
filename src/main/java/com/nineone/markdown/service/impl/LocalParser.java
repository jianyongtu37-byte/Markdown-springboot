package com.nineone.markdown.service.impl;

import com.nineone.markdown.enums.VideoSource;
import com.nineone.markdown.service.VideoParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 本地视频链接解析器
 */
@Component
@Slf4j
public class LocalParser implements VideoParser {
    @Override
    public boolean supports(String url) {
        // 默认解析器，当其他解析器都不支持时使用
        // 这里返回true，但实际使用时会优先匹配其他解析器
        return true;
    }

    @Override
    public String extractVideoId(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        // 本地视频使用文件名作为ID
        int lastSlash = url.lastIndexOf('/');
        int lastQuestion = url.lastIndexOf('?');
        
        String fileName;
        if (lastQuestion > lastSlash) {
            fileName = url.substring(lastSlash + 1, lastQuestion);
        } else {
            fileName = url.substring(lastSlash + 1);
        }

        // 去除可能的文件扩展名
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            fileName = fileName.substring(0, lastDot);
        }

        return fileName;
    }

    @Override
    public String getPlatformName() {
        return "本地视频";
    }

    @Override
    public VideoSource getVideoSource() {
        return VideoSource.LOCAL;
    }
}