package com.eipresso.clustering.config;

import com.hazelcast.config.*;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.spring.context.SpringManagedContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Centralized Hazelcast Clustering Configuration for EIP-resso Microservices
 * 
 * Supports both Active-Active and Active-Passive clustering strategies:
 * - Active-Active: For stateless services (User Management, Product Catalog, Notifications, Analytics)
 * - Active-Passive: For stateful services requiring consistency (Order Management, Payment, Orchestration)
 * 
 * Features:
 * - Service discovery integration with Consul
 * - Split-brain protection for critical services
 * - Network partition tolerance
 * - Kubernetes-ready configuration
 */
@Configuration
@EnableConfigurationProperties(EipressoClusteringProperties.class)
public class HazelcastClusteringConfiguration {
    
    private final EipressoClusteringProperties clusteringProperties;
    
    public HazelcastClusteringConfiguration(EipressoClusteringProperties clusteringProperties) {
        this.clusteringProperties = clusteringProperties;
    }
    
    /**
     * Active-Active Hazelcast Configuration
     * For stateless services that can handle concurrent operations
     */
    @Bean
    @ConditionalOnProperty(
        value = "eipresso.clustering.strategy", 
        havingValue = "active-active"
    )
    public HazelcastInstance activeActiveHazelcastInstance() {
        Config config = new Config();
        
        // Basic cluster configuration
        config.setClusterName(clusteringProperties.getClusterName());
        config.setInstanceName(clusteringProperties.getInstanceName());
        
        // Network configuration for Active-Active
        configureActiveActiveNetwork(config);
        
        // Map configurations for stateless services
        configureActiveActiveMaps(config);
        
        // Spring integration
        config.setManagedContext(new SpringManagedContext());
        
        return Hazelcast.newHazelcastInstance(config);
    }
    
    /**
     * Active-Passive Hazelcast Configuration
     * For stateful services requiring strong consistency
     */
    @Bean
    @ConditionalOnProperty(
        value = "eipresso.clustering.strategy", 
        havingValue = "active-passive"
    )
    public HazelcastInstance activePassiveHazelcastInstance() {
        Config config = new Config();
        
        // Basic cluster configuration
        config.setClusterName(clusteringProperties.getClusterName());
        config.setInstanceName(clusteringProperties.getInstanceName());
        
        // Network configuration for Active-Passive
        configureActivePassiveNetwork(config);
        
        // Map configurations for stateful services
        configureActivePassiveMaps(config);
        
        // Split-brain protection for critical data
        configureSplitBrainProtection(config);
        
        // Spring integration
        config.setManagedContext(new SpringManagedContext());
        
        return Hazelcast.newHazelcastInstance(config);
    }
    
    /**
     * Configure network settings for Active-Active clustering
     */
    private void configureActiveActiveNetwork(Config config) {
        NetworkConfig networkConfig = config.getNetworkConfig();
        
        // Port configuration
        networkConfig.setPort(clusteringProperties.getPort());
        networkConfig.setPortAutoIncrement(true);
        networkConfig.setPortCount(10);
        
        // Join configuration with multiple strategies
        JoinConfig joinConfig = networkConfig.getJoin();
        
        // Multicast for local development
        if (clusteringProperties.isMulticastEnabled()) {
            MulticastConfig multicastConfig = joinConfig.getMulticastConfig();
            multicastConfig.setEnabled(true);
            multicastConfig.setMulticastGroup("224.2.2.3");
            multicastConfig.setMulticastPort(54327);
        }
        
        // TCP/IP for production
        TcpIpConfig tcpIpConfig = joinConfig.getTcpIpConfig();
        tcpIpConfig.setEnabled(true);
        tcpIpConfig.setMembers(clusteringProperties.getMembers());
    }
    
    /**
     * Configure network settings for Active-Passive clustering
     */
    private void configureActivePassiveNetwork(Config config) {
        configureActiveActiveNetwork(config); // Base network config
    }
    
