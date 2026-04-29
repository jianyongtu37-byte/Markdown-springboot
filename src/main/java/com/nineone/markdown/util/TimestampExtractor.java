package com.nineone.markdown.util;

import com.nineone.markdown.entity.ArticleTimestamp;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 时间戳提取器
 * 从Markdown内容中提取时间戳信息
 */
@Slf4j
public class TimestampExtractor {

    // 支持的时间戳格式正则表达式
    private static final Pattern[] TIMESTAMP_PATTERNS = {
        // 格式1: [01:27] 或 [1:27]
        Pattern.compile("\\[(\\d+):(\\d{1,2})\\]"),
        // 格式2: 01:27 或 1:27 (前面可能有空格或标点)
        Pattern.compile("(?:^|[\\s\\p{P}])(\\d+):(\\d{1,2})(?:$|[\\s\\p{P}])"),
        // 格式3: 1分27秒
        Pattern.compile("(\\d+)[分时](\\d{1,2})秒"),
        // 格式4: 1分 (只有分钟)
        Pattern.compile("(\\d+)[分时](?!\\d)"),
        // 格式5: 27秒 (只有秒)
        Pattern.compile("(\\d+)秒")
    };

    /**
     * 从Markdown内容中提取时间戳
     * @param content Markdown内容
     * @return 时间戳列表
     */
    public static List<ArticleTimestamp> extractTimestamps(String content) {
        List<ArticleTimestamp> timestamps = new ArrayList<>();
        
        if (content == null || content.isBlank()) {
            return timestamps;
        }

        // 按行处理内容
        String[] lines = content.split("\\r?\\n");
        
        for (int lineNo = 0; lineNo < lines.length; lineNo++) {
            String line = lines[lineNo];
            List<ArticleTimestamp> lineTimestamps = extractTimestampsFromLine(line, lineNo + 1);
            timestamps.addAll(lineTimestamps);
        }

        log.debug("从内容中提取到 {} 个时间戳", timestamps.size());
        return timestamps;
    }

    /**
     * 从单行文本中提取时间戳
     * @param line 文本行
     * @param lineNo 行号
     * @return 该行的时间戳列表
     */
    private static List<ArticleTimestamp> extractTimestampsFromLine(String line, int lineNo) {
        List<ArticleTimestamp> timestamps = new ArrayList<>();
        
        for (Pattern pattern : TIMESTAMP_PATTERNS) {
            Matcher matcher = pattern.matcher(line);
            
            while (matcher.find()) {
                try {
                    int minutes = 0;
                    int seconds = 0;
                    
                    // 根据不同的模式组提取分钟和秒
                    if (pattern.pattern().contains("[分时]") && !pattern.pattern().contains("秒")) {
                        // 格式4: 只有分钟
                        minutes = Integer.parseInt(matcher.group(1));
                        seconds = 0;
                    } else if (pattern.pattern().contains("秒") && !pattern.pattern().contains("[分时]")) {
                        // 格式5: 只有秒
                        minutes = 0;
                        seconds = Integer.parseInt(matcher.group(1));
                    } else {
                        // 格式1-3: 有分钟和秒
                        minutes = Integer.parseInt(matcher.group(1));
                        seconds = Integer.parseInt(matcher.group(2));
                    }
                    
                    // 验证时间合理性
                    if (isValidTime(minutes, seconds)) {
                        int totalSeconds = minutes * 60 + seconds;
                        String label = formatTimeLabel(minutes, seconds);
                        
                        // 提取时间戳附近的内容作为摘要
                        String excerpt = extractExcerpt(line, matcher.start(), matcher.end());
                        
                        ArticleTimestamp timestamp = ArticleTimestamp.builder()
                                .label(label)
                                .seconds(totalSeconds)
                                .excerpt(excerpt)
                                .lineNo(lineNo)
                                .build();
                        
                        timestamps.add(timestamp);
                        log.debug("提取到时间戳: {} ({}秒), 行号: {}, 摘要: {}", 
                                label, totalSeconds, lineNo, excerpt);
                    }
                } catch (NumberFormatException e) {
                    log.warn("时间戳解析失败: {}", matcher.group(), e);
                }
            }
        }
        
        return timestamps;
    }

    /**
     * 验证时间是否合理
     * @param minutes 分钟
     * @param seconds 秒
     * @return 是否有效
     */
    private static boolean isValidTime(int minutes, int seconds) {
        // 分钟不能太大（假设视频不超过10小时）
        if (minutes < 0 || minutes > 600) {
            return false;
        }
        
        // 秒数必须在0-59之间（除非只有秒没有分钟的情况）
        if (seconds < 0 || seconds > 59) {
            // 对于只有秒的情况，允许更大的秒数
            if (minutes == 0 && seconds <= 36000) { // 最多10小时
                return true;
            }
            return false;
        }
        
        return true;
    }

    /**
     * 格式化时间标签
     * @param minutes 分钟
     * @param seconds 秒
     * @return 格式化的时间标签，如 "01:27"
     */
    private static String formatTimeLabel(int minutes, int seconds) {
        if (minutes == 0) {
            return String.format("%02d", seconds);
        }
        return String.format("%02d:%02d", minutes, seconds);
    }

    /**
     * 提取时间戳附近的内容作为摘要
     * @param line 文本行
     * @param start 匹配开始位置
     * @param end 匹配结束位置
     * @return 摘要内容
     */
    private static String extractExcerpt(String line, int start, int end) {
        // 提取时间戳前后各30个字符
        int excerptStart = Math.max(0, start - 30);
        int excerptEnd = Math.min(line.length(), end + 30);
        
        String excerpt = line.substring(excerptStart, excerptEnd).trim();
        
        // 如果截断了开头，添加"..."
        if (excerptStart > 0) {
            excerpt = "..." + excerpt;
        }
        
        // 如果截断了结尾，添加"..."
        if (excerptEnd < line.length()) {
            excerpt = excerpt + "...";
        }
        
        // 限制摘要长度
        if (excerpt.length() > 100) {
            excerpt = excerpt.substring(0, 100) + "...";
        }
        
        return excerpt;
    }

    /**
     * 将秒数转换为时间标签
     * @param totalSeconds 总秒数
     * @return 时间标签，如 "01:27"
     */
    public static String secondsToLabel(int totalSeconds) {
        if (totalSeconds < 0) {
            return "00:00";
        }
        
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        
        return formatTimeLabel(minutes, seconds);
    }

    /**
     * 将时间标签转换为秒数
     * @param label 时间标签，如 "01:27"
     * @return 总秒数，解析失败返回-1
     */
    public static int labelToSeconds(String label) {
        if (label == null || label.isBlank()) {
            return -1;
        }
        
        try {
            // 尝试解析 "mm:ss" 格式
            if (label.contains(":")) {
                String[] parts = label.split(":");
                if (parts.length == 2) {
                    int minutes = Integer.parseInt(parts[0]);
                    int seconds = Integer.parseInt(parts[1]);
                    if (isValidTime(minutes, seconds)) {
                        return minutes * 60 + seconds;
                    }
                }
            }
            // 尝试解析纯秒数
            else {
                int seconds = Integer.parseInt(label);
                if (seconds >= 0 && seconds <= 36000) { // 最多10小时
                    return seconds;
                }
            }
        } catch (NumberFormatException e) {
            log.warn("时间标签解析失败: {}", label, e);
        }
        
        return -1;
    }
}