package com.chatglm.webapp.service.impl;

import com.chatglm.webapp.client.ChatGLMClient;
import com.chatglm.webapp.config.RabbitMQConfig;
import com.chatglm.webapp.model.ApiRequest;
import com.chatglm.webapp.model.ApiResponse;
import com.chatglm.webapp.model.ChatHistory;
import com.chatglm.webapp.model.ChatMessage;
import com.chatglm.webapp.service.AsyncChatService;
import com.chatglm.webapp.service.ChatHistoryService;
import com.chatglm.webapp.service.ChatService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 异步聊天服务实现类
 */
@Service
public class AsyncChatServiceImpl implements AsyncChatService {
    
    private static final Logger log = LoggerFactory.getLogger(AsyncChatServiceImpl.class);

    @Autowired
    private RabbitTemplate rabbitTemplate;
    
    @Autowired
    private ChatGLMClient chatGLMClient;
    
    @Autowired
    private ChatService chatService;
    
    @Autowired
    private ChatHistoryService chatHistoryService;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private static final String MESSAGE_PREFIX = "chat_message:";
    private static final long MESSAGE_EXPIRE_HOURS = 24;

    @Override
    public String sendChatMessage(ChatMessage chatMessage) {
        String messageId = UUID.randomUUID().toString();
        chatMessage.setMessageId(messageId);
        
        // 保存消息到Redis
        saveMessageToRedis(chatMessage);
        
        // 发送到消息队列
        rabbitTemplate.convertAndSend(
            RabbitMQConfig.CHAT_EXCHANGE,
            RabbitMQConfig.CHAT_ROUTING_KEY,
            chatMessage
        );
        
        log.info("消息已发送到队列，消息ID: {}", messageId);
        return messageId;
    }

    @RabbitListener(queues = RabbitMQConfig.CHAT_QUEUE)
    public void processChatMessage(ChatMessage chatMessage) {
        log.info("开始处理消息: {}", chatMessage.getMessageId());
        
        try {
            // 更新消息状态为处理中
            chatMessage.setStatus(ChatMessage.MessageStatus.PROCESSING);
            saveMessageToRedis(chatMessage);
            
            // 构建API请求
            ApiRequest apiRequest = new ApiRequest();
            apiRequest.setMessage(chatMessage.getContent());
            apiRequest.setStream(chatMessage.isStream());
            
            // 调用ChatGLM API
            ApiResponse<String> apiResponse = chatService.chat(apiRequest);
            
            if (apiResponse.getCode() == 200) {
                // 处理成功
                chatMessage.setAiResponse(apiResponse.getData());
                chatMessage.setStatus(ChatMessage.MessageStatus.COMPLETED);
                
                // 保存聊天历史
                ChatHistory chatHistory = new ChatHistory();
                // 将String类型的userId转换为Long类型
                try {
                    Long userId = Long.parseLong(chatMessage.getUserId());
                    chatHistory.setUserId(userId);
                } catch (NumberFormatException e) {
                    log.warn("无法解析用户ID为Long类型: {}", chatMessage.getUserId());
                    chatHistory.setUserId(0L); // 设置默认值
                }
                chatHistory.setUserMessage(chatMessage.getContent());
                chatHistory.setAiResponse(chatMessage.getAiResponse());
                chatHistory.setSessionId(chatMessage.getSessionId());
                chatHistory.setModel("chatglm_turbo");
                chatHistoryService.saveChatHistory(chatHistory);
                
                log.info("消息处理完成: {}", chatMessage.getMessageId());
            } else {
                // 处理失败
                chatMessage.setStatus(ChatMessage.MessageStatus.FAILED);
                chatMessage.setErrorMessage(apiResponse.getMessage());
                log.error("消息处理失败: {}, 错误: {}", chatMessage.getMessageId(), apiResponse.getMessage());
            }
            
        } catch (Exception e) {
            // 异常处理
            chatMessage.setStatus(ChatMessage.MessageStatus.FAILED);
            chatMessage.setErrorMessage(e.getMessage());
            log.error("消息处理异常: {}, 异常: {}", chatMessage.getMessageId(), e.getMessage(), e);
        }
        
        // 更新Redis中的消息状态
        saveMessageToRedis(chatMessage);
    }

    @Override
    public ChatMessage.MessageStatus getMessageStatus(String messageId) {
        ChatMessage chatMessage = getMessageFromRedis(messageId);
        return chatMessage != null ? chatMessage.getStatus() : null;
    }

    @Override
    public ChatMessage getMessageResult(String messageId) {
        return getMessageFromRedis(messageId);
    }

    /**
     * 保存消息到Redis
     */
    private void saveMessageToRedis(ChatMessage chatMessage) {
        try {
            String key = MESSAGE_PREFIX + chatMessage.getMessageId();
            String value = objectMapper.writeValueAsString(chatMessage);
            redisTemplate.opsForValue().set(
                key, 
                value, 
                MESSAGE_EXPIRE_HOURS, 
                TimeUnit.HOURS
            );
        } catch (JsonProcessingException e) {
            log.error("保存消息到Redis失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 从Redis获取消息
     */
    private ChatMessage getMessageFromRedis(String messageId) {
        try {
            String key = MESSAGE_PREFIX + messageId;
            String value = (String) redisTemplate.opsForValue().get(key);
            return value != null ? objectMapper.readValue(value, ChatMessage.class) : null;
        } catch (Exception e) {
            log.error("从Redis获取消息失败: {}", e.getMessage(), e);
            return null;
        }
    }
}