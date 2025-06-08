package com.eipresso.orchestration.config;

import org.apache.camel.saga.CamelSagaService;
import org.apache.camel.saga.InMemorySagaService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Saga Configuration for Order Orchestration Service
 * 
 * Configures the CamelSagaService required for Saga pattern implementation.
 * Uses InMemorySagaService for development - in production, this should be
 * replaced with a persistent saga service (e.g., database-backed).
 */
@Configuration
public class SagaConfiguration {
    
    /**
     * Configure the Camel Saga Service
     * 
     * @return CamelSagaService instance for managing saga transactions
     */
    @Bean
    public CamelSagaService camelSagaService() {
        // For development, use in-memory saga service
        // In production, consider using a persistent implementation
        InMemorySagaService sagaService = new InMemorySagaService();
        
        // Configure saga service properties
        sagaService.setMaxRetryAttempts(3);
        sagaService.setRetryDelayInMilliseconds(1000);
        
        return sagaService;
    }
} 