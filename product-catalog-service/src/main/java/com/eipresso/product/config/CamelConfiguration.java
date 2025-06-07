package com.eipresso.product.config;

import com.hazelcast.config.Config;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.CamelContext;
import org.apache.camel.spring.boot.CamelContextConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * Apache Camel Configuration for Product Catalog Service
 * 
 * Configures:
 * - Hazelcast clustering for Active-Active deployment
 * - Redis cache integration
 * - Database connection for SQL operations
 * - Camel component configurations
 */
@Configuration
public class CamelConfiguration {
    
    @Value("${redis.host:localhost}")
    private String redisHost;
    
    @Value("${redis.port:6379}")
    private int redisPort;
    

    
    /**
     * Hazelcast Configuration for Clustering
     * Active-Active clustering for read-heavy workload
     */
    @Bean
    public HazelcastInstance hazelcastInstance() {
        Config config = new Config();
        config.setInstanceName("product-catalog-cluster");
        
        // Network configuration for clustering
        NetworkConfig networkConfig = config.getNetworkConfig();
        networkConfig.setPort(5701);
        networkConfig.setPortAutoIncrement(true);
        
        // Join configuration - multicast for local development
        JoinConfig joinConfig = networkConfig.getJoin();
        joinConfig.getMulticastConfig().setEnabled(true);
        joinConfig.getTcpIpConfig().setEnabled(false);
        
        return Hazelcast.newHazelcastInstance(config);
    }
    
    /**
     * Redis Configuration for Caching
     */
    @Bean
    public JedisPool jedisPool() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(20);
        poolConfig.setMaxIdle(10);
        poolConfig.setMinIdle(5);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        
        return new JedisPool(poolConfig, redisHost, redisPort);
    }
    

    
    /**
     * Camel Context Configuration
     */
    @Bean
    public CamelContextConfiguration camelContextConfiguration() {
        return new CamelContextConfiguration() {
            @Override
            public void beforeApplicationStart(CamelContext camelContext) {
                // Enable JMX for monitoring
                camelContext.setUseMDCLogging(true);
                
                // Set clustering-friendly configuration
                camelContext.setAllowUseOriginalMessage(false);
                camelContext.setUseBreadcrumb(false);
            }
            
            @Override
            public void afterApplicationStart(CamelContext camelContext) {
                // Log active routes for monitoring
                camelContext.getRoutes().forEach(route -> {
                    System.out.println("🚀 Active Camel Route: " + route.getId() + 
                                     " - " + route.getDescription());
                });
                
                System.out.println("✅ Product Catalog Service - Camel Context Started with " + 
                                 camelContext.getRoutes().size() + " routes");
            }
        };
    }
    
    /**
     * Cache Configuration Bean
     */
    @Bean
    public CacheConfig cacheConfig() {
        return new CacheConfig();
    }
    
    /**
     * Internal cache configuration class
     */
    public static class CacheConfig {
        private int defaultTtl = 3600; // 1 hour
        private int featuredProductTtl = 7200; // 2 hours
        private int popularProductTtl = 10800; // 3 hours
        
        public int getDefaultTtl() { return defaultTtl; }
        public int getFeaturedProductTtl() { return featuredProductTtl; }
        public int getPopularProductTtl() { return popularProductTtl; }
    }
} 