package com.chatglm.webapp.exception;

public class ChatGLMException extends RuntimeException {
    
    private int code;

    public ChatGLMException(String message) {
        super(message);
        this.code = 500;
    }

    public ChatGLMException(String message, Throwable cause) {
        super(message, cause);
        this.code = 500;
    }

    public ChatGLMException(int code, String message) {
        super(message);
        this.code = code;
    }

    public ChatGLMException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }
}