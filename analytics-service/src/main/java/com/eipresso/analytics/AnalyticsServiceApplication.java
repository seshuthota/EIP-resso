package com.eipresso.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.annotation.PostConstruct;
import java.util.TimeZone;

/**
 * EIP-resso Analytics Service Application
 * 
 * Advanced Camel EIP Patterns Implementation:
 * - Event Sourcing Pattern for business event capture
 * - CQRS Pattern for separate read/write models
 * - Streaming Pattern for real-time event processing
 * - Aggregator Pattern for time-window metrics
 * 
 * Clustering: Active-Active (read-only data aggregation)
 * Port: 8087
 */
@SpringBootApplication
@EnableDiscoveryClient
@RefreshScope
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
@EnableJpaRepositories
@EnableElasticsearchRepositories
@ComponentScan(basePackages = "com.eipresso.analytics")
public class AnalyticsServiceApplication {

    public static void main(String[] args) {
        // Set default timezone
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        
        System.out.println("🚀 Starting EIP-resso Analytics Service...");
        System.out.println("📊 Advanced EIP Patterns: Event Sourcing, CQRS, Streaming, Aggregator");
        System.out.println("🔧 Port: 8087 | Clustering: Active-Active");
        
        SpringApplication.run(AnalyticsServiceApplication.class, args);
    }

    @PostConstruct
    public void init() {
        System.out.println("🎯 Analytics Service initialized with CQRS and Event Sourcing");
        System.out.println("📊 Ready for real-time analytics processing");
    }
} 