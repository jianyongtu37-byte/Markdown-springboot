package com.nineone.markdown.controller;

import com.nineone.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 视频控制器
 * 提供视频 URL 解析和元数据提取功能
 */
@RestController
@RequestMapping("/api/videos")
@Slf4j
public class VideoController {

    // YouTube URL patterns
    private static final Pattern YOUTUBE_WATCH_PATTERN = Pattern.compile(
            "https?://(?:www\\.)?youtube\\.com/watch\\?v=([a-zA-Z0-9_-]{11})");
    private static final Pattern YOUTUBE_SHORT_PATTERN = Pattern.compile(
            "https?://youtu\\.be/([a-zA-Z0-9_-]{11})");
    private static final Pattern YOUTUBE_EMBED_PATTERN = Pattern.compile(
            "https?://(?:www\\.)?youtube\\.com/embed/([a-zA-Z0-9_-]{11})");

    // Bilibili URL patterns
    private static final Pattern BILIBILI_VIDEO_PATTERN = Pattern.compile(
            "https?://(?:www\\.)?bilibili\\.com/video/(BV[a-zA-Z0-9]+)");
    private static final Pattern BILIBILI_BV_PATTERN = Pattern.compile(
            "https?://b23\\.tv/(BV[a-zA-Z0-9]+)");

    /**
     * 解析视频 URL，返回视频元数据
     */
    @PostMapping("/resolve")
    public Result<Map<String, Object>> resolveVideo(@RequestBody Map<String, String> request) {
        String url = request.get("url");
        if (url == null || url.trim().isEmpty()) {
            return Result.badRequest("视频URL不能为空");
        }

        url = url.trim();
        Map<String, Object> meta = new HashMap<>();

        // Try YouTube
        Matcher ytWatch = YOUTUBE_WATCH_PATTERN.matcher(url);
        Matcher ytShort = YOUTUBE_SHORT_PATTERN.matcher(url);
        Matcher ytEmbed = YOUTUBE_EMBED_PATTERN.matcher(url);

        if (ytWatch.find()) {
            String videoId = ytWatch.group(1);
            meta.put("videoSource", "YOUTUBE");
            meta.put("videoId", videoId);
            meta.put("videoUrl", url);
            meta.put("embedUrl", "https://www.youtube.com/embed/" + videoId);
            log.info("Resolved YouTube video: {}", videoId);
            return Result.success(meta);
        } else if (ytShort.find()) {
            String videoId = ytShort.group(1);
            meta.put("videoSource", "YOUTUBE");
            meta.put("videoId", videoId);
            meta.put("videoUrl", url);
            meta.put("embedUrl", "https://www.youtube.com/embed/" + videoId);
            log.info("Resolved YouTube short link: {}", videoId);
            return Result.success(meta);
        } else if (ytEmbed.find()) {
            String videoId = ytEmbed.group(1);
            meta.put("videoSource", "YOUTUBE");
            meta.put("videoId", videoId);
            meta.put("videoUrl", url);
            meta.put("embedUrl", url);
            log.info("Resolved YouTube embed: {}", videoId);
            return Result.success(meta);
        }

        // Try Bilibili
        Matcher biliVideo = BILIBILI_VIDEO_PATTERN.matcher(url);
        Matcher biliBV = BILIBILI_BV_PATTERN.matcher(url);

        if (biliVideo.find()) {
            String bvId = biliVideo.group(1);
            meta.put("videoSource", "BILIBILI");
            meta.put("videoId", bvId);
            meta.put("videoUrl", url);
            meta.put("embedUrl", "https://player.bilibili.com/player.html?bvid=" + bvId);
            log.info("Resolved Bilibili video: {}", bvId);
            return Result.success(meta);
        } else if (biliBV.find()) {
            String bvId = biliBV.group(1);
            meta.put("videoSource", "BILIBILI");
            meta.put("videoId", bvId);
            meta.put("videoUrl", url);
            meta.put("embedUrl", "https://player.bilibili.com/player.html?bvid=" + bvId);
            log.info("Resolved Bilibili short link: {}", bvId);
            return Result.success(meta);
        }

        // Unknown / local
        meta.put("videoSource", "LOCAL");
        meta.put("videoId", url);
        meta.put("videoUrl", url);
        log.info("Resolved as LOCAL/unknown video: {}", url);
        return Result.success(meta);
    }
}
