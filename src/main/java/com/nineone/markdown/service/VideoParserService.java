package com.nineone.markdown.service;

import com.nineone.markdown.enums.VideoSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 视频解析服务 - 使用策略模式管理多个解析器
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VideoParserService {
    
    private final List<VideoParser> parsers;
    
    /**
     * 解析视频URL，获取视频元数据
     * @param videoUrl 视频URL
     * @return 视频元数据
     */
    public VideoMeta resolve(String videoUrl) {
        if (videoUrl == null || videoUrl.isBlank()) {
            return null;
        }
        
        // 查找支持该URL的解析器
        VideoParser parser = findParser(videoUrl);
        if (parser == null) {
            log.warn("没有找到支持该URL的解析器: {}", videoUrl);
            return null;
        }
        
        // 提取视频ID
        String videoId = parser.extractVideoId(videoUrl);
        VideoSource source = parser.getVideoSource();
        
        // 目前仅解析视频ID，时长需要后续通过API获取
        return VideoMeta.builder()
                .source(source)
                .videoId(videoId)
                .duration(null) // 留空，后续可通过API获取
                .build();
    }
    
    /**
     * 查找支持该URL的解析器
     * @param url 视频URL
     * @return 支持该URL的解析器，如果没找到则返回null
     */
    public VideoParser findParser(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        
        // 首先尝试特定的解析器（Bilibili、YouTube）
        for (VideoParser parser : parsers) {
            // 跳过默认解析器（LocalParser）
            if (parser instanceof com.nineone.markdown.service.impl.LocalParser) {
                continue;
            }
            
            if (parser.supports(url)) {
                log.debug("找到解析器: {} 支持URL: {}", parser.getPlatformName(), url);
                return parser;
            }
        }
        
        // 如果没有特定的解析器支持，使用默认解析器（LocalParser）
        for (VideoParser parser : parsers) {
            if (parser instanceof com.nineone.markdown.service.impl.LocalParser) {
                log.debug("使用默认解析器处理URL: {}", url);
                return parser;
            }
        }
        
        return null;
    }
    
    /**
     * 获取所有解析器
     * @return 解析器列表
     */
    public List<VideoParser> getAllParsers() {
        return parsers;
    }
}