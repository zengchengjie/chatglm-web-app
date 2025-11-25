package com.chatglm.webapp.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class RateLimiter {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 基于令牌桶算法的限流
     * @param key 限流键（如用户ID或IP）
     * @param capacity 桶容量
     * @param refillRate 每秒补充的令牌数
     * @param timeUnit 时间单位
     * @return 是否允许通过
     */
    public boolean allowRequest(String key, int capacity, int refillRate, TimeUnit timeUnit) {
        String tokenKey = "rate_limit:token:" + key;
        String timestampKey = "rate_limit:timestamp:" + key;
        
        long now = System.currentTimeMillis();
        
        // 获取当前令牌数
        Object tokenObj = redisTemplate.opsForValue().get(tokenKey);
        int currentTokens = tokenObj != null ? (int) tokenObj : capacity;
        
        // 获取上次更新时间
        Object timestampObj = redisTemplate.opsForValue().get(timestampKey);
        long lastRefill = timestampObj != null ? (long) timestampObj : now;
        
        // 计算应该补充的令牌数
        long timePassed = now - lastRefill;
        int tokensToAdd = (int) (timePassed * refillRate / timeUnit.toMillis(1));
        
        if (tokensToAdd > 0) {
            currentTokens = Math.min(capacity, currentTokens + tokensToAdd);
            redisTemplate.opsForValue().set(timestampKey, now, 1, TimeUnit.HOURS);
        }
        
        if (currentTokens > 0) {
            // 消耗一个令牌
            redisTemplate.opsForValue().set(tokenKey, currentTokens - 1, 1, TimeUnit.HOURS);
            return true;
        }
        
        return false;
    }
    
    /**
     * 简单计数器限流
     * @param key 限流键
     * @param maxRequests 最大请求数
     * @param timeWindow 时间窗口（秒）
     * @return 是否允许通过
     */
    public boolean allowRequestSimple(String key, int maxRequests, int timeWindow) {
        String counterKey = "rate_limit:counter:" + key;
        
        Long count = redisTemplate.opsForValue().increment(counterKey);
        if (count == 1) {
            // 第一次设置过期时间
            redisTemplate.expire(counterKey, timeWindow, TimeUnit.SECONDS);
        }
        
        return count != null && count <= maxRequests;
    }
    
    /**
     * 获取剩余请求次数
     * @param key 限流键
     * @param maxRequests 最大请求数
     * @return 剩余次数
     */
    public int getRemainingRequests(String key, int maxRequests) {
        String counterKey = "rate_limit:counter:" + key;
        Object countObj = redisTemplate.opsForValue().get(counterKey);
        int count = countObj != null ? (int) countObj : 0;
        return Math.max(0, maxRequests - count);
    }
}