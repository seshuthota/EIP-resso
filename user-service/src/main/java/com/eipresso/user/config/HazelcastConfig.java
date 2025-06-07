package com.eipresso.user.config;

import com.hazelcast.config.*;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "hazelcast.enabled", havingValue = "true", matchIfMissing = true)
public class HazelcastConfig {
    
    @Value("${hazelcast.cluster-name:eipresso-user-cluster}")
    private String clusterName;
    
    @Value("${hazelcast.network.port:5701}")
    private int port;
    
    @Bean("hazelcastConfiguration")
    public Config hazelcastConfig() {
        Config config = new Config();
        config.setClusterName(clusterName);
        
        // Network configuration for clustering
        NetworkConfig networkConfig = config.getNetworkConfig();
        networkConfig.setPort(port);
        networkConfig.setPortAutoIncrement(true);
        
        // Join configuration - TCP/IP for development, AWS/K8s discovery for production
        JoinConfig joinConfig = networkConfig.getJoin();
        joinConfig.getMulticastConfig().setEnabled(false);
        
        TcpIpConfig tcpIpConfig = joinConfig.getTcpIpConfig();
        tcpIpConfig.setEnabled(true);
        tcpIpConfig.addMember("127.0.0.1:5701");
        tcpIpConfig.addMember("127.0.0.1:5702");
        tcpIpConfig.addMember("127.0.0.1:5703");
        
        // Management Center configuration
        ManagementCenterConfig managementCenterConfig = config.getManagementCenterConfig();
        managementCenterConfig.setConsoleEnabled(true);
        
        // Map configurations for different use cases
        
        // Default map configuration
        MapConfig defaultMapConfig = new MapConfig("default");
        defaultMapConfig.setBackupCount(1);
        defaultMapConfig.setTimeToLiveSeconds(300);
        defaultMapConfig.setMaxIdleSeconds(600);
        config.addMapConfig(defaultMapConfig);
        
        // Idempotent Consumer repository map for Camel EIP pattern
        MapConfig idempotentMapConfig = new MapConfig("idempotent-repository");
        idempotentMapConfig.setBackupCount(1);
        idempotentMapConfig.setTimeToLiveSeconds(3600); // 1 hour for duplicate prevention
        idempotentMapConfig.setMaxIdleSeconds(1800);
        
        // Configure eviction policy
        EvictionConfig evictionConfig = new EvictionConfig();
        evictionConfig.setEvictionPolicy(EvictionPolicy.LRU);
        evictionConfig.setMaxSizePolicy(MaxSizePolicy.PER_NODE);
        evictionConfig.setSize(10000); // Max 10k entries per node
        idempotentMapConfig.setEvictionConfig(evictionConfig);
        
        config.addMapConfig(idempotentMapConfig);
        
        // JWT Token blacklist map (for token invalidation)
        MapConfig jwtBlacklistConfig = new MapConfig("jwt-blacklist");
        jwtBlacklistConfig.setBackupCount(1);
        jwtBlacklistConfig.setTimeToLiveSeconds(900); // 15 minutes (match token expiry)
        config.addMapConfig(jwtBlacklistConfig);
        
        // User session map for distributed session management
        MapConfig userSessionConfig = new MapConfig("user-sessions");
        userSessionConfig.setBackupCount(1);
        userSessionConfig.setTimeToLiveSeconds(1800); // 30 minutes
        config.addMapConfig(userSessionConfig);
        
        // Security audit cache for rate limiting
        MapConfig auditCacheConfig = new MapConfig("audit-cache");
        auditCacheConfig.setBackupCount(1);
        auditCacheConfig.setTimeToLiveSeconds(300); // 5 minutes
        config.addMapConfig(auditCacheConfig);
        
        return config;
    }
    
    @Bean
    public HazelcastInstance hazelcastInstance(Config config) {
        return Hazelcast.newHazelcastInstance(config);
    }
    
    /**
     * Idempotent Repository for Camel EIP Pattern
     * Prevents duplicate user registration processing
     */
    @Bean("hazelcastIdempotentRepository")
    public MemoryIdempotentRepository hazelcastIdempotentRepository() {
        return new MemoryIdempotentRepository();
    }
    
    /**
     * JWT Token Blacklist for token invalidation
     */
    @Bean
    public com.hazelcast.map.IMap<String, String> jwtBlacklistMap(HazelcastInstance hazelcastInstance) {
        return hazelcastInstance.getMap("jwt-blacklist");
    }
    
    /**
     * User Session Map for distributed session management
     */
    @Bean
    public com.hazelcast.map.IMap<String, Object> userSessionMap(HazelcastInstance hazelcastInstance) {
        return hazelcastInstance.getMap("user-sessions");
    }
    
    /**
     * Audit Cache for security monitoring and rate limiting
     */
    @Bean
    public com.hazelcast.map.IMap<String, Object> auditCacheMap(HazelcastInstance hazelcastInstance) {
        return hazelcastInstance.getMap("audit-cache");
    }
} 