package com.chatglm.webapp.service;

import com.chatglm.webapp.client.ChatGLMClient;
import com.chatglm.webapp.model.ApiRequest;
import com.chatglm.webapp.model.ChatGLMModel;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

public interface ChatService {
    
    /**
     * 普通聊天请求
     * @param request 聊天请求
     * @return 聊天响应内容
     */
    com.chatglm.webapp.model.ApiResponse<String> chat(ApiRequest request);
    
    /**
     * 流式聊天请求
     * @param request 聊天请求
     * @param emitter SSE发送器
     */
    void streamChat(ApiRequest request, SseEmitter emitter) throws IOException;
    
    /**
     * 构建聊天消息列表
     * @param message 用户消息
     * @param history 历史消息
     * @return 格式化后的消息列表
     */
    List<ChatGLMModel.Message> buildMessages(String message, List<ChatGLMModel.Message> history);
}