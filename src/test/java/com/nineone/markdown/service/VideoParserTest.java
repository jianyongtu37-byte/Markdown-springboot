package com.nineone.markdown.service;

import com.nineone.markdown.service.impl.BilibiliParser;
import com.nineone.markdown.service.impl.YouTubeParser;
import com.nineone.markdown.service.impl.LocalParser;

public class VideoParserTest {
    public static void main(String[] args) {
        System.out.println("测试视频解析策略模式实现...");
        
        // 测试YouTube解析器
        VideoParser youtubeParser = new YouTubeParser();
        String youtubeUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
        System.out.println("YouTube URL: " + youtubeUrl);
        System.out.println("支持吗? " + youtubeParser.supports(youtubeUrl));
        System.out.println("视频ID: " + youtubeParser.extractVideoId(youtubeUrl));
        System.out.println("平台名称: " + youtubeParser.getPlatformName());
        System.out.println("视频来源: " + youtubeParser.getVideoSource());
        System.out.println();
        
        // 测试B站解析器
        VideoParser bilibiliParser = new BilibiliParser();
        String bilibiliUrl = "https://www.bilibili.com/video/BV1GJ411x7h7";
        System.out.println("B站 URL: " + bilibiliUrl);
        System.out.println("支持吗? " + bilibiliParser.supports(bilibiliUrl));
        System.out.println("视频ID: " + bilibiliParser.extractVideoId(bilibiliUrl));
        System.out.println("平台名称: " + bilibiliParser.getPlatformName());
        System.out.println("视频来源: " + bilibiliParser.getVideoSource());
        System.out.println();
        
        // 测试本地解析器
        VideoParser localParser = new LocalParser();
        String localUrl = "/path/to/video/myvideo.mp4";
        System.out.println("本地 URL: " + localUrl);
        System.out.println("支持吗? " + localParser.supports(localUrl));
        System.out.println("视频ID: " + localParser.extractVideoId(localUrl));
        System.out.println("平台名称: " + localParser.getPlatformName());
        System.out.println("视频来源: " + localParser.getVideoSource());
        System.out.println();
        
        System.out.println("测试完成！策略模式实现成功。");
    }
}