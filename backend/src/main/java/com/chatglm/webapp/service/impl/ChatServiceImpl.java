package com.chatglm.webapp.service.impl;

import com.chatglm.webapp.client.ChatGLMClient;
import com.chatglm.webapp.exception.ChatGLMException;
import com.chatglm.webapp.model.ApiRequest;
import com.chatglm.webapp.model.ChatGLMModel;
import com.chatglm.webapp.model.ChatHistory;
import com.chatglm.webapp.service.ChatHistoryService;
import com.chatglm.webapp.service.ChatService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class ChatServiceImpl implements ChatService {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatServiceImpl.class);
    
    @Autowired
    private ChatGLMClient chatGLMClient;
    
    @Autowired
    private ChatHistoryService chatHistoryService;
    
    private final Counter chatCounter;
    private final Counter streamChatCounter;
    
    public ChatServiceImpl(MeterRegistry meterRegistry) {
        this.chatCounter = Counter.builder("chatglm.chat.requests")
                .description("Number of chat requests")
                .register(meterRegistry);
        this.streamChatCounter = Counter.builder("chatglm.stream.chat.requests")
                .description("Number of stream chat requests")
                .register(meterRegistry);
    }
    
    @Override
    public com.chatglm.webapp.model.ApiResponse<String> chat(ApiRequest request) {
        // 监控计数
        chatCounter.increment();
        
        // 验证请求
        if (request == null) {
            return com.chatglm.webapp.model.ApiResponse.fail(400, "Request cannot be null");
        }
        
        if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            return com.chatglm.webapp.model.ApiResponse.fail(400, "Message cannot be empty");
        }
        
        try {
            // 构建消息列表
            List<ChatGLMModel.Message> messages = buildMessages(request.getMessage(), request.getHistory());
            
            // 调用ChatGLM API
            ChatGLMModel.ChatResponse response = chatGLMClient.chat(messages);
            
            // 获取响应内容
            String reply = response.getFirstReply();
            if (reply == null) {
                return com.chatglm.webapp.model.ApiResponse.fail(500, "No valid response received");
            }
            
            // 保存聊天历史记录
            saveChatHistory(request.getMessage(), reply, "chatglm_turbo");
            
            return com.chatglm.webapp.model.ApiResponse.success(reply);
        } catch (Exception e) {
            logger.error("Error in chat service: {}", e.getMessage(), e);
            return com.chatglm.webapp.model.ApiResponse.fail(500, "Chat service error: " + e.getMessage());
        }
    }
    
    @Override
    public void streamChat(ApiRequest request, final SseEmitter emitter) throws IOException {
        // 监控计数
        streamChatCounter.increment();
        
        // 设置超时时间为60秒，避免连接过早断开
        emitter.onTimeout(() -> {
            logger.info("SSE connection timeout");
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data("连接超时，请重试"));
            } catch (IOException e) {
                logger.error("Error sending timeout message: {}", e.getMessage(), e);
            }
            emitter.complete();
        });
        
        // 构建消息列表
        List<ChatGLMModel.Message> messages = buildMessages(request.getMessage(), request.getHistory());
        
        // 调用流式API
        chatGLMClient.streamChat(messages, new ChatGLMClient.StreamResponseHandler() {
            @Override
            public void onMessage(String content) {
                try {
                    // 使用标准的SSE事件格式发送消息内容
                    emitter.send(SseEmitter.event()
                            .name("message")
                            .data(content));
                } catch (IOException e) {
                    logger.error("Error sending SSE message: {}", e.getMessage(), e);
                    try {
                        emitter.completeWithError(e);
                    } catch (Exception ex) {
                        logger.error("Error completing SSE with error: {}", ex.getMessage(), ex);
                    }
                }
            }

            @Override
            public void onComplete() {
                try {
                    // 发送完成消息并结束连接
                    emitter.send(SseEmitter.event()
                            .name("done")
                            .data("[DONE]"));
                    emitter.complete();
                    logger.info("SSE connection completed successfully");
                    
                    // 保存流式聊天的完整响应（这里简化处理，实际应该收集所有流式内容）
                    saveChatHistory(request.getMessage(), "[流式响应]", "chatglm_turbo");
                } catch (IOException e) {
                    logger.error("Error sending complete message: {}", e.getMessage(), e);
                    try {
                        emitter.completeWithError(e);
                    } catch (Exception ex) {
                        logger.error("Error completing SSE: {}", ex.getMessage(), ex);
                    }
                }
            }

            @Override
            public void onError(Throwable error) {
                logger.error("Stream chat error: {}", error.getMessage(), error);
                try {
                    // 发送错误消息
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("Error: " + error.getMessage()));
                    emitter.completeWithError(error);
                } catch (Exception ex) {
                    logger.error("Error completing SSE with error: {}", ex.getMessage(), ex);
                }
            }
        });
    }
    
    @Override
    public List<ChatGLMModel.Message> buildMessages(String message, List<ChatGLMModel.Message> history) {
        List<ChatGLMModel.Message> messages = new ArrayList<>();
        
        // 添加系统消息
        messages.add(new ChatGLMModel.Message("system", "你是一个智能助手，可以回答各种问题，请使用简洁、友好的语言进行回复。"));
        
        // 添加历史消息（如果有）
        if (history != null && !history.isEmpty()) {
            messages.addAll(history);
        }
        
        // 添加当前用户消息
        messages.add(new ChatGLMModel.Message("user", message));
        
        return messages;
    }
    
    /**
     * 保存聊天历史记录
     */
    private void saveChatHistory(String userMessage, String aiResponse, String model) {
        try {
            // 获取当前用户信息（简化实现）
            Long userId = getCurrentUserId();
            String sessionId = generateSessionId();
            
            ChatHistory chatHistory = new ChatHistory(userId, sessionId, userMessage, aiResponse, model);
            chatHistoryService.saveChatHistory(chatHistory);
        } catch (Exception e) {
            logger.warn("Failed to save chat history: {}", e.getMessage());
        }
    }
    
    /**
     * 获取当前用户ID（简化实现）
     */
    private Long getCurrentUserId() {
        // 这里应该从SecurityContext中获取当前用户信息
        // 简化实现，返回默认用户ID
        return 1L;
    }
    
    /**
     * 生成会话ID
     */
    private String generateSessionId() {
        return "session_" + System.currentTimeMillis();
    }
}