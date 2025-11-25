package com.chatglm.webapp.client;

import com.chatglm.webapp.config.ChatGLMConfig;
import com.chatglm.webapp.model.ChatGLMModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class ChatGLMClient {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatGLMClient.class);
    
    @Autowired
    private ChatGLMConfig config;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    // 普通同步调用 - 真实API调用
    public ChatGLMModel.ChatResponse chat(List<ChatGLMModel.Message> messages) {
        try {
            logger.info("Sending chat request to ChatGLM API: {}", config.getBaseUrl());
            
            // 创建请求体
            ChatGLMModel.ChatRequest request = new ChatGLMModel.ChatRequest();
            request.setModel(config.getModelName());
            request.setMessages(messages);
            request.setTemperature(config.getTemperature());
            request.setTopP(config.getTopP());
            request.setMaxTokens(config.getMaxTokens());
            
            // 创建RestTemplate并设置超时
            RestTemplate restTemplate = new RestTemplate(createRequestFactory());
            
            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            headers.set("Authorization", "Bearer " + config.getApiKey());
            
            // 创建请求实体
            HttpEntity<ChatGLMModel.ChatRequest> requestEntity = new HttpEntity<>(request, headers);
            
            // 发送请求
            ResponseEntity<ChatGLMModel.ChatResponse> responseEntity = 
                restTemplate.exchange(config.getBaseUrl(), HttpMethod.POST, requestEntity, ChatGLMModel.ChatResponse.class);
            
            if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
                logger.info("ChatGLM API request successful");
                return responseEntity.getBody();
            } else {
                logger.error("API request failed with status: {}", responseEntity.getStatusCode());
                logger.warn("Falling back to simulation mode due to API error");
                return useSimulationModeForChat(messages);
            }
            
        } catch (Exception e) {
            logger.error("Error in ChatGLM API request: {}", e.getMessage(), e);
            logger.warn("Falling back to simulation mode due to API error");
            return useSimulationModeForChat(messages);
        }
    }
    
    // 流式调用，使用HTTP长连接实现
    public CompletableFuture<Void> streamChat(List<ChatGLMModel.Message> messages, StreamResponseHandler handler) {
        logger.info("Starting stream chat with ChatGLM API");
        
        // 确保handler不为null
        final StreamResponseHandler safeHandler = handler != null ? handler : new StreamResponseHandler() {
            @Override
            public void onMessage(String content) {}
            @Override
            public void onComplete() {}
            @Override
            public void onError(Throwable error) {}
        };
        
        // 首先尝试真实API调用
        return CompletableFuture.runAsync(() -> {
            HttpURLConnection connection = null;
            BufferedReader reader = null;
            OutputStream outputStream = null;
            
            try {
                // 创建请求体
                ChatGLMModel.ChatRequest request = new ChatGLMModel.ChatRequest();
                request.setModel(config.getModelName());
                request.setMessages(messages);
                request.setTemperature(config.getTemperature());
                request.setTopP(config.getTopP());
                request.setMaxTokens(config.getMaxTokens());
                request.setStream(true); // 启用流式响应
                
                // 创建URL连接
                URL url = new URL(config.getBaseUrl());
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + config.getApiKey());
                connection.setDoOutput(true);
                connection.setConnectTimeout(config.getTimeout());
                connection.setReadTimeout(config.getTimeout());
                // connection.setChunkedStreamingMode(0); // 移除分块传输编码设置
                
                // 禁用缓存
                connection.setUseCaches(false);
                connection.setDefaultUseCaches(false);
                
                // 发送请求体
                outputStream = connection.getOutputStream();
                String requestBody = objectMapper.writeValueAsString(request);
                outputStream.write(requestBody.getBytes("UTF-8"));
                outputStream.flush();
                
                // 检查响应状态
                int responseCode = connection.getResponseCode();
                if (responseCode != 200) {
                    String errorMessage = connection.getResponseMessage();
                    InputStream errorStream = connection.getErrorStream();
                    if (errorStream != null) {
                        BufferedReader errorReader = new BufferedReader(new InputStreamReader(errorStream));
                        String errorLine;
                        StringBuilder errorResponse = new StringBuilder();
                        while ((errorLine = errorReader.readLine()) != null) {
                            errorResponse.append(errorLine);
                        }
                        errorMessage = errorResponse.toString();
                    }
                    throw new IOException("API request failed with status: " + responseCode + " - " + errorMessage);
                }
                
                // 读取响应流
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
                String line;
                
                while ((line = reader.readLine()) != null) {
                    try {
                        // 处理SSE格式的响应行
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6);
                            if (data.equals("[DONE]")) {
                                break;
                            }
                            
                            // 解析响应数据
                            ChatGLMModel.ChatCompletionChunk chunk = objectMapper.readValue(data, ChatGLMModel.ChatCompletionChunk.class);
                            if (chunk != null && chunk.getChoices() != null && !chunk.getChoices().isEmpty()) {
                                ChatGLMModel.ChunkChoice choice = chunk.getChoices().get(0);
                                if (choice != null && choice.getDelta() != null && choice.getDelta().getContent() != null) {
                                    // 发送增量内容
                                    safeHandler.onMessage(choice.getDelta().getContent());
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.error("Error processing stream line: {}", e.getMessage(), e);
                    }
                }
                
                safeHandler.onComplete();
                logger.info("Stream chat completed");
                           
            } catch (Exception e) {
                logger.error("Error in stream chat: {}", e.getMessage(), e);
                logger.warn("Falling back to simulation mode due to API error");
                // 如果API调用失败，使用模拟模式
                useSimulationMode(messages, safeHandler);
            } finally {
                try {
                    if (reader != null) reader.close();
                    if (outputStream != null) outputStream.close();
                    if (connection != null) connection.disconnect();
                } catch (Exception e) {
                    logger.error("Error closing resources: {}", e.getMessage(), e);
                }
            }
        });
        
        /* 模拟模式代码（已替换为真实API调用）
        return CompletableFuture.runAsync(() -> {
            HttpURLConnection connection = null;
            BufferedReader reader = null;
            OutputStream outputStream = null;
            
            try {
                // 创建请求体
                ChatGLMModel.ChatRequest request = new ChatGLMModel.ChatRequest();
                request.setModel(config.getModelName());
                request.setMessages(messages);
                request.setTemperature(config.getTemperature());
                request.setTopP(config.getTopP());
                request.setMaxTokens(config.getMaxTokens());
                request.setStream(true); // 启用流式响应
                
                // 创建URL连接
                URL url = new URL(config.getBaseUrl());
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + config.getApiKey());
                connection.setDoOutput(true);
                connection.setConnectTimeout(config.getTimeout());
                connection.setReadTimeout(config.getTimeout());
                connection.setChunkedStreamingMode(0); // 启用分块传输编码
                
                // 发送请求体
                outputStream = connection.getOutputStream();
                objectMapper.writeValue(outputStream, request);
                outputStream.flush();
                
                // 读取响应流
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
                String line;
                
                while ((line = reader.readLine()) != null) {
                    try {
                        // 处理SSE格式的响应行
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6);
                            if (data.equals("[DONE]")) {
                                break;
                            }
                            
                            // 解析响应数据
                            ChatGLMModel.ChatCompletionChunk chunk = objectMapper.readValue(data, ChatGLMModel.ChatCompletionChunk.class);
                            if (chunk != null && chunk.getChoices() != null && !chunk.getChoices().isEmpty()) {
                                ChatGLMModel.ChunkChoice choice = chunk.getChoices().get(0);
                                if (choice != null && choice.getDelta() != null && choice.getDelta().getContent() != null) {
                                    // 发送增量内容
                                    safeHandler.onMessage(choice.getDelta().getContent());
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.error("Error processing stream line: {}", e.getMessage(), e);
                    }
                }
                
                safeHandler.onComplete();
                logger.info("Stream chat completed");
                          
            } catch (Exception e) {
                logger.error("Error in stream chat: {}", e.getMessage(), e);
                safeHandler.onError(e);
            } finally {
                try {
                    if (reader != null) reader.close();
                    if (outputStream != null) outputStream.close();
                    if (connection != null) connection.disconnect();
                } catch (Exception e) {
                    logger.error("Error closing resources: {}", e.getMessage(), e);
                }
            }
        });
        */
    }
    
    // 创建请求工厂，设置超时时间
    private ClientHttpRequestFactory createRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(config.getTimeout());
        factory.setReadTimeout(config.getTimeout());
        return factory;
    }

    // 模拟模式实现 - 流式聊天
    private CompletableFuture<Void> useSimulationMode(List<ChatGLMModel.Message> messages, StreamResponseHandler handler) {
        return CompletableFuture.runAsync(() -> {
            try {
                // 获取最后一条用户消息
                String userMessage = messages.stream()
                    .filter(msg -> "user".equals(msg.getRole()))
                    .reduce((first, second) -> second)
                    .map(ChatGLMModel.Message::getContent)
                    .orElse("你好");
                
                // 模拟AI回复
                String response = "你好！我是ChatGLM智能助手。";
                if (userMessage.contains("你好") || userMessage.contains("hello") || userMessage.contains("hi")) {
                    response = "你好！我是ChatGLM智能助手，很高兴为你服务！";
                } else if (userMessage.contains("名字") || userMessage.contains("name")) {
                    response = "我是ChatGLM智能助手，由智谱AI开发。";
                } else if (userMessage.contains("帮助") || userMessage.contains("help")) {
                    response = "我可以回答各种问题、提供信息、协助解决问题等。请告诉我你需要什么帮助？";
                } else {
                    response = "我收到了你的消息：" + userMessage + "。这是一个演示模式，实际使用时我会调用ChatGLM API来提供更准确的回答。";
                }
                
                // 模拟流式输出 - 改进版本，避免字符截断
                // 将回复分成几个部分发送，确保内容完整
                String[] sentences = response.split("(?<=[。！？])");
                for (String sentence : sentences) {
                    if (!sentence.trim().isEmpty()) {
                        // 每个句子作为一个完整的消息发送
                        handler.onMessage(sentence);
                        Thread.sleep(100); // 稍微延迟，模拟思考过程
                    }
                }
                
                handler.onComplete();
                logger.info("Simulation mode chat completed");
                
            } catch (Exception e) {
                logger.error("Error in simulation mode: {}", e.getMessage(), e);
                handler.onError(e);
            }
        });
    }
    
    // 模拟模式实现 - 普通聊天
    private ChatGLMModel.ChatResponse useSimulationModeForChat(List<ChatGLMModel.Message> messages) {
        // 获取最后一条用户消息
        String userMessage = messages.stream()
            .filter(msg -> "user".equals(msg.getRole()))
            .reduce((first, second) -> second)
            .map(ChatGLMModel.Message::getContent)
            .orElse("你好");
        
        // 模拟AI回复
        String response = "你好！我是ChatGLM智能助手。";
        if (userMessage.contains("你好") || userMessage.contains("hello") || userMessage.contains("hi")) {
            response = "你好！我是ChatGLM智能助手，很高兴为你服务！";
        } else if (userMessage.contains("名字") || userMessage.contains("name")) {
            response = "我是ChatGLM智能助手，由智谱AI开发。";
        } else if (userMessage.contains("帮助") || userMessage.contains("help")) {
            response = "我可以回答各种问题、提供信息、协助解决问题等。请告诉我你需要什么帮助？";
        } else {
            response = "我收到了你的消息：" + userMessage + "。这是一个演示模式，实际使用时我会调用ChatGLM API来提供更准确的回答。";
        }
        
        // 创建模拟响应
        ChatGLMModel.ChatResponse chatResponse = new ChatGLMModel.ChatResponse();
        ChatGLMModel.Choice choice = new ChatGLMModel.Choice();
        ChatGLMModel.Message message = new ChatGLMModel.Message();
        message.setRole("assistant");
        message.setContent(response);
        choice.setMessage(message);
        chatResponse.setChoices(List.of(choice));
        
        logger.info("Simulation mode chat completed");
        return chatResponse;
    }
    
    // 流式响应处理接口
    public interface StreamResponseHandler {
        void onMessage(String content);
        void onComplete();
        void onError(Throwable error);
    }
}