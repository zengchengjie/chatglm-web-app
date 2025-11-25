package com.chatglm.webapp.service.impl;

import com.chatglm.webapp.model.ChatHistory;
import com.chatglm.webapp.service.ChatHistoryService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class ChatHistoryServiceImpl implements ChatHistoryService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    public ChatHistoryServiceImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    @Override
    public void saveChatHistory(ChatHistory chatHistory) {
        // 使用Redis存储聊天历史（生产环境建议使用MySQL）
        String key = "chat:history:" + chatHistory.getUserId() + ":" + chatHistory.getSessionId();
        redisTemplate.opsForList().rightPush(key, chatHistory);
        
        // 设置过期时间（7天）
        redisTemplate.expire(key, 7, TimeUnit.DAYS);
        
        // 更新用户使用量统计
        String usageKey = "chat:usage:" + chatHistory.getUserId();
        redisTemplate.opsForValue().increment(usageKey);
    }
    
    @Override
    public List<ChatHistory> getChatHistoryByUserId(Long userId, int limit) {
        List<ChatHistory> result = new ArrayList<>();
        
        // 获取用户的所有会话
        String pattern = "chat:history:" + userId + ":*";
        // 这里简化实现，实际应该使用Redis的SCAN命令
        
        return result;
    }
    
    @Override
    public List<ChatHistory> getChatHistoryBySessionId(String sessionId) {
        // 简化实现
        return new ArrayList<>();
    }
    
    @Override
    public void deleteChatHistoryByUserId(Long userId) {
        // 删除用户的所有聊天记录
        String pattern = "chat:history:" + userId + ":*";
        // 这里简化实现
    }
    
    @Override
    public Long countUserUsage(Long userId) {
        String usageKey = "chat:usage:" + userId;
        Object usage = redisTemplate.opsForValue().get(usageKey);
        return usage != null ? Long.valueOf(usage.toString()) : 0L;
    }
}