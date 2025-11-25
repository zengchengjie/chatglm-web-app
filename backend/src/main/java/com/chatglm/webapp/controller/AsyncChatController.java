package com.chatglm.webapp.controller;

import com.chatglm.webapp.model.ApiRequest;
import com.chatglm.webapp.model.ApiResponse;
import com.chatglm.webapp.model.ChatMessage;
import com.chatglm.webapp.service.AsyncChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.UUID;

/**
 * 异步聊天控制器
 * 用于处理高并发聊天请求，通过消息队列实现异步处理
 */
@Slf4j
@RestController
@RequestMapping("/async-chat")
public class AsyncChatController {

    @Autowired
    private AsyncChatService asyncChatService;

    /**
     * 异步聊天接口
     * @param request 聊天请求
     * @return 消息ID
     */
    @PostMapping("/completions")
    public ResponseEntity<ApiResponse<String>> asyncChat(@RequestBody ApiRequest request) {
        log.info("Received async chat request: {}", request.getMessage());
        
        // 验证请求参数
        if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "Message cannot be empty"));
        }
        
        try {
            // 创建聊天消息
            ChatMessage chatMessage = new ChatMessage();
            chatMessage.setUserId(getCurrentUserId()); // 从认证信息中获取用户ID
            chatMessage.setContent(request.getMessage());
            chatMessage.setSessionId(generateSessionId());
            chatMessage.setHistory(request.getHistory() != null ? request.getHistory().toString() : "");
            chatMessage.setStream(request.isStream());
            
            // 发送到消息队列
            String messageId = asyncChatService.sendChatMessage(chatMessage);
            
            log.info("Async chat message sent successfully, messageId: {}", messageId);
            return ResponseEntity.ok(ApiResponse.success(messageId));
            
        } catch (Exception e) {
            log.error("Error processing async chat request: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(500, "Failed to process async chat request"));
        }
    }

    /**
     * 获取消息状态
     * @param messageId 消息ID
     * @return 消息状态
     */
    @GetMapping("/status/{messageId}")
    public ResponseEntity<ApiResponse<String>> getMessageStatus(@PathVariable String messageId) {
        log.info("Getting message status for: {}", messageId);
        
        try {
            ChatMessage.MessageStatus status = asyncChatService.getMessageStatus(messageId);
            
            if (status == null) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(ApiResponse.success(status.name()));
            
        } catch (Exception e) {
            log.error("Error getting message status: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(500, "Failed to get message status"));
        }
    }

    /**
     * 获取消息结果
     * @param messageId 消息ID
     * @return 消息结果
     */
    @GetMapping("/result/{messageId}")
    public ResponseEntity<ApiResponse<ChatMessage>> getMessageResult(@PathVariable String messageId) {
        log.info("Getting message result for: {}", messageId);
        
        try {
            ChatMessage chatMessage = asyncChatService.getMessageResult(messageId);
            
            if (chatMessage == null) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(ApiResponse.success(chatMessage));
            
        } catch (Exception e) {
            log.error("Error getting message result: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(500, "Failed to get message result"));
        }
    }

    /**
     * 轮询获取消息结果（长轮询）
     * @param messageId 消息ID
     * @param timeout 超时时间（秒）
     * @return 消息结果
     */
    @GetMapping("/poll/{messageId}")
    public ResponseEntity<ApiResponse<Object>> pollMessageResult(
            @PathVariable String messageId,
            @RequestParam(defaultValue = "30") int timeout) {
        log.info("Polling message result for: {}, timeout: {}s", messageId, timeout);
        
        try {
            long startTime = System.currentTimeMillis();
            long timeoutMillis = timeout * 1000L;
            
            while (System.currentTimeMillis() - startTime < timeoutMillis) {
                ChatMessage chatMessage = asyncChatService.getMessageResult(messageId);
                
                if (chatMessage != null && 
                    (chatMessage.getStatus() == ChatMessage.MessageStatus.COMPLETED || 
                     chatMessage.getStatus() == ChatMessage.MessageStatus.FAILED)) {
                    return ResponseEntity.ok(ApiResponse.success(chatMessage));
                }
                
                // 等待1秒后重试
                Thread.sleep(1000);
            }
            
            // 超时返回
            return ResponseEntity.ok(ApiResponse.success(
                "Request timeout, please check status later"));
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Polling interrupted: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(500, "Polling interrupted"));
        } catch (Exception e) {
            log.error("Error polling message result: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(500, "Failed to poll message result"));
        }
    }

    /**
     * 取消消息处理
     * @param messageId 消息ID
     * @return 取消结果
     */
    @PostMapping("/cancel/{messageId}")
    public ResponseEntity<ApiResponse<String>> cancelMessage(@PathVariable String messageId) {
        log.info("Canceling message: {}", messageId);
        
        try {
            // 这里可以实现取消逻辑，比如设置消息状态为取消
            // 注意：RabbitMQ消息一旦被消费就无法取消，这里主要是标记状态
            
            return ResponseEntity.ok(ApiResponse.success("Message cancellation requested"));
            
        } catch (Exception e) {
            log.error("Error canceling message: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(500, "Failed to cancel message"));
        }
    }

    /**
     * 获取当前用户ID（模拟实现）
     */
    private String getCurrentUserId() {
        // 在实际应用中，这里应该从JWT token或session中获取用户ID
        // 这里使用模拟数据
        return "user-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 生成会话ID
     */
    private String generateSessionId() {
        return "session-" + UUID.randomUUID().toString().substring(0, 8);
    }
}