package com.chatglm.webapp.service;

import com.chatglm.webapp.model.ChatHistory;
import java.util.List;

public interface ChatHistoryService {
    
    /**
     * 保存聊天记录
     */
    void saveChatHistory(ChatHistory chatHistory);
    
    /**
     * 获取用户的聊天历史
     */
    List<ChatHistory> getChatHistoryByUserId(Long userId, int limit);
    
    /**
     * 获取会话的聊天历史
     */
    List<ChatHistory> getChatHistoryBySessionId(String sessionId);
    
    /**
     * 删除用户的聊天历史
     */
    void deleteChatHistoryByUserId(Long userId);
    
    /**
     * 统计用户使用量
     */
    Long countUserUsage(Long userId);
}