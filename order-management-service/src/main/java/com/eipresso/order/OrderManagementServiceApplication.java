package com.eipresso.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.hazelcast.config.Config;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.TcpIpConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

/**
 * Order Management Service Application
 * Implements Event Sourcing, EIP patterns, and Active-Passive clustering
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableTransactionManagement
public class OrderManagementServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderManagementServiceApplication.class, args);
    }

    /**
     * Hazelcast Configuration for Active-Passive Clustering
     */
    @Configuration
    public static class HazelcastConfig {
        
        @Bean
        public HazelcastInstance hazelcastInstance() {
            Config config = new Config();
            config.setClusterName("eip-resso-order-cluster");
            
            // Network configuration for clustering
            NetworkConfig networkConfig = config.getNetworkConfig();
            networkConfig.setPort(5703);
            networkConfig.setPortAutoIncrement(true);
            
            // TCP/IP join configuration
            JoinConfig joinConfig = networkConfig.getJoin();
            joinConfig.getMulticastConfig().setEnabled(false);
            
            TcpIpConfig tcpIpConfig = joinConfig.getTcpIpConfig();
            tcpIpConfig.setEnabled(true);
            tcpIpConfig.addMember("127.0.0.1:5703");
            tcpIpConfig.addMember("127.0.0.1:5704");
            
            // Management Center configuration
            config.getManagementCenterConfig()
                  .setScriptingEnabled(true);
            
            // Map configurations for order data
            config.getMapConfig("orders")
                  .setBackupCount(1)
                  .setAsyncBackupCount(1);
            
            config.getMapConfig("order-events")
                  .setBackupCount(1)
                  .setAsyncBackupCount(1);
            
            // Idempotent repository configuration
            config.getMapConfig("order-idempotent")
                  .setTimeToLiveSeconds(3600) // 1 hour TTL
                  .setBackupCount(1);
            
            return Hazelcast.newHazelcastInstance(config);
        }
    }
} 