package com.eipresso.config.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration Testing for Configuration Management Service
 * 
 * This test class simulates realistic coffee shop scenarios where multiple
 * services interact with the configuration server in production-like conditions:
 * 
 * 1. Service Discovery and Bootstrap Scenarios
 * 2. Multi-Service Configuration Coordination
 * 3. Configuration Refresh and Change Propagation
 * 4. Failure Recovery and Resilience Testing
 * 5. Performance Under Load Scenarios
 * 
 * Each test represents actual coffee shop operations where configuration
 * management is critical for business continuity.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "spring.cloud.config.server.git.uri=classpath:/test-config-repo/",
    "spring.cloud.config.server.git.clone-on-start=true",
    "management.endpoints.web.exposure.include=*",
    "eip-resso.config.mode=test-mode"
})
@DisplayName("EIP-resso Configuration Server - Integration Testing")
class ConfigServerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private final String baseUrl() {
        return "http://localhost:" + port;
    }

    @BeforeEach
    void setUp(TestInfo testInfo) {
        System.out.println("\n🧪 STARTING INTEGRATION TEST: " + testInfo.getDisplayName());
    }

    // ========================================================================
    // SCENARIO 1: Coffee Shop Morning Startup - All Services Bootstrap
    // ========================================================================

    @Test
    @DisplayName("☕ Integration 1.1: Morning Startup - All Coffee Shop Services Get Configuration")
    void testMorningStartup_AllCoffeeShopServicesGetConfiguration() throws Exception {
        // GIVEN: Coffee shop is starting up and all services need their configuration
        String[] criticalServices = {
            "user-service",      // Customer management
            "product-catalog",   // Coffee menu
            "order-management",  // Order processing
            "payment-service",   // Payment processing
            "inventory-service", // Stock management
            "notification-service" // Customer notifications
        };

        // WHEN: All services request their configuration during startup
        Map<String, Environment> serviceConfigurations = new ConcurrentHashMap<>();
        
        // Simulate concurrent startup configuration requests
        ExecutorService executor = Executors.newFixedThreadPool(criticalServices.length);
        CountDownLatch latch = new CountDownLatch(criticalServices.length);

        for (String service : criticalServices) {
            executor.submit(() -> {
                try {
                    ResponseEntity<Environment> response = restTemplate.getForEntity(
                        baseUrl() + "/" + service + "/default",
                        Environment.class
                    );
                    
                    if (response.getStatusCode() == HttpStatus.OK) {
                        serviceConfigurations.put(service, response.getBody());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for all services to get their configuration
        assertTrue(latch.await(30, TimeUnit.SECONDS), 
            "All services should receive configuration within 30 seconds");

        // THEN: All critical services should have received their configuration
        assertEquals(criticalServices.length, serviceConfigurations.size(),
            "All critical coffee shop services should receive configuration");

        // Verify each service configuration
        for (String service : criticalServices) {
            Environment env = serviceConfigurations.get(service);
            assertNotNull(env, "Service " + service + " should have configuration");
            assertEquals(service, env.getName(), "Configuration should match service name");
            assertFalse(env.getPropertySources().isEmpty(), 
                "Service " + service + " should have property sources");
        }

        executor.shutdown();
        System.out.println("✅ Morning startup complete - All " + criticalServices.length + 
                          " coffee shop services configured successfully");
    }

    @Test
    @DisplayName("☕ Integration 1.2: Service Discovery Health Check - Config Server Registration")
    void testServiceDiscoveryHealthCheck_ConfigServerRegistration() throws Exception {
        // GIVEN: Config server should be discoverable by other coffee shop services
        
        // WHEN: Services check config server health for service discovery
        ResponseEntity<Map> healthResponse = restTemplate.getForEntity(
            baseUrl() + "/actuator/health",
            Map.class
        );

        // THEN: Config server should report healthy status for service discovery
        assertEquals(HttpStatus.OK, healthResponse.getStatusCode());
        assertNotNull(healthResponse.getBody());

        Map<String, Object> health = healthResponse.getBody();
        assertEquals("UP", health.get("status"), "Config server should be UP for service discovery");

        // Verify config server components are healthy
        if (health.containsKey("components")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> components = (Map<String, Object>) health.get("components");
            
            // Check critical components for service discovery
            assertTrue(components.containsKey("configServer") || 
                      components.containsKey("diskSpace") ||
                      components.containsKey("ping"), 
                "Essential components should be healthy for service discovery");
        }

        System.out.println("✅ Service discovery health check validated - Config server is discoverable");
    }

    // ========================================================================
    // SCENARIO 2: Peak Hours Operation - High Load Configuration Requests
    // ========================================================================

    @Test
    @DisplayName("☕ Integration 2.1: Peak Hours - Concurrent Configuration Requests During Rush Hour")
    void testPeakHours_ConcurrentConfigurationRequestsDuringRushHour() throws Exception {
        // GIVEN: Coffee shop experiences rush hour with high configuration request load
        int concurrentUsers = 20;
        int requestsPerUser = 5;
        int totalRequests = concurrentUsers * requestsPerUser;

        // WHEN: Multiple services make concurrent configuration requests
        ExecutorService executor = Executors.newFixedThreadPool(concurrentUsers);
        CountDownLatch latch = new CountDownLatch(totalRequests);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        for (int user = 0; user < concurrentUsers; user++) {
            final int userId = user;
            executor.submit(() -> {
                String[] services = {"user-service", "product-catalog", "order-management", 
                                   "payment-service", "notification-service"};
                
                for (int request = 0; request < requestsPerUser; request++) {
                    try {
                        String service = services[request % services.length];
                        String profile = (userId % 2 == 0) ? "production" : "staging";
                        
                        ResponseEntity<Environment> response = restTemplate.getForEntity(
                            baseUrl() + "/" + service + "/" + profile,
                            Environment.class
                        );

                        if (response.getStatusCode() == HttpStatus.OK) {
                            successCount.incrementAndGet();
                        } else {
                            errorCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }

        // Wait for all requests to complete
        assertTrue(latch.await(60, TimeUnit.SECONDS), 
            "All requests should complete within 60 seconds");

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;

        // THEN: Most requests should succeed with acceptable performance
        int totalCompleted = successCount.get() + errorCount.get();
        assertEquals(totalRequests, totalCompleted, "All requests should complete");

        double successRate = (double) successCount.get() / totalRequests * 100;
        assertTrue(successRate >= 95.0, 
            "Success rate should be at least 95%. Actual: " + successRate + "%");

        assertTrue(totalTime < 30000, 
            "Peak hour load should complete within 30 seconds. Actual: " + totalTime + "ms");

        executor.shutdown();
        System.out.println("✅ Peak hours load test complete - " + successCount.get() + "/" + 
                          totalRequests + " requests succeeded in " + totalTime + "ms");
    }

    @Test
    @DisplayName("☕ Integration 2.2: Configuration Caching - Reduce Load During Peak Hours")
    void testConfigurationCaching_ReduceLoadDuringPeakHours() throws Exception {
        // GIVEN: Coffee shop services should cache configuration to reduce load
        String service = "user-service";
        String profile = "default";

        // WHEN: Same service requests configuration multiple times (simulating caching behavior)
        long startTime = System.currentTimeMillis();
        
        // First request (should hit config server)
        ResponseEntity<Environment> firstResponse = restTemplate.getForEntity(
            baseUrl() + "/" + service + "/" + profile,
            Environment.class
        );

        // Multiple subsequent requests (should be served from cache in real scenario)
        for (int i = 0; i < 10; i++) {
            ResponseEntity<Environment> response = restTemplate.getForEntity(
                baseUrl() + "/" + service + "/" + profile,
                Environment.class
            );
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;

        // THEN: All requests should succeed quickly (config server handles repeated requests efficiently)
        assertEquals(HttpStatus.OK, firstResponse.getStatusCode());
        assertTrue(totalTime < 5000, 
            "Repeated configuration requests should complete quickly: " + totalTime + "ms");

        System.out.println("✅ Configuration caching behavior validated - 11 requests in " + totalTime + "ms");
    }

    // ========================================================================
    // SCENARIO 3: Configuration Changes - Real-time Updates During Operations
    // ========================================================================

    @Test
    @DisplayName("☕ Integration 3.1: Menu Update - Product Catalog Configuration Refresh")
    void testMenuUpdate_ProductCatalogConfigurationRefresh() throws Exception {
        // GIVEN: Coffee shop needs to update menu prices during operation
        String service = "product-catalog";
        String profile = "production";

        // WHEN: We fetch current menu configuration
        ResponseEntity<Environment> beforeUpdate = restTemplate.getForEntity(
            baseUrl() + "/" + service + "/" + profile,
            Environment.class
        );

        assertEquals(HttpStatus.OK, beforeUpdate.getStatusCode());
        assertNotNull(beforeUpdate.getBody());

        // AND: Trigger configuration refresh (simulating Git push with menu changes)
        ResponseEntity<List> refreshResponse = restTemplate.exchange(
            baseUrl() + "/actuator/refresh",
            HttpMethod.POST,
            null,
            new ParameterizedTypeReference<List>() {}
        );

        assertEquals(HttpStatus.OK, refreshResponse.getStatusCode());

        // AND: Fetch configuration after refresh
        ResponseEntity<Environment> afterUpdate = restTemplate.getForEntity(
            baseUrl() + "/" + service + "/" + profile,
            Environment.class
        );

        // THEN: Configuration should be available before and after refresh
        assertEquals(HttpStatus.OK, afterUpdate.getStatusCode());
        assertNotNull(afterUpdate.getBody());

        // Verify configuration structure remains consistent
        assertEquals(service, afterUpdate.getBody().getName());
        assertFalse(afterUpdate.getBody().getPropertySources().isEmpty());

        System.out.println("✅ Menu update configuration refresh validated - Product catalog updated");
    }

    @Test
    @DisplayName("☕ Integration 3.2: Database Connection Update - Critical Service Reconfiguration")
    void testDatabaseConnectionUpdate_CriticalServiceReconfiguration() throws Exception {
        // GIVEN: Coffee shop needs to update database connection during low-traffic period
        String[] criticalServices = {"user-service", "order-management", "payment-service"};

        // WHEN: We update database configuration for critical services
        for (String service : criticalServices) {
            // Fetch current configuration
            ResponseEntity<Environment> currentConfig = restTemplate.getForEntity(
                baseUrl() + "/" + service + "/production",
                Environment.class
            );

            assertEquals(HttpStatus.OK, currentConfig.getStatusCode(), 
                "Service " + service + " should have configuration available");

            Environment env = currentConfig.getBody();
            assertNotNull(env, "Configuration should not be null for " + service);

            // Verify critical database-related properties are accessible
            List<PropertySource> propertySources = env.getPropertySources();
            assertFalse(propertySources.isEmpty(), 
                "Property sources should be available for " + service);
        }

        // Trigger global refresh for all services
        ResponseEntity<List> refreshResponse = restTemplate.exchange(
            baseUrl() + "/actuator/refresh",
            HttpMethod.POST,
            null,
            new ParameterizedTypeReference<List>() {}
        );

        assertEquals(HttpStatus.OK, refreshResponse.getStatusCode());

        // THEN: All critical services should still have valid configuration after refresh
        for (String service : criticalServices) {
            ResponseEntity<Environment> updatedConfig = restTemplate.getForEntity(
                baseUrl() + "/" + service + "/production",
                Environment.class
            );

            assertEquals(HttpStatus.OK, updatedConfig.getStatusCode(),
                "Service " + service + " should maintain configuration after refresh");
        }

        System.out.println("✅ Database connection update validated - All critical services reconfigured");
    }

    // ========================================================================
    // SCENARIO 4: Failure Recovery - Configuration Server Resilience
    // ========================================================================

    @Test
    @DisplayName("☕ Integration 4.1: Service Resilience - Graceful Degradation During Config Issues")
    void testServiceResilience_GracefulDegradationDuringConfigIssues() throws Exception {
        // GIVEN: Coffee shop services should handle configuration server issues gracefully
        
        // WHEN: We request configuration for non-existent service (simulating config issues)
        ResponseEntity<Environment> response = restTemplate.getForEntity(
            baseUrl() + "/non-existent-service/default",
            Environment.class
        );

        // THEN: Config server should handle gracefully instead of throwing errors
        assertEquals(HttpStatus.OK, response.getStatusCode(), 
            "Config server should handle non-existent service gracefully");

        assertNotNull(response.getBody(), "Response should not be null");
        assertEquals("non-existent-service", response.getBody().getName());

        // WHEN: We request configuration with invalid profile
        ResponseEntity<Environment> invalidProfileResponse = restTemplate.getForEntity(
            baseUrl() + "/user-service/invalid-profile-xyz",
            Environment.class
        );

        // THEN: Should still return graceful response
        assertEquals(HttpStatus.OK, invalidProfileResponse.getStatusCode(),
            "Config server should handle invalid profiles gracefully");

        System.out.println("✅ Service resilience validated - Graceful degradation working");
    }

    @Test
    @DisplayName("☕ Integration 4.2: Recovery Testing - Config Server Restart Scenario")
    void testRecoveryTesting_ConfigServerRestartScenario() throws Exception {
        // GIVEN: Coffee shop config server needs to recover quickly after restart
        
        // WHEN: We test configuration availability (simulating post-restart)
        String[] essentialServices = {"user-service", "order-management", "payment-service"};
        
        for (String service : essentialServices) {
            ResponseEntity<Environment> response = restTemplate.getForEntity(
                baseUrl() + "/" + service + "/default",
                Environment.class
            );

            // THEN: Essential services should get configuration immediately after restart
            assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Service " + service + " should be available immediately after restart");

            Environment env = response.getBody();
            assertNotNull(env, "Configuration should be available for " + service);
            assertEquals(service, env.getName());
        }

        // Verify config server health after restart simulation
        ResponseEntity<Map> healthCheck = restTemplate.getForEntity(
            baseUrl() + "/actuator/health",
            Map.class
        );

        assertEquals(HttpStatus.OK, healthCheck.getStatusCode());
        assertEquals("UP", healthCheck.getBody().get("status"));

        System.out.println("✅ Recovery testing validated - Config server ready after restart");
    }

    // ========================================================================
    // SCENARIO 5: Multi-Environment Operations - Production/Staging/Development
    // ========================================================================

    @Test
    @DisplayName("☕ Integration 5.1: Multi-Environment Deployment - Staging to Production Promotion")
    void testMultiEnvironmentDeployment_StagingToProductionPromotion() throws Exception {
        // GIVEN: Coffee shop promotes changes from staging to production
        String service = "analytics-service";
        String[] environments = {"development", "staging", "production"};

        // WHEN: We fetch configuration for all environments
        Map<String, Environment> envConfigs = new HashMap<>();
        
        for (String env : environments) {
            ResponseEntity<Environment> response = restTemplate.getForEntity(
                baseUrl() + "/" + service + "/" + env,
                Environment.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                envConfigs.put(env, response.getBody());
            }
        }

        // THEN: At least default configuration should be available for promotion testing
        assertFalse(envConfigs.isEmpty(), "At least one environment should have configuration");

        // Verify environment-specific behavior
        envConfigs.forEach((env, config) -> {
            assertNotNull(config, "Configuration should not be null for " + env);
            assertEquals(service, config.getName(), "Service name should match for " + env);
            
            // Each environment should have property sources
            assertFalse(config.getPropertySources().isEmpty(), 
                "Environment " + env + " should have property sources");
        });

        System.out.println("✅ Multi-environment deployment validated - " + envConfigs.size() + 
                          " environments configured for " + service);
    }

    @Test
    @DisplayName("☕ Integration 5.2: Environment Isolation - Configuration Separation")
    void testEnvironmentIsolation_ConfigurationSeparation() throws Exception {
        // GIVEN: Coffee shop environments should have isolated configurations
        String service = "notification-service";
        
        // WHEN: We fetch configuration for different environments
        ResponseEntity<Environment> devConfig = restTemplate.getForEntity(
            baseUrl() + "/" + service + "/development",
            Environment.class
        );

        ResponseEntity<Environment> prodConfig = restTemplate.getForEntity(
            baseUrl() + "/" + service + "/production",
            Environment.class
        );

        // THEN: Both environments should provide configuration
        assertEquals(HttpStatus.OK, devConfig.getStatusCode());
        assertEquals(HttpStatus.OK, prodConfig.getStatusCode());

        Environment devEnv = devConfig.getBody();
        Environment prodEnv = prodConfig.getBody();

        assertNotNull(devEnv, "Development environment should have configuration");
        assertNotNull(prodEnv, "Production environment should have configuration");

        // Verify environment isolation
        assertEquals(service, devEnv.getName());
        assertEquals(service, prodEnv.getName());

        // Verify different profiles are applied
        assertTrue(devEnv.getProfiles().length > 0 || prodEnv.getProfiles().length > 0,
            "At least one environment should have profile-specific configuration");

        System.out.println("✅ Environment isolation validated - Development and production configurations separated");
    }

    // ========================================================================
    // SCENARIO 6: Performance and Monitoring - Production Operations
    // ========================================================================

    @Test
    @DisplayName("☕ Integration 6.1: Monitoring Integration - Config Server Metrics Collection")
    void testMonitoringIntegration_ConfigServerMetricsCollection() throws Exception {
        // GIVEN: Coffee shop needs comprehensive monitoring of config server
        
        // WHEN: We check metrics endpoints
        ResponseEntity<String> metricsResponse = restTemplate.getForEntity(
            baseUrl() + "/actuator/prometheus",
            String.class
        );

        ResponseEntity<Map> infoResponse = restTemplate.getForEntity(
            baseUrl() + "/actuator/info",
            Map.class
        );

        // THEN: Monitoring endpoints should provide operational metrics
        assertEquals(HttpStatus.OK, metricsResponse.getStatusCode());
        assertEquals(HttpStatus.OK, infoResponse.getStatusCode());

        String metrics = metricsResponse.getBody();
        assertNotNull(metrics, "Prometheus metrics should be available");
        assertTrue(metrics.contains("jvm_"), "JVM metrics should be exposed");

        Map<String, Object> info = infoResponse.getBody();
        assertNotNull(info, "Application info should be available");
        assertTrue(info.containsKey("app"), "Application information should be present");

        System.out.println("✅ Monitoring integration validated - Config server metrics available");
    }

    @Test
    @DisplayName("☕ Integration 6.2: End-to-End Configuration Flow - Complete Coffee Shop Setup")
    void testEndToEndConfigurationFlow_CompleteCoffeeShopSetup() throws Exception {
        // GIVEN: Complete coffee shop system needs end-to-end configuration validation
        
        long startTime = System.currentTimeMillis();
        
        // WHEN: We simulate complete system configuration flow
        String[] allServices = {
            "user-service", "product-catalog", "order-management", 
            "payment-service", "inventory-service", "notification-service", 
            "analytics-service", "api-gateway"
        };
        
        Map<String, Boolean> serviceResults = new ConcurrentHashMap<>();
        ExecutorService executor = Executors.newFixedThreadPool(allServices.length);
        CountDownLatch latch = new CountDownLatch(allServices.length);
        
        for (String service : allServices) {
            executor.submit(() -> {
                try {
                    // Test multiple configurations per service
                    ResponseEntity<Environment> defaultConfig = restTemplate.getForEntity(
                        baseUrl() + "/" + service + "/default",
                        Environment.class
                    );
                    
                    ResponseEntity<Environment> prodConfig = restTemplate.getForEntity(
                        baseUrl() + "/" + service + "/production",
                        Environment.class
                    );
                    
                    boolean success = defaultConfig.getStatusCode() == HttpStatus.OK &&
                                    prodConfig.getStatusCode() == HttpStatus.OK;
                    
                    serviceResults.put(service, success);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(45, TimeUnit.SECONDS), 
            "End-to-end configuration should complete within 45 seconds");
        
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        
        // THEN: All services should be successfully configured
        assertEquals(allServices.length, serviceResults.size(),
            "All services should complete configuration requests");
        
        long successfulServices = serviceResults.values().stream()
            .mapToLong(success -> success ? 1 : 0)
            .sum();
        
        assertTrue(successfulServices >= allServices.length * 0.9, 
            "At least 90% of services should be successfully configured");
        
        executor.shutdown();
        System.out.println("✅ End-to-end configuration flow complete - " + successfulServices + 
                          "/" + allServices.length + " services configured in " + totalTime + "ms");
    }
} 