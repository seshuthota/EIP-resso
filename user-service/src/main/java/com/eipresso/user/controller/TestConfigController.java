package com.eipresso.user.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Test controller to demonstrate dynamic configuration refresh
 */
@RestController
@RequestMapping("/api/test")
@RefreshScope
public class TestConfigController {

    @Value("${user-service.test.message:Default message}")
    private String testMessage;

    @Value("${user-service.test.feature-enabled:false}")
    private boolean featureEnabled;

    @Value("${user-service.test.refresh-count:0}")
    private int refreshCount;

    @GetMapping("/config")
    public Map<String, Object> getTestConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("message", testMessage);
        config.put("featureEnabled", featureEnabled);
        config.put("refreshCount", refreshCount);
        config.put("timestamp", System.currentTimeMillis());
        return config;
    }
} 