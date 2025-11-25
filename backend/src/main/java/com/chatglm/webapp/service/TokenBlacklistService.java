package com.chatglm.webapp.service;

import com.chatglm.webapp.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenBlacklistService {

    @Autowired
    private JwtUtil jwtUtil;

    // 存储已失效的token及其过期时间
    private final ConcurrentHashMap<String, Long> blacklistedTokens = new ConcurrentHashMap<>();

    /**
     * 将token添加到黑名单
     * @param token 要失效的token
     */
    public void blacklistToken(String token) {
        try {
            // 获取token的过期时间
            Long expiration = jwtUtil.getExpirationFromToken(token);
            if (expiration != null && expiration > System.currentTimeMillis()) {
                blacklistedTokens.put(token, expiration);
            }
        } catch (Exception e) {
            // 如果token解析失败，也加入黑名单
            blacklistedTokens.put(token, System.currentTimeMillis() + 86400000L); // 24小时后过期
        }
    }

    /**
     * 检查token是否在黑名单中
     * @param token 要检查的token
     * @return 如果token在黑名单中返回true，否则返回false
     */
    public boolean isTokenBlacklisted(String token) {
        Long expiration = blacklistedTokens.get(token);
        if (expiration == null) {
            return false;
        }
        
        // 如果token已过期，从黑名单中移除
        if (expiration <= System.currentTimeMillis()) {
            blacklistedTokens.remove(token);
            return false;
        }
        
        return true;
    }

    /**
     * 清理过期的token
     */
    public void cleanupExpiredTokens() {
        long currentTime = System.currentTimeMillis();
        blacklistedTokens.entrySet().removeIf(entry -> entry.getValue() <= currentTime);
    }
}
