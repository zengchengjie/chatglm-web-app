package com.chatglm.webapp.exception;

import com.chatglm.webapp.model.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

@ControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ChatGLMException.class)
    @ResponseBody
    public Object handleChatGLMException(ChatGLMException e) {
        logger.error("ChatGLM API Error: {}", e.getMessage(), e);
        
        // 检查是否是SSE请求
        if (isSseRequest()) {
            SseEmitter emitter = new SseEmitter();
            try {
                // 发送错误信息并关闭连接
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data("Error: " + e.getMessage()));
                emitter.completeWithError(e);
            } catch (IOException ex) {
                logger.error("Error sending SSE error message: {}", ex.getMessage(), ex);
            }
            return emitter;
        }
        
        // 普通请求返回ApiResponse
        return ApiResponse.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public Object handleException(Exception e) {
        logger.error("System Error: {}", e.getMessage(), e);
        
        // 检查是否是SSE请求
        if (isSseRequest()) {
            // 对于SSE请求，直接返回null，错误已在服务层处理
            logger.warn("SSE request encountered error, returning null to avoid converter issues");
            return null;
        }
        
        // 普通请求返回ApiResponse
        return ApiResponse.fail(500, "服务器内部错误，请稍后重试");
    }
    
    /**
     * 检查当前请求是否是SSE请求
     */
    private boolean isSseRequest() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes) {
            HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
            // 检查Accept头或请求路径
            String accept = request.getHeader(HttpHeaders.ACCEPT);
            String path = request.getRequestURI();
            
            return (accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE)) ||
                   (path != null && path.contains("/chat/stream"));
        }
        return false;
    }
}