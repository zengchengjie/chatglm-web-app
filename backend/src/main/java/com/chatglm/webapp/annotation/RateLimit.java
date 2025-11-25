package com.chatglm.webapp.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {
    
    /**
     * 限流键类型
     */
    KeyType keyType() default KeyType.IP;
    
    /**
     * 最大请求数
     */
    int maxRequests() default 10;
    
    /**
     * 时间窗口（秒）
     */
    int timeWindow() default 60;
    
    /**
     * 限流提示信息
     */
    String message() default "请求过于频繁，请稍后再试";
    
    enum KeyType {
        IP,        // 基于IP限流
        USER,      // 基于用户限流
        GLOBAL     // 全局限流
    }
}