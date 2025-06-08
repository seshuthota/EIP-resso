package com.eipresso.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import jakarta.annotation.PostConstruct;
import java.util.TimeZone;

/**
 * EIP-resso Notification Service Application
 * 
 * Advanced Camel EIP Patterns Implementation:
 * - Publish-Subscribe Pattern for multi-channel notifications
 * - Message Filter Pattern for user preference filtering
 * - Throttling Pattern for rate limiting
 * - Dead Letter Channel for failed notification handling
 * - Template Method Pattern for dynamic message templates
 * 
 * Clustering: Active-Active (stateless message processing)
 * Port: 8086
 */
@SpringBootApplication
@EnableDiscoveryClient
@RefreshScope
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
@EnableJpaRepositories
@ComponentScan(basePackages = "com.eipresso.notification")
public class NotificationServiceApplication {

    public static void main(String[] args) {
        // Set default timezone
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        
        System.out.println("🚀 Starting EIP-resso Notification Service...");
        System.out.println("📡 Advanced EIP Patterns: Publish-Subscribe, Message Filter, Throttling");
        System.out.println("🔧 Port: 8086 | Clustering: Active-Active");
        
        SpringApplication.run(NotificationServiceApplication.class, args);
    }

    @PostConstruct
    public void init() {
        System.out.println("🎯 Notification Service initialized with advanced EIP patterns");
        System.out.println("📊 Ready for multi-channel notification processing");
    }
} 