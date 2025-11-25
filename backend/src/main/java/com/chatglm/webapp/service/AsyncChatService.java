package com.chatglm.webapp.service;

import com.chatglm.webapp.model.ChatMessage;

/**
 * 异步聊天服务接口
 * 用于处理高并发聊天请求
 */
public interface AsyncChatService {

    /**
     * 发送聊天消息到消息队列
     * @param chatMessage 聊天消息
     * @return 消息ID
     */
    String sendChatMessage(ChatMessage chatMessage);

    /**
     * 处理聊天消息
     * @param chatMessage 聊天消息
     */
    void processChatMessage(ChatMessage chatMessage);

    /**
     * 获取消息处理状态
     * @param messageId 消息ID
     * @return 消息状态
     */
    ChatMessage.MessageStatus getMessageStatus(String messageId);

    /**
     * 获取处理结果
     * @param messageId 消息ID
     * @return 处理结果
     */
    ChatMessage getMessageResult(String messageId);
}