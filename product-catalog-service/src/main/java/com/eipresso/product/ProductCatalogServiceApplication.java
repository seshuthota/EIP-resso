package com.eipresso.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Product Catalog Service - Apache Camel EIP Patterns Implementation
 * 
 * Focus: Advanced Apache Camel patterns with clustering and message-driven architecture
 * 
 * EIP Patterns Implemented:
 * - Cache Pattern: Intelligent caching with TTL management
 * - Multicast: Broadcast price changes to multiple services
 * - Recipient List: Dynamic routing based on product categories/regions
 * - Polling Consumer: Regular supplier feed updates
 * - Content-Based Router: Route messages based on product characteristics
 * 
 * Clustering: Active-Active with Hazelcast for read-heavy workload
 */
@SpringBootApplication
@EnableDiscoveryClient
public class ProductCatalogServiceApplication {
    
    public static void main(String[] args) {
        // Enable Camel route management and monitoring
        System.setProperty("camel.springboot.jmx-enabled", "true");
        System.setProperty("camel.springboot.endpoint-runtime-statistics-enabled", "true");
        System.setProperty("camel.springboot.load-statistics-enabled", "true");
        
        SpringApplication.run(ProductCatalogServiceApplication.class, args);
    }
} 