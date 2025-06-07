package com.eipresso.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * EIP-resso Configuration Server
 * 
 * Centralized configuration management service providing:
 * - Git-backed configuration repository
 * - Environment-specific property management
 * - Configuration encryption support
 * - Apache Camel route monitoring for configuration changes
 * - Real-time configuration refresh capabilities
 * 
 * Learning Focus:
 * - Spring Cloud Config Server patterns
 * - Configuration as Code practices
 * - Environment promotion strategies
 * - Configuration change monitoring with Camel
 */
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
} 