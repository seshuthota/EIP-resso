package com.eipresso.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import jakarta.annotation.PostConstruct;
import java.util.TimeZone;

/**
 * EIP-resso Payment Service Application
 * 
 * Advanced Camel EIP Patterns Implementation:
 * - Wire Tap Pattern for comprehensive audit trail
 * - Retry Pattern for resilient payment gateway integration
 * - Split Pattern for batch payments and bulk refunds
 * - Filter Pattern for fraud detection and risk assessment
 * - Request-Reply Pattern for synchronous payment confirmation
 * - Circuit Breaker Pattern for fault tolerance
 * 
 * Clustering: Active-Passive (financial transaction integrity)
 * Port: 8084
 */
@SpringBootApplication
@EnableDiscoveryClient
@RefreshScope
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
@EnableJpaRepositories
@ComponentScan(basePackages = "com.eipresso.payment")
public class PaymentServiceApplication {

    public static void main(String[] args) {
        // Set default timezone
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        
        System.out.println("💳 Starting EIP-resso Payment Service...");
        System.out.println("🔐 Advanced EIP Patterns: Wire Tap, Retry, Split, Filter, Request-Reply, Circuit Breaker");
        System.out.println("🏦 Port: 8084 | Clustering: Active-Passive (Financial Integrity)");
        
        SpringApplication.run(PaymentServiceApplication.class, args);
    }

    @PostConstruct
    public void init() {
        System.out.println("💰 Payment Service initialized with advanced security and EIP patterns");
        System.out.println("🔒 Ready for secure payment processing with fraud detection");
    }
} 