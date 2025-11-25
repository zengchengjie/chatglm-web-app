package com.chatglm.webapp.model;

import java.util.List;

public class ApiRequest {
    
    private String message;
    private List<ChatGLMModel.Message> history;
    private boolean stream = false;

    public ApiRequest() {
    }

    public ApiRequest(String message) {
        this.message = message;
    }

    public ApiRequest(String message, List<ChatGLMModel.Message> history, boolean stream) {
        this.message = message;
        this.history = history;
        this.stream = stream;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<ChatGLMModel.Message> getHistory() {
        return history;
    }

    public void setHistory(List<ChatGLMModel.Message> history) {
        this.history = history;
    }

    public boolean isStream() {
        return stream;
    }

    public void setStream(boolean stream) {
        this.stream = stream;
    }
}