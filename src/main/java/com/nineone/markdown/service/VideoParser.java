package com.nineone.markdown.service;

import com.nineone.markdown.enums.VideoSource;

/**
 * 视频解析器接口 - 策略模式
 */
public interface VideoParser {
    /**
     * 判断当前解析器是否支持这个链接
     * @param url 视频URL
     * @return true表示支持，false表示不支持
     */
    boolean supports(String url);

    /**
     * 提取视频ID
     * @param url 视频URL
     * @return 视频ID，如YouTube的videoId或B站的BV号
     */
    String extractVideoId(String url);

    /**
     * 返回平台名称
     * @return 平台名称
     */
    String getPlatformName();

    /**
     * 获取视频来源枚举
     * @return 视频来源枚举
     */
    VideoSource getVideoSource();
}
