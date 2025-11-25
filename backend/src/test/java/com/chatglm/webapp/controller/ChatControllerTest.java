package com.chatglm.webapp.controller;

import com.chatglm.webapp.model.ApiRequest;
import com.chatglm.webapp.model.ApiResponse;
import com.chatglm.webapp.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ChatController集成测试
 */
@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ChatService chatService;

    @InjectMocks
    private ChatController chatController;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(chatController).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void testChatCompletions_Success() throws Exception {
        // 准备测试数据
        ApiRequest apiRequest = new ApiRequest();
        apiRequest.setMessage("Hello, ChatGLM!");
        apiRequest.setStream(false);

        ApiResponse<String> apiResponse = new ApiResponse<>();
        apiResponse.setCode(200);
        apiResponse.setMessage("success");
        apiResponse.setData("Hello! How can I help you today?");

        // 模拟服务调用
        when(chatService.chat(any(ApiRequest.class))).thenReturn(apiResponse);

        // 执行测试
        mockMvc.perform(post("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(apiRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data").value("Hello! How can I help you today?"));
    }

    @Test
    void testChatCompletions_InvalidRequest() throws Exception {
        // 测试无效请求 (发送null内容应该触发400错误)
        mockMvc.perform(post("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testChatCompletions_EmptyMessage() throws Exception {
        // 测试空消息
        ApiRequest apiRequest = new ApiRequest();
        apiRequest.setMessage("");

        ApiResponse<String> apiResponse = new ApiResponse<>();
        apiResponse.setCode(400);
        apiResponse.setMessage("Message cannot be empty");

        when(chatService.chat(any(ApiRequest.class))).thenReturn(apiResponse);

        mockMvc.perform(post("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(apiRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Message cannot be empty"));
    }

    @Test
    void testChatCompletions_StreamMode() throws Exception {
        // 测试流式模式
        ApiRequest apiRequest = new ApiRequest();
        apiRequest.setMessage("Stream test");
        apiRequest.setStream(true);

        ApiResponse<String> apiResponse = new ApiResponse<>();
        apiResponse.setCode(200);
        apiResponse.setMessage("stream started");
        apiResponse.setData("stream-data");

        when(chatService.chat(any(ApiRequest.class))).thenReturn(apiResponse);

        mockMvc.perform(post("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(apiRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("stream started"));
    }

    @Test
    void testHealthCheck() throws Exception {
        mockMvc.perform(get("/chat/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("ChatGLM backend service is healthy"));
    }
}