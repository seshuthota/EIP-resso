package com.eipresso.clustering.monitor;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.Member;
import com.hazelcast.partition.PartitionService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuator.health.Health;
import org.springframework.boot.actuator.health.HealthIndicator;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Cluster Monitoring Service for EIP-resso Hazelcast Clustering
 * 
 * Provides comprehensive monitoring, health checks, and metrics for the cluster:
 * - Cluster health status
 * - Member count and status
 * - Partition distribution
 * - Split-brain detection
 * - Performance metrics
 */
@Service
public class ClusterMonitoringService implements HealthIndicator {
    
    private final HazelcastInstance hazelcastInstance;
    private final MeterRegistry meterRegistry;
    
    public ClusterMonitoringService(HazelcastInstance hazelcastInstance, MeterRegistry meterRegistry) {
        this.hazelcastInstance = hazelcastInstance;
        this.meterRegistry = meterRegistry;
        
        // Register cluster metrics
        registerClusterMetrics();
    }
    
    /**
     * Health check for the Hazelcast cluster
     */
    @Override
    public Health health() {
        try {
            if (hazelcastInstance == null || !hazelcastInstance.getLifecycleService().isRunning()) {
                return Health.down()
                    .withDetail("status", "Hazelcast instance not running")
                    .build();
            }
            
            ClusterStatus clusterStatus = getClusterStatus();
            
            // Determine health based on cluster status
            if (clusterStatus.isSplitBrainDetected()) {
                return Health.down()
                    .withDetail("status", "Split-brain detected")
                    .withDetails(clusterStatus.toMap())
                    .build();
            }
            
            if (clusterStatus.getMemberCount() < getMinimumRequiredMembers()) {
                return Health.degraded()
                    .withDetail("status", "Insufficient cluster members")
                    .withDetails(clusterStatus.toMap())
                    .build();
            }
            
            return Health.up()
                .withDetail("status", "Cluster healthy")
                .withDetails(clusterStatus.toMap())
                .build();
                
        } catch (Exception e) {
            return Health.down()
                .withDetail("status", "Error checking cluster health")
                .withDetail("error", e.getMessage())
                .build();
        }
    }
    
