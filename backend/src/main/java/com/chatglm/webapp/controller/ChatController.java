package com.chatglm.webapp.controller;

import com.chatglm.webapp.annotation.RateLimit;
import com.chatglm.webapp.model.ApiRequest;
import com.chatglm.webapp.model.ApiResponse;
import com.chatglm.webapp.service.AsyncChatService;
import com.chatglm.webapp.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@RestController
@RequestMapping("/chat")
public class ChatController {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);
    
    @Autowired
    private ChatService chatService;
    
    /**
     * 普通聊天接口
     * @param request 聊天请求
     * @return 聊天响应
     */
    @PostMapping("/completions")
    @RateLimit(keyType = RateLimit.KeyType.USER, maxRequests = 5, timeWindow = 60, message = "聊天请求过于频繁，请稍后再试")
    public ApiResponse<String> chat(@RequestBody ApiRequest request) {
        logger.info("Received chat request: {}", request.getMessage());
        ApiResponse<String> response = chatService.chat(request);
        logger.info("Chat response: {}", response.getData());
        return response;
    }
    
    /**
     * 流式聊天接口
     * @param request 聊天请求
     * @return SSE发送器
     */
    @PostMapping("/stream")
    public SseEmitter streamChat(@RequestBody ApiRequest request) {
        logger.info("Received stream chat request: {}", request.getMessage());
        
        // 创建SSE发送器，设置60秒超时时间
        SseEmitter emitter = new SseEmitter(60000L);
        
        // 处理连接超时
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
        
        // 处理连接完成
        emitter.onCompletion(() -> {
            logger.info("SSE connection completed");
        });
        
        // 处理连接错误
        emitter.onError(error -> {
            logger.error("SSE connection error: {}", error.getMessage(), error);
            // 错误已在服务层处理，这里不再重复处理
        });
        
        try {
            // 启动流式聊天
            chatService.streamChat(request, emitter);
        } catch (IOException e) {
            logger.error("Error starting stream chat: {}", e.getMessage(), e);
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data("启动流式聊天失败: " + e.getMessage()));
                emitter.completeWithError(e);
            } catch (IOException ex) {
                logger.error("Error sending error message: {}", ex.getMessage(), ex);
                emitter.completeWithError(ex);
            }
        }
        
        return emitter;
    }
    
    /**
     * 健康检查接口
     * @return 服务状态
     */
    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.success("ChatGLM backend service is healthy");
    }
}