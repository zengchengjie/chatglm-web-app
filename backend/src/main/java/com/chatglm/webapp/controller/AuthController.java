package com.chatglm.webapp.controller;

import com.chatglm.webapp.model.ApiResponse;
import com.chatglm.webapp.model.LoginRequest;
import com.chatglm.webapp.model.LoginResponse;
import com.chatglm.webapp.model.User;
import com.chatglm.webapp.service.TokenBlacklistService;
import com.chatglm.webapp.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/auth")
public class AuthController {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private JwtUtil jwtUtil;
    
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;
    
    // 模拟用户数据库
    private final Map<String, User> userDatabase = new HashMap<>();
    
    @PostConstruct
    public void init() {
        // 初始化测试用户
        String rawPassword = "admin123";
        String encodedPassword = passwordEncoder.encode(rawPassword);
        logger.info("Raw password: {}", rawPassword);
        logger.info("Encoded password: {}", encodedPassword);
        
        User admin = new User("admin", "admin@chatglm.com", 
                             encodedPassword, "ADMIN");
        admin.setId(1L);
        userDatabase.put("admin", admin);
        
        User user = new User("user", "user@chatglm.com", 
                           passwordEncoder.encode("user123"), "USER");
        user.setId(2L);
        userDatabase.put("user", user);
    }
    
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest loginRequest) {
        logger.info("Login attempt for username: {}", loginRequest.getUsername());
        User user = userDatabase.get(loginRequest.getUsername());
        
        if (user == null) {
            logger.warn("User not found: {}", loginRequest.getUsername());
            return ApiResponse.error(401, "用户名或密码错误");
        }
        
        logger.info("User found: {}", user.getUsername());
        logger.info("Raw password from request: {}", loginRequest.getPassword());
        logger.info("Stored encoded password: {}", user.getPassword());
        boolean passwordMatch = passwordEncoder.matches(loginRequest.getPassword(), user.getPassword());
        logger.info("Password match result: {}", passwordMatch);
        
        if (!passwordMatch) {
            return ApiResponse.error(401, "用户名或密码错误");
        }
        
        if (!user.getEnabled()) {
            return ApiResponse.error(401, "账户已被禁用");
        }
        
        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
        LoginResponse loginResponse = new LoginResponse(token, user.getId(), 
                                                        user.getUsername(), user.getEmail(), 
                                                        user.getRole(), 86400L);
        
        return ApiResponse.success(loginResponse);
    }
    
    @PostMapping("/register")
    public ApiResponse<String> register(@RequestBody User user) {
        if (userDatabase.containsKey(user.getUsername())) {
            return ApiResponse.error(400, "用户名已存在");
        }
        
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setId((long) (userDatabase.size() + 1));
        userDatabase.put(user.getUsername(), user);
        
        return ApiResponse.success("注册成功");
    }
    
    @GetMapping("/me")
    public ApiResponse<User> getCurrentUser(jakarta.servlet.http.HttpServletRequest request) {
        // 从SecurityContext中获取当前用户信息
        org.springframework.security.core.Authentication authentication = 
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null) {
            return ApiResponse.error(401, "未登录");
        }
        
        String username = authentication.getName();
        User user = userDatabase.get(username);
        
        if (user == null) {
            return ApiResponse.error(404, "用户不存在");
        }
        
        // 创建新的User对象来返回，避免修改原始数据
        User userResponse = new User(user.getUsername(), user.getEmail(), null, user.getRole());
        userResponse.setId(user.getId());
        userResponse.setEnabled(user.getEnabled());
        userResponse.setCreatedAt(user.getCreatedAt());
        userResponse.setUpdatedAt(user.getUpdatedAt());
        
        return ApiResponse.success(userResponse);
    }

    @PostMapping("/logout")
    public ApiResponse<String> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            // 将token添加到黑名单
            tokenBlacklistService.blacklistToken(token);
        }
        return ApiResponse.success("退出登录成功");
    }
}