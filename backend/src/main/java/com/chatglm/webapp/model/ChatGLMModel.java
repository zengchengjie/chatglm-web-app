package com.chatglm.webapp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class ChatGLMModel {
    
    // Message类定义
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {
        @JsonProperty("role")
        private String role;
        
        @JsonProperty("content")
        private String content;

        public Message() {}

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    // ChatRequest类定义
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChatRequest {
        @JsonProperty("model")
        private String model;
        
        @JsonProperty("messages")
        private List<Message> messages;
        
        @JsonProperty("temperature")
        private double temperature;
        
        @JsonProperty("top_p")
        private double topP;
        
        @JsonProperty("max_tokens")
        private int maxTokens;
        
        @JsonProperty("stream")
        private boolean stream;

        public ChatRequest() {}

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public List<Message> getMessages() {
            return messages;
        }

        public void setMessages(List<Message> messages) {
            this.messages = messages;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public double getTopP() {
            return topP;
        }

        public void setTopP(double topP) {
            this.topP = topP;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        public boolean isStream() {
            return stream;
        }

        public void setStream(boolean stream) {
            this.stream = stream;
        }
    }

    // Choice类定义
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Choice {
        @JsonProperty("index")
        private int index;
        
        @JsonProperty("message")
        private Message message;

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public Message getMessage() {
            return message;
        }

        public void setMessage(Message message) {
            this.message = message;
        }
    }

    // Usage类定义
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private int promptTokens;
        
        @JsonProperty("completion_tokens")
        private int completionTokens;
        
        @JsonProperty("total_tokens")
        private int totalTokens;

        public int getPromptTokens() {
            return promptTokens;
        }

        public void setPromptTokens(int promptTokens) {
            this.promptTokens = promptTokens;
        }

        public int getCompletionTokens() {
            return completionTokens;
        }

        public void setCompletionTokens(int completionTokens) {
            this.completionTokens = completionTokens;
        }

        public int getTotalTokens() {
            return totalTokens;
        }

        public void setTotalTokens(int totalTokens) {
            this.totalTokens = totalTokens;
        }
    }

    // ChatResponse类定义
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChatResponse {
        @JsonProperty("id")
        private String id;
        
        @JsonProperty("object")
        private String object;
        
        @JsonProperty("created")
        private long created;
        
        @JsonProperty("choices")
        private List<Choice> choices;
        
        @JsonProperty("usage")
        private Usage usage;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getObject() {
            return object;
        }

        public void setObject(String object) {
            this.object = object;
        }

        public long getCreated() {
            return created;
        }

        public void setCreated(long created) {
            this.created = created;
        }

        public List<Choice> getChoices() {
            return choices;
        }

        public void setChoices(List<Choice> choices) {
            this.choices = choices;
        }

        public Usage getUsage() {
            return usage;
        }

        public void setUsage(Usage usage) {
            this.usage = usage;
        }
        
        public String getFirstReply() {
            if (choices != null && !choices.isEmpty() && choices.get(0).getMessage() != null) {
                return choices.get(0).getMessage().getContent();
            }
            return null;
        }
    }
    
    // ChatCompletionChunk类定义（用于流式响应）
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChatCompletionChunk {
        @JsonProperty("id")
        private String id;
        
        @JsonProperty("object")
        private String object;
        
        @JsonProperty("created")
        private long created;
        
        @JsonProperty("model")
        private String model;
        
        @JsonProperty("choices")
        private List<ChunkChoice> choices;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getObject() {
            return object;
        }

        public void setObject(String object) {
            this.object = object;
        }

        public long getCreated() {
            return created;
        }

        public void setCreated(long created) {
            this.created = created;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public List<ChunkChoice> getChoices() {
            return choices;
        }

        public void setChoices(List<ChunkChoice> choices) {
            this.choices = choices;
        }
        
        public String getDeltaContent() {
            if (choices != null && !choices.isEmpty() && choices.get(0).getDelta() != null) {
                return choices.get(0).getDelta().getContent();
            }
            return "";
        }
        
        public boolean isFinish() {
            if (choices != null && !choices.isEmpty() && choices.get(0).getFinishReason() != null) {
                return true;
            }
            return false;
        }
    }
    
    // ChunkChoice类定义（用于流式响应）
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChunkChoice {
        @JsonProperty("index")
        private int index;
        
        @JsonProperty("delta")
        private Delta delta;
        
        @JsonProperty("finish_reason")
        private String finishReason;

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public Delta getDelta() {
            return delta;
        }

        public void setDelta(Delta delta) {
            this.delta = delta;
        }

        public String getFinishReason() {
            return finishReason;
        }

        public void setFinishReason(String finishReason) {
            this.finishReason = finishReason;
        }
    }
    
    // Delta类定义（用于流式响应）
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Delta {
        @JsonProperty("role")
        private String role;
        
        @JsonProperty("content")
        private String content;

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}