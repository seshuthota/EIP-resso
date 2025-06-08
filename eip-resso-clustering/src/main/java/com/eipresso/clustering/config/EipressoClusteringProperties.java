package com.eipresso.clustering.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.List;

/**
 * Configuration Properties for EIP-resso Hazelcast Clustering
 * 
 * Supports configuration for both Active-Active and Active-Passive clustering strategies
 * with comprehensive networking, security, and monitoring options.
 */
@ConfigurationProperties(prefix = "eipresso.clustering")
public class EipressoClusteringProperties {
    
    /**
     * Clustering strategy: "active-active" or "active-passive"
     */
    private String strategy = "active-active";
    
    /**
     * Hazelcast cluster name
     */
    private String clusterName = "eip-resso-cluster";
    
    /**
     * Instance name for this service
     */
    private String instanceName;
    
    /**
     * Service name for discovery
     */
    private String serviceName;
    
    /**
     * Hazelcast port
     */
    private int port = 5701;
    
    /**
     * Cluster member addresses
     */
    private List<String> members = List.of("127.0.0.1:5701", "127.0.0.1:5702", "127.0.0.1:5703");
    
    /**
     * Minimum cluster size for split-brain protection
     */
    private int minClusterSize = 2;
    
    /**
     * Enable multicast discovery
     */
    private boolean multicastEnabled = false;
    
    /**
     * Enable Consul service discovery
     */
    private boolean consulDiscoveryEnabled = true;
    
    /**
     * Consul host
     */
    private String consulHost = "localhost";
    
    /**
     * Consul port
     */
    private int consulPort = 8500;
    
    /**
     * Management Center configuration
     */
    @NestedConfigurationProperty
    private ManagementCenterConfig managementCenter = new ManagementCenterConfig();
    
    /**
     * Security configuration
     */
    @NestedConfigurationProperty
    private SecurityConfig security = new SecurityConfig();
    
    // Getters and Setters
    
    public String getStrategy() {
        return strategy;
    }
    
    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }
    
    public String getClusterName() {
        return clusterName;
    }
    
    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }
    
    public String getInstanceName() {
        return instanceName;
    }
    
    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
    }
    
    public String getServiceName() {
        return serviceName;
    }
    
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
    
    public int getPort() {
        return port;
    }
    
    public void setPort(int port) {
        this.port = port;
    }
    
    public List<String> getMembers() {
        return members;
    }
    
    public void setMembers(List<String> members) {
        this.members = members;
    }
    
    public int getMinClusterSize() {
        return minClusterSize;
    }
    
    public void setMinClusterSize(int minClusterSize) {
        this.minClusterSize = minClusterSize;
    }
    
    public boolean isMulticastEnabled() {
        return multicastEnabled;
    }
    
    public void setMulticastEnabled(boolean multicastEnabled) {
        this.multicastEnabled = multicastEnabled;
    }
    
    public boolean isConsulDiscoveryEnabled() {
        return consulDiscoveryEnabled;
    }
    
    public void setConsulDiscoveryEnabled(boolean consulDiscoveryEnabled) {
        this.consulDiscoveryEnabled = consulDiscoveryEnabled;
    }
    
    public String getConsulHost() {
        return consulHost;
    }
    
    public void setConsulHost(String consulHost) {
        this.consulHost = consulHost;
    }
    
    public int getConsulPort() {
        return consulPort;
    }
    
    public void setConsulPort(int consulPort) {
        this.consulPort = consulPort;
    }
    
    public ManagementCenterConfig getManagementCenter() {
        return managementCenter;
    }
    
    public void setManagementCenter(ManagementCenterConfig managementCenter) {
        this.managementCenter = managementCenter;
    }
    
    public SecurityConfig getSecurity() {
        return security;
    }
    
    public void setSecurity(SecurityConfig security) {
        this.security = security;
    }
    
    /**
     * Management Center Configuration
     */
    public static class ManagementCenterConfig {
        private boolean enabled = false;
        private String url = "http://localhost:8080/hazelcast-mancenter";
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
        
        public String getUrl() {
            return url;
        }
        
        public void setUrl(String url) {
            this.url = url;
        }
    }
    
    /**
     * Security Configuration
     */
    public static class SecurityConfig {
        private boolean enabled = false;
        private String username = "eip-resso-cluster";
        private String password = "cluster-secret";
        private String clientUsername = "eip-resso-client";
        private String clientPassword = "client-secret";
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
        
        public String getUsername() {
            return username;
        }
        
        public void setUsername(String username) {
            this.username = username;
        }
        
        public String getPassword() {
            return password;
        }
        
        public void setPassword(String password) {
            this.password = password;
        }
        
        public String getClientUsername() {
            return clientUsername;
        }
        
        public void setClientUsername(String clientUsername) {
            this.clientUsername = clientUsername;
        }
        
        public String getClientPassword() {
            return clientPassword;
        }
        
        public void setClientPassword(String clientPassword) {
            this.clientPassword = clientPassword;
        }
    }
} 