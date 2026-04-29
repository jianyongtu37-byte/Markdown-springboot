package com.nineone.markdown.enums;

import lombok.Getter;

/**
 * 视频来源枚举
 */
@Getter
public enum VideoSource {
    YOUTUBE("YouTube"),
    BILIBILI("哔哩哔哩"),
    LOCAL("本地视频");

    private final String description;

    VideoSource(String description) {
        this.description = description;
    }

    /**
     * 根据URL判断视频来源
     */
    public static VideoSource fromUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        
        String lowerUrl = url.toLowerCase();
        if (lowerUrl.contains("youtube.com") || lowerUrl.contains("youtu.be")) {
            return YOUTUBE;
        } else if (lowerUrl.contains("bilibili.com")) {
            return BILIBILI;
        } else {
            return LOCAL;
        }
    }
}