package com.chatglm.webapp.client;

import com.chatglm.webapp.config.QwenConfig;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class QwenClientImpl {

    private static final Logger logger = LoggerFactory.getLogger(QwenClientImpl.class);

    @Autowired
    private QwenConfig config;

    @Autowired
    private ObjectMapper objectMapper;

    // 普通同步调用 - 真实API调用
    public ChatGLMModel.ChatResponse chat(List<ChatGLMModel.Message> messages) {
        try {
            logger.info("Sending chat request to Qwen API: {}", config.getBaseUrl());

            // 创建请求体 - 适配通义千问API格式
            Map<String, Object> request = new HashMap<>();
            request.put("model", config.getModelName());

            // 转换消息格式
            Map<String, Object> input = new HashMap<>();
            input.put("messages", convertMessages(messages));
            request.put("input", input);

            // 添加参数
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("temperature", config.getTemperature());
            parameters.put("top_p", config.getTopP());
            parameters.put("max_tokens", config.getMaxTokens());
            request.put("parameters", parameters);

            // 创建RestTemplate并设置超时
            RestTemplate restTemplate = new RestTemplate(createRequestFactory());

            // 设置请求头 - 通义千问使用不同的认证方式
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            headers.set("Authorization", "Bearer " + config.getApiKey());
            headers.set("X-DashScope-SSE", "disable");

            // 创建请求实体
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(request, headers);

            // 发送请求
            ResponseEntity<Map> responseEntity =
                restTemplate.exchange(config.getBaseUrl(), HttpMethod.POST, requestEntity, Map.class);

            if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
                logger.info("Qwen API request successful");
                // 转换响应格式为ChatGLM格式
                return convertQwenResponseToChatGLM(responseEntity.getBody());
            } else {
                logger.error("API request failed with status: {}", responseEntity.getStatusCode());
                logger.warn("Falling back to simulation mode due to API error");
                return useSimulationModeForChat(messages);
            }

        } catch (Exception e) {
            logger.error("Error in Qwen API request: {}", e.getMessage(), e);
            logger.warn("Falling back to simulation mode due to API error");
            return useSimulationModeForChat(messages);
        }
    }

    // 流式调用，使用HTTP长连接实现
    public CompletableFuture<Void> streamChat(List<ChatGLMModel.Message> messages, StreamResponseHandler handler) {
        logger.info("Starting stream chat with Qwen API");

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
                // 创建请求体 - 适配通义千问API格式
                Map<String, Object> request = new HashMap<>();
                request.put("model", config.getModelName());

                // 转换消息格式
                Map<String, Object> input = new HashMap<>();
                input.put("messages", convertMessages(messages));
                request.put("input", input);

                // 添加参数
                Map<String, Object> parameters = new HashMap<>();
                parameters.put("temperature", config.getTemperature());
                parameters.put("top_p", config.getTopP());
                parameters.put("max_tokens", config.getMaxTokens());
                parameters.put("incremental_output", true); // 启用增量输出
                request.put("parameters", parameters);

                // 创建URL连接
                URL url = new URL(config.getBaseUrl());
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + config.getApiKey());
                connection.setRequestProperty("X-DashScope-SSE", "enable"); // 启用SSE
                connection.setDoOutput(true);
                connection.setConnectTimeout(config.getTimeout());
                connection.setReadTimeout(config.getTimeout());

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
                        if (line.startsWith("data:")) {
                            String data = line.substring(5).trim();
                            if (data.equals("[DONE]")) {
                                break;
                            }

                            // 解析响应数据
                            Map<String, Object> chunk = objectMapper.readValue(data, Map.class);
                            if (chunk != null && chunk.containsKey("output")) {
                                Map<String, Object> output = (Map<String, Object>) chunk.get("output");
                                if (output.containsKey("text")) {
                                    // 发送增量内容
                                    safeHandler.onMessage(output.get("text").toString());
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
    }

    // 创建请求工厂，设置超时时间
    private ClientHttpRequestFactory createRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(config.getTimeout());
        factory.setReadTimeout(config.getTimeout());
        return factory;
    }

    // 转换消息格式以适配通义千问API
    private List<Map<String, String>> convertMessages(List<ChatGLMModel.Message> messages) {
        return messages.stream()
                .map(msg -> {
                    Map<String, String> map = new HashMap<>();
                    map.put("role", msg.getRole());
                    map.put("content", msg.getContent());
                    return map;
                })
                .toList();
    }

    // 将通义千问API响应转换为ChatGLM格式
    private ChatGLMModel.ChatResponse convertQwenResponseToChatGLM(Map<String, Object> qwenResponse) {
        ChatGLMModel.ChatResponse chatResponse = new ChatGLMModel.ChatResponse();

        try {
            if (qwenResponse.containsKey("output")) {
                Map<String, Object> output = (Map<String, Object>) qwenResponse.get("output");
                if (output.containsKey("text")) {
                    String content = output.get("text").toString();

                    ChatGLMModel.Choice choice = new ChatGLMModel.Choice();
                    ChatGLMModel.Message message = new ChatGLMModel.Message();
                    message.setRole("assistant");
                    message.setContent(content);
                    choice.setMessage(message);
                    chatResponse.setChoices(List.of(choice));
                }
            }

            // 设置其他属性
            if (qwenResponse.containsKey("request_id")) {
                chatResponse.setId(qwenResponse.get("request_id").toString());
            }

            if (qwenResponse.containsKey("usage")) {
                Map<String, Object> usage = (Map<String, Object>) qwenResponse.get("usage");
                ChatGLMModel.Usage chatUsage = new ChatGLMModel.Usage();

                if (usage.containsKey("input_tokens")) {
                    chatUsage.setPromptTokens(((Number) usage.get("input_tokens")).intValue());
                }

                if (usage.containsKey("output_tokens")) {
                    chatUsage.setCompletionTokens(((Number) usage.get("output_tokens")).intValue());
                }

                if (usage.containsKey("total_tokens")) {
                    chatUsage.setTotalTokens(((Number) usage.get("total_tokens")).intValue());
                }

                chatResponse.setUsage(chatUsage);
            }
        } catch (Exception e) {
            logger.error("Error converting Qwen response: {}", e.getMessage(), e);
        }

        return chatResponse;
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
                String response = "你好！我是通义千问智能助手。";
                if (userMessage.contains("你好") || userMessage.contains("hello") || userMessage.contains("hi")) {
                    response = "你好！我是通义千问智能助手，很高兴为你服务！";
                } else if (userMessage.contains("名字") || userMessage.contains("name")) {
                    response = "我是通义千问智能助手，由阿里云开发。";
                } else if (userMessage.contains("帮助") || userMessage.contains("help")) {
                    response = "我可以回答各种问题、提供信息、协助解决问题等。请告诉我你需要什么帮助？";
                } else {
                    response = "我收到了你的消息：" + userMessage + "。这是一个演示模式，实际使用时我会调用通义千问API来提供更准确的回答。";
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
        String response = "你好！我是通义千问智能助手。";
        if (userMessage.contains("你好") || userMessage.contains("hello") || userMessage.contains("hi")) {
            response = "你好！我是通义千问智能助手，很高兴为你服务！";
        } else if (userMessage.contains("名字") || userMessage.contains("name")) {
            response = "我是通义千问智能助手，由阿里云开发。";
        } else if (userMessage.contains("帮助") || userMessage.contains("help")) {
            response = "我可以回答各种问题、提供信息、协助解决问题等。请告诉我你需要什么帮助？";
        } else {
            response = "我收到了你的消息：" + userMessage + "。这是一个演示模式，实际使用时我会调用通义千问API来提供更准确的回答。";
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