package com.chatglm.webapp.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 聊天消息实体
 * 用于消息队列传输
 */
public class ChatMessage implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 消息ID
     */
    private String messageId;
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 用户消息内容
     */
    private String content;
    
    /**
     * 会话ID
     */
    private String sessionId;
    
    /**
     * 历史对话记录
     */
    private String history;
    
    /**
     * 是否启用流式响应
     */
    private boolean stream;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 消息状态
     */
    private MessageStatus status;
    
    /**
     * AI回复内容
     */
    private String aiResponse;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 消息状态枚举
     */
    public enum MessageStatus {
        PENDING,    // 待处理
        PROCESSING, // 处理中
        COMPLETED,  // 已完成
        FAILED      // 失败
    }
    
    public ChatMessage() {
        this.createTime = LocalDateTime.now();
        this.status = MessageStatus.PENDING;
    }
    
    // Getter and Setter methods
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    
    public String getHistory() { return history; }
    public void setHistory(String history) { this.history = history; }
    
    public boolean isStream() { return stream; }
    public void setStream(boolean stream) { this.stream = stream; }
    
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    
    public MessageStatus getStatus() { return status; }
    public void setStatus(MessageStatus status) { this.status = status; }
    
    public String getAiResponse() { return aiResponse; }
    public void setAiResponse(String aiResponse) { this.aiResponse = aiResponse; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}