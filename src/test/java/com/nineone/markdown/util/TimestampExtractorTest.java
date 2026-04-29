package com.nineone.markdown.util;

import com.nineone.markdown.entity.ArticleTimestamp;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TimestampExtractorTest {

    @Test
    void testExtractTimestampsWithBracketFormat() {
        String content = "这是一个测试内容 [01:27] 这里有一个时间戳 [2:30] 另一个时间戳";
        
        List<ArticleTimestamp> timestamps = TimestampExtractor.extractTimestamps(content);
        
        assertEquals(2, timestamps.size());
        
        // 验证第一个时间戳
        ArticleTimestamp ts1 = timestamps.get(0);
        assertEquals("01:27", ts1.getLabel());
        assertEquals(87, ts1.getSeconds());
        assertNotNull(ts1.getExcerpt());
        assertEquals(1, ts1.getLineNo());
        
        // 验证第二个时间戳
        ArticleTimestamp ts2 = timestamps.get(1);
        assertEquals("02:30", ts2.getLabel());
        assertEquals(150, ts2.getSeconds());
        assertNotNull(ts2.getExcerpt());
        assertEquals(1, ts2.getLineNo());
    }

    @Test
    void testExtractTimestampsWithColonFormat() {
        String content = "在 01:27 这个时间点，发生了重要事件。然后在 2:30 又有另一个事件。";
        
        List<ArticleTimestamp> timestamps = TimestampExtractor.extractTimestamps(content);
        
        assertEquals(2, timestamps.size());
        
        // 验证第一个时间戳
        ArticleTimestamp ts1 = timestamps.get(0);
        assertEquals("01:27", ts1.getLabel());
        assertEquals(87, ts1.getSeconds());
        
        // 验证第二个时间戳
        ArticleTimestamp ts2 = timestamps.get(1);
        assertEquals("02:30", ts2.getLabel());
        assertEquals(150, ts2.getSeconds());
    }

    @Test
    void testExtractTimestampsWithChineseFormat() {
        String content = "在1分27秒的时候，我们开始学习。然后在2分30秒的时候，我们休息。";
        
        List<ArticleTimestamp> timestamps = TimestampExtractor.extractTimestamps(content);
        
        assertEquals(2, timestamps.size());
        
        // 验证第一个时间戳
        ArticleTimestamp ts1 = timestamps.get(0);
        assertEquals("01:27", ts1.getLabel());
        assertEquals(87, ts1.getSeconds());
        
        // 验证第二个时间戳
        ArticleTimestamp ts2 = timestamps.get(1);
        assertEquals("02:30", ts2.getLabel());
        assertEquals(150, ts2.getSeconds());
    }

    @Test
    void testExtractTimestampsWithMinutesOnly() {
        String content = "在1分的时候，我们开始。在2分的时候，我们继续。";
        
        List<ArticleTimestamp> timestamps = TimestampExtractor.extractTimestamps(content);
        
        assertEquals(2, timestamps.size());
        
        // 验证第一个时间戳
        ArticleTimestamp ts1 = timestamps.get(0);
        assertEquals("01:00", ts1.getLabel());
        assertEquals(60, ts1.getSeconds());
        
        // 验证第二个时间戳
        ArticleTimestamp ts2 = timestamps.get(1);
        assertEquals("02:00", ts2.getLabel());
        assertEquals(120, ts2.getSeconds());
    }

    @Test
    void testExtractTimestampsWithSecondsOnly() {
        String content = "在27秒的时候，我们开始。在90秒的时候，我们继续。";
        
        List<ArticleTimestamp> timestamps = TimestampExtractor.extractTimestamps(content);
        
        assertEquals(2, timestamps.size());
        
        // 验证第一个时间戳
        ArticleTimestamp ts1 = timestamps.get(0);
        assertEquals("27", ts1.getLabel());
        assertEquals(27, ts1.getSeconds());
        
        // 验证第二个时间戳
        ArticleTimestamp ts2 = timestamps.get(1);
        assertEquals("90", ts2.getLabel());
        assertEquals(90, ts2.getSeconds());
    }

    @Test
    void testExtractTimestampsWithMultipleLines() {
        String content = "第一行内容 [01:27] 第一个时间戳\n" +
                        "第二行内容 2:30 第二个时间戳\n" +
                        "第三行内容 3分45秒 第三个时间戳";
        
        List<ArticleTimestamp> timestamps = TimestampExtractor.extractTimestamps(content);
        
        assertEquals(3, timestamps.size());
        
        // 验证行号
        assertEquals(1, timestamps.get(0).getLineNo());
        assertEquals(2, timestamps.get(1).getLineNo());
        assertEquals(3, timestamps.get(2).getLineNo());
    }

    @Test
    void testExtractTimestampsWithInvalidTime() {
        String content = "无效时间 [70:80] 另一个无效时间 [1:70]";
        
        List<ArticleTimestamp> timestamps = TimestampExtractor.extractTimestamps(content);
        
        // 70:80 无效（秒数超过59），1:70 无效（秒数超过59）
        assertEquals(0, timestamps.size());
    }

    @Test
    void testExtractTimestampsWithMixedValidAndInvalid() {
        String content = "有效时间 [01:27] 无效时间 [1:70] 另一个有效时间 [2:30]";
        
        List<ArticleTimestamp> timestamps = TimestampExtractor.extractTimestamps(content);
        
        // 只应该提取两个有效时间戳
        assertEquals(2, timestamps.size());
        assertEquals("01:27", timestamps.get(0).getLabel());
        assertEquals("02:30", timestamps.get(1).getLabel());
    }

    @Test
    void testExtractTimestampsEmptyContent() {
        String content = "";
        
        List<ArticleTimestamp> timestamps = TimestampExtractor.extractTimestamps(content);
        
        assertTrue(timestamps.isEmpty());
    }

    @Test
    void testExtractTimestampsNullContent() {
        List<ArticleTimestamp> timestamps = TimestampExtractor.extractTimestamps(null);
        
        assertTrue(timestamps.isEmpty());
    }

    @Test
    void testSecondsToLabel() {
        assertEquals("00:00", TimestampExtractor.secondsToLabel(0));
        assertEquals("01:27", TimestampExtractor.secondsToLabel(87));
        assertEquals("02:30", TimestampExtractor.secondsToLabel(150));
        assertEquals("10:05", TimestampExtractor.secondsToLabel(605));
        assertEquals("27", TimestampExtractor.secondsToLabel(27));
        assertEquals("00:00", TimestampExtractor.secondsToLabel(-1));
    }

    @Test
    void testLabelToSeconds() {
        assertEquals(87, TimestampExtractor.labelToSeconds("01:27"));
        assertEquals(150, TimestampExtractor.labelToSeconds("2:30"));
        assertEquals(60, TimestampExtractor.labelToSeconds("1:00"));
        assertEquals(27, TimestampExtractor.labelToSeconds("27"));
        assertEquals(3600, TimestampExtractor.labelToSeconds("60:00"));
        
        // 无效标签
        assertEquals(-1, TimestampExtractor.labelToSeconds("invalid"));
        assertEquals(-1, TimestampExtractor.labelToSeconds("70:80"));
        assertEquals(-1, TimestampExtractor.labelToSeconds(""));
        assertEquals(-1, TimestampExtractor.labelToSeconds(null));
    }

    @Test
    void testExcerptExtraction() {
        String content = "这是一个很长的内容，其中包含了一个时间戳 [01:27] 在这个位置，然后继续其他内容。";
        
        List<ArticleTimestamp> timestamps = TimestampExtractor.extractTimestamps(content);
        
        assertEquals(1, timestamps.size());
        ArticleTimestamp timestamp = timestamps.get(0);
        
        assertNotNull(timestamp.getExcerpt());
        assertTrue(timestamp.getExcerpt().contains("时间戳"));
        assertTrue(timestamp.getExcerpt().contains("01:27"));
        assertTrue(timestamp.getExcerpt().length() <= 106); // 100 + "..." 的长度
    }
}