    /**
     * Get comprehensive cluster status
     */
    public ClusterStatus getClusterStatus() {
        ClusterStatus status = new ClusterStatus();
        
        // Basic cluster info
        status.setClusterName(hazelcastInstance.getConfig().getClusterName());
        status.setInstanceName(hazelcastInstance.getName());
        status.setRunning(hazelcastInstance.getLifecycleService().isRunning());
        
        // Member information
        Set<Member> members = hazelcastInstance.getCluster().getMembers();
        status.setMemberCount(members.size());
        status.setLocalMember(hazelcastInstance.getCluster().getLocalMember());
        status.setMembers(members);
        
        // Partition information
        PartitionService partitionService = hazelcastInstance.getPartitionService();
        status.setPartitionCount(partitionService.getPartitions().size());
        status.setSafeState(partitionService.isClusterSafe());
        status.setMigrationQueueSize(partitionService.getMigrationQueueSize());
        
        // Split-brain detection
        status.setSplitBrainDetected(detectSplitBrain());
        
        // Performance metrics
        status.setMemoryUsed(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
        status.setMemoryTotal(Runtime.getRuntime().totalMemory());
        
        return status;
    }
    
    /**
     * Register Prometheus metrics for cluster monitoring
     */
    private void registerClusterMetrics() {
        // Cluster member count
        Gauge.builder("hazelcast.cluster.members")
            .description("Number of cluster members")
            .register(meterRegistry, this, service -> 
                service.hazelcastInstance.getCluster().getMembers().size());
        
        // Partition safety
        Gauge.builder("hazelcast.cluster.safe")
            .description("Cluster safe state (1 = safe, 0 = unsafe)")
            .register(meterRegistry, this, service -> 
                service.hazelcastInstance.getPartitionService().isClusterSafe() ? 1 : 0);
        
        // Migration queue size
        Gauge.builder("hazelcast.cluster.migration.queue.size")
            .description("Size of partition migration queue")
            .register(meterRegistry, this, service -> 
                service.hazelcastInstance.getPartitionService().getMigrationQueueSize());
        
        // Instance running state
        Gauge.builder("hazelcast.instance.running")
            .description("Instance running state (1 = running, 0 = stopped)")
            .register(meterRegistry, this, service -> 
                service.hazelcastInstance.getLifecycleService().isRunning() ? 1 : 0);
    }
    
    /**
     * Detect potential split-brain scenarios
     */
    private boolean detectSplitBrain() {
        try {
            // Check if cluster is in safe state
            if (!hazelcastInstance.getPartitionService().isClusterSafe()) {
                return true;
            }
            
            // Check for migration activity (could indicate partition healing)
            if (hazelcastInstance.getPartitionService().getMigrationQueueSize() > 0) {
                return true;
            }
            
            // Additional checks can be added here based on specific requirements
            return false;
            
        } catch (Exception e) {
            // If we can't determine safely, assume split-brain
            return true;
        }
    }
    
    /**
     * Get minimum required members based on service type
     */
    private int getMinimumRequiredMembers() {
        // This could be made configurable per service
        return 2;
    }
    
    /**
     * Cluster Status Data Transfer Object
     */
    public static class ClusterStatus {
        private String clusterName;
        private String instanceName;
        private boolean running;
        private int memberCount;
        private Member localMember;
        private Set<Member> members;
        private int partitionCount;
        private boolean safeState;
        private int migrationQueueSize;
        private boolean splitBrainDetected;
        private long memoryUsed;
        private long memoryTotal;
        
        // Convert to map for health endpoint
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("clusterName", clusterName);
            map.put("instanceName", instanceName);
            map.put("running", running);
            map.put("memberCount", memberCount);
            map.put("localMember", localMember != null ? localMember.getAddress().toString() : "unknown");
            map.put("partitionCount", partitionCount);
            map.put("safeState", safeState);
            map.put("migrationQueueSize", migrationQueueSize);
            map.put("splitBrainDetected", splitBrainDetected);
            map.put("memoryUsedMB", memoryUsed / 1024 / 1024);
            map.put("memoryTotalMB", memoryTotal / 1024 / 1024);
            map.put("memoryUsagePercent", Math.round((double) memoryUsed / memoryTotal * 100));
            return map;
        }
        
        // Getters and Setters
        public String getClusterName() { return clusterName; }
        public void setClusterName(String clusterName) { this.clusterName = clusterName; }
        
        public String getInstanceName() { return instanceName; }
        public void setInstanceName(String instanceName) { this.instanceName = instanceName; }
        
        public boolean isRunning() { return running; }
        public void setRunning(boolean running) { this.running = running; }
        
        public int getMemberCount() { return memberCount; }
        public void setMemberCount(int memberCount) { this.memberCount = memberCount; }
        
        public Member getLocalMember() { return localMember; }
        public void setLocalMember(Member localMember) { this.localMember = localMember; }
        
        public Set<Member> getMembers() { return members; }
        public void setMembers(Set<Member> members) { this.members = members; }
        
        public int getPartitionCount() { return partitionCount; }
        public void setPartitionCount(int partitionCount) { this.partitionCount = partitionCount; }
        
        public boolean isSafeState() { return safeState; }
        public void setSafeState(boolean safeState) { this.safeState = safeState; }
        
        public int getMigrationQueueSize() { return migrationQueueSize; }
        public void setMigrationQueueSize(int migrationQueueSize) { this.migrationQueueSize = migrationQueueSize; }
        
        public boolean isSplitBrainDetected() { return splitBrainDetected; }
        public void setSplitBrainDetected(boolean splitBrainDetected) { this.splitBrainDetected = splitBrainDetected; }
        
        public long getMemoryUsed() { return memoryUsed; }
        public void setMemoryUsed(long memoryUsed) { this.memoryUsed = memoryUsed; }
        
        public long getMemoryTotal() { return memoryTotal; }
        public void setMemoryTotal(long memoryTotal) { this.memoryTotal = memoryTotal; }
    }
} 