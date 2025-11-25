package com.chatglm.webapp.controller;

import com.chatglm.webapp.model.ApiResponse;
import com.chatglm.webapp.service.ChatHistoryService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/monitoring")
public class MonitoringController {

    @Autowired
    private MeterRegistry meterRegistry;
    
    @Autowired
    private ChatHistoryService chatHistoryService;

    @GetMapping("/metrics")
    public ApiResponse<Map<String, Object>> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        // JVM 指标
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        
        metrics.put("jvm.heap.used", memoryBean.getHeapMemoryUsage().getUsed());
        metrics.put("jvm.heap.max", memoryBean.getHeapMemoryUsage().getMax());
        metrics.put("system.cpu.load", osBean.getSystemLoadAverage());
        metrics.put("jvm.threads.live", threadBean.getThreadCount());
        
        // 业务指标
        Counter chatCounter = meterRegistry.find("chatglm.chat.requests").counter();
        Counter streamCounter = meterRegistry.find("chatglm.stream.chat.requests").counter();
        
        if (chatCounter != null) {
            metrics.put("chat.requests.total", chatCounter.count());
        }
        if (streamCounter != null) {
            metrics.put("stream.chat.requests.total", streamCounter.count());
        }
        
        // 用户使用量统计
        metrics.put("user.usage.total", chatHistoryService.countUserUsage(1L));
        
        return ApiResponse.success(metrics);
    }
    
    @GetMapping("/health/detailed")
    public ApiResponse<Map<String, Object>> getDetailedHealth() {
        Map<String, Object> healthInfo = new HashMap<>();
        
        healthInfo.put("status", "UP");
        healthInfo.put("timestamp", System.currentTimeMillis());
        healthInfo.put("uptime", ManagementFactory.getRuntimeMXBean().getUptime());
        
        // 内存使用情况
        Runtime runtime = Runtime.getRuntime();
        healthInfo.put("memory.free", runtime.freeMemory());
        healthInfo.put("memory.total", runtime.totalMemory());
        healthInfo.put("memory.max", runtime.maxMemory());
        healthInfo.put("memory.used", runtime.totalMemory() - runtime.freeMemory());
        
        // 系统信息
        healthInfo.put("available.processors", runtime.availableProcessors());
        
        return ApiResponse.success(healthInfo);
    }
}