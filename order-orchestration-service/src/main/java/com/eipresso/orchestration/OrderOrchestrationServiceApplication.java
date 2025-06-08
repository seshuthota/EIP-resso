package com.eipresso.orchestration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Order Orchestration Service - Business Process Conductor
 * 
 * Implements advanced EIP patterns:
 * - Saga Pattern for distributed transaction management
 * - Process Manager Pattern for complex workflow coordination
 * - Scatter-Gather Pattern for parallel service calls
 * - Compensating Actions Pattern for rollback mechanisms
 * 
 * Port: 8089
 * Clustering: Active-Passive (workflow state consistency)
 */
@SpringBootApplication
@EnableFeignClients
public class OrderOrchestrationServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(OrderOrchestrationServiceApplication.class, args);
    }
} 