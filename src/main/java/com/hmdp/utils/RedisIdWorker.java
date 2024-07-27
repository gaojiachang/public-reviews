package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;


@Component
public class RedisIdWorker {
    /**
     * 初始时间戳
     */
    private static final Long BEGIN_TIMESTAMP = 1640995200L;
    /**
     * 序列号位数
     */
    private static final Integer COUNT_BITS = 32;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public Long nextId(String keyPrefix) { // 1位符号位 31位时间戳 32位序列号
        //生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;
        //生成序列号
        //生成当前日期 精确到天
        String today = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        //自增长
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + today);
        //拼接并返回
        return timestamp << COUNT_BITS | count;
    }
}
