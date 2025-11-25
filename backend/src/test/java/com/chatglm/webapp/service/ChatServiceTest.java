package com.chatglm.webapp.service;

import com.chatglm.webapp.client.ChatGLMClient;
import com.chatglm.webapp.model.ApiRequest;
import com.chatglm.webapp.model.ApiResponse;
import com.chatglm.webapp.model.ChatGLMModel;
import com.chatglm.webapp.service.impl.ChatServiceImpl;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * ChatService单元测试
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatGLMClient chatGLMClient;

    private MeterRegistry meterRegistry;

    private ChatServiceImpl chatService;

    private ApiRequest apiRequest;
    private ApiResponse<String> apiResponse;
    private ChatGLMModel.ChatResponse chatResponse;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        
        // 使用反射设置私有字段
        chatService = new ChatServiceImpl(meterRegistry);
        try {
            java.lang.reflect.Field clientField = ChatServiceImpl.class.getDeclaredField("chatGLMClient");
            clientField.setAccessible(true);
            clientField.set(chatService, chatGLMClient);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set chatGLMClient field", e);
        }

        apiRequest = new ApiRequest();
        apiRequest.setMessage("Hello, ChatGLM!");
        apiRequest.setStream(false);

        apiResponse = new ApiResponse<>();
        apiResponse.setCode(200);
        apiResponse.setMessage("success");
        apiResponse.setData("Hello! How can I help you today?");
        
        // 创建模拟的ChatGLM响应
        chatResponse = new ChatGLMModel.ChatResponse();
        ChatGLMModel.Choice choice = new ChatGLMModel.Choice();
        ChatGLMModel.Message message = new ChatGLMModel.Message();
        message.setRole("assistant");
        message.setContent("Hello! How can I help you today?");
        choice.setMessage(message);
        chatResponse.setChoices(List.of(choice));
    }

    @Test
    void testChat_Success() {
        // 模拟API调用成功
        when(chatGLMClient.chat(anyList())).thenReturn(chatResponse);

        ApiResponse<String> result = chatService.chat(apiRequest);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals("Hello! How can I help you today?", result.getData());
        
        verify(chatGLMClient, times(1)).chat(anyList());
    }

    @Test
    void testChat_WithHistory() {
        // 设置历史对话
        apiRequest.setHistory(List.of(new ChatGLMModel.Message("user", "Hi")));
        
        when(chatGLMClient.chat(anyList())).thenReturn(chatResponse);

        ApiResponse<String> result = chatService.chat(apiRequest);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        verify(chatGLMClient, times(1)).chat(anyList());
    }

    @Test
    void testChat_StreamMode() {
        // 测试流式模式
        apiRequest.setStream(true);
        
        when(chatGLMClient.chat(anyList())).thenReturn(chatResponse);

        ApiResponse<String> result = chatService.chat(apiRequest);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        verify(chatGLMClient, times(1)).chat(anyList());
    }

    @Test
    void testChat_ApiError() {
        // 模拟API调用失败
        when(chatGLMClient.chat(anyList())).thenThrow(new RuntimeException("API Error"));

        ApiResponse<String> result = chatService.chat(apiRequest);

        assertNotNull(result);
        assertEquals(500, result.getCode());
        assertTrue(result.getMessage().contains("Chat service error"));
        
        verify(chatGLMClient, times(1)).chat(anyList());
    }

    @Test
    void testChat_NullRequest() {
        // 测试空请求处理
        ApiResponse<String> result = chatService.chat(null);
        
        assertNotNull(result);
        assertEquals(400, result.getCode());
        assertEquals("Request cannot be null", result.getMessage());
    }

    @Test
    void testChat_EmptyMessage() {
        // 测试空消息处理
        apiRequest.setMessage("");
        
        ApiResponse<String> result = chatService.chat(apiRequest);
        
        assertNotNull(result);
        assertEquals(400, result.getCode());
        assertEquals("Message cannot be empty", result.getMessage());
    }
}