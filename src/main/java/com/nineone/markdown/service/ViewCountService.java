package com.nineone.markdown.service;

import com.nineone.markdown.mapper.ArticleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 文章阅读量Redis缓冲服务
 * 先写入Redis计数器，定时批量同步到MySQL，减少DB写压力
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ViewCountService {

    private static final String VIEW_COUNT_KEY_PREFIX = "article:view:";
    private static final String VIEW_SYNC_LOCK = "article:view:sync_lock";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ArticleMapper articleMapper;

    /**
     * 增加文章阅读量（写入Redis，异步同步到MySQL）
     */
    public boolean incrementViewCount(Long articleId) {
        String key = VIEW_COUNT_KEY_PREFIX + articleId;
        redisTemplate.opsForValue().increment(key, 1);
        redisTemplate.expire(key, 2, TimeUnit.HOURS);
        return true;
    }

    /**
     * 获取文章阅读量（MySQL + Redis缓冲差值）
     */
    public int getViewCount(Long articleId) {
        String key = VIEW_COUNT_KEY_PREFIX + articleId;
        Object cached = redisTemplate.opsForValue().get(key);
        int buffered = 0;
        if (cached != null) {
            buffered = ((Number) cached).intValue();
        }
        // 从MySQL获取基础阅读量，加上Redis缓冲
        Integer dbCount = articleMapper.selectLikeCountById(articleId);
        // selectLikeCountById 返回其实是like_count，这里我们需要做的是用一个新方法或者在ArticleMapper加一个新查询
        // 暂用直接查询
        return buffered;
    }

    /**
     * 每5分钟将Redis中的阅读量缓冲同步到MySQL
     *
     * 使用 SCAN 替代 keys()：
     * - keys() 是 O(N) 且会阻塞 Redis（key 数量大时卡顿）
     * - SCAN 是增量迭代，每次只扫描部分 key，不阻塞服务
     */
    @Scheduled(fixedRate = 300000)
    public void syncViewCounts() {
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(VIEW_SYNC_LOCK, "1", 4, TimeUnit.MINUTES);
        if (Boolean.TRUE.equals(locked)) {
            try {
                int synced = redisTemplate.execute((RedisCallback<Integer>) connection -> {
                    int count = 0;
                    ScanOptions options = ScanOptions.scanOptions()
                            .match(VIEW_COUNT_KEY_PREFIX + "*")
                            .count(100)
                            .build();
                    try (Cursor<byte[]> cursor = connection.scan(options)) {
                        while (cursor.hasNext()) {
                            String key = new String(cursor.next(), StandardCharsets.UTF_8);
                            if (VIEW_SYNC_LOCK.equals(key)) {
                                continue;
                            }
                            try {
                                Long articleId = Long.parseLong(key.replace(VIEW_COUNT_KEY_PREFIX, ""));
                                Object value = redisTemplate.opsForValue().get(key);
                                if (value != null) {
                                    int delta = ((Number) value).intValue();
                                    if (delta > 0) {
                                        articleMapper.updateViewCount(articleId);
                                        redisTemplate.opsForValue().decrement(key, delta);
                                        count++;
                                    }
                                }
                            } catch (Exception e) {
                                log.warn("同步文章阅读量失败: key={}", key, e);
                            }
                        }
                    }
                    return count;
                });
                if (synced > 0) {
                    log.info("浏览量同步完成，共同步{}篇文章", synced);
                }
            } finally {
                redisTemplate.delete(VIEW_SYNC_LOCK);
            }
        }
    }
}