    /**
     * Configure maps for Active-Active services
     */
    private void configureActiveActiveMaps(Config config) {
        // User session cache (distributed)
        MapConfig userSessionConfig = new MapConfig("user-sessions");
        userSessionConfig.setTimeToLiveSeconds(3600); // 1 hour
        userSessionConfig.setMaxIdleSeconds(1800); // 30 minutes
        userSessionConfig.setBackupCount(1);
        userSessionConfig.setAsyncBackupCount(1);
        config.addMapConfig(userSessionConfig);
        
        // Product catalog cache
        MapConfig productCacheConfig = new MapConfig("product-cache");
        productCacheConfig.setTimeToLiveSeconds(7200); // 2 hours
        productCacheConfig.setMaxIdleSeconds(3600); // 1 hour
        productCacheConfig.setBackupCount(2);
        productCacheConfig.setEvictionConfig(new EvictionConfig()
            .setEvictionPolicy(EvictionPolicy.LRU)
            .setMaxSizePolicy(MaxSizePolicy.USED_HEAP_PERCENTAGE)
            .setSize(80));
        config.addMapConfig(productCacheConfig);
        
        // Notification preferences
        MapConfig notificationPrefsConfig = new MapConfig("notification-preferences");
        notificationPrefsConfig.setTimeToLiveSeconds(86400); // 24 hours
        notificationPrefsConfig.setBackupCount(1);
        config.addMapConfig(notificationPrefsConfig);
        
        // Analytics event buffer
        MapConfig analyticsBufferConfig = new MapConfig("analytics-events");
        analyticsBufferConfig.setTimeToLiveSeconds(600); // 10 minutes
        analyticsBufferConfig.setBackupCount(1);
        config.addMapConfig(analyticsBufferConfig);
    }
    
    /**
     * Configure maps for Active-Passive services
     */
    private void configureActivePassiveMaps(Config config) {
        // Order state management (requires strong consistency)
        MapConfig orderStateConfig = new MapConfig("order-states");
        orderStateConfig.setBackupCount(2);
        orderStateConfig.setAsyncBackupCount(0); // Synchronous backups for consistency
        orderStateConfig.setReadBackupData(false); // Always read from primary
        config.addMapConfig(orderStateConfig);
        
        // Payment transactions (critical data)
        MapConfig paymentTransactionConfig = new MapConfig("payment-transactions");
        paymentTransactionConfig.setBackupCount(3);
        paymentTransactionConfig.setAsyncBackupCount(0);
        paymentTransactionConfig.setReadBackupData(false);
        config.addMapConfig(paymentTransactionConfig);
        
        // Saga coordination state
        MapConfig sagaStateConfig = new MapConfig("saga-states");
        sagaStateConfig.setTimeToLiveSeconds(7200); // 2 hours
        sagaStateConfig.setBackupCount(2);
        sagaStateConfig.setAsyncBackupCount(0);
        config.addMapConfig(sagaStateConfig);
        
        // Workflow instances
        MapConfig workflowConfig = new MapConfig("workflow-instances");
        workflowConfig.setTimeToLiveSeconds(14400); // 4 hours
        workflowConfig.setBackupCount(2);
        config.addMapConfig(workflowConfig);
    }
    
    /**
     * Configure split-brain protection for critical services
     */
    private void configureSplitBrainProtection(Config config) {
        SplitBrainProtectionConfig splitBrainConfig = new SplitBrainProtectionConfig();
        splitBrainConfig.setName("critical-data-protection");
        splitBrainConfig.setEnabled(true);
        splitBrainConfig.setMinimumClusterSize(clusteringProperties.getMinClusterSize());
        config.addSplitBrainProtectionConfig(splitBrainConfig);
        
        // Apply to critical maps
        config.getMapConfig("order-states")
            .setSplitBrainProtectionName("critical-data-protection");
        config.getMapConfig("payment-transactions")
            .setSplitBrainProtectionName("critical-data-protection");
        config.getMapConfig("saga-states")
            .setSplitBrainProtectionName("critical-data-protection");
    }
} 