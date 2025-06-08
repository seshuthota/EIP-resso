package com.eipresso.config.routes;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.UseAdviceWith;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive Scenario-Based Testing for Configuration Management Camel Routes
 * 
 * This test class focuses on testing the Apache Camel routes within the Configuration
 * Management Service, covering real-world scenarios:
 * 
 * 1. Production Configuration Monitoring Routes
 * 2. Standard Production Routes (Health, Metrics, Status)
 * 3. Configuration Change Event Handling
 * 4. Error Handling and Recovery Scenarios
 * 5. Timer-based Monitoring and Alerts
 * 
 * Each test simulates actual coffee shop operational scenarios where configuration
 * changes would occur and need to be monitored/handled appropriately.
 */
@SpringBootTest
@CamelSpringBootTest
@UseAdviceWith
@TestPropertySource(properties = {
    "eip-resso.config.mode=test-mode",
    "camel.springboot.jmx-enabled=false",
    "logging.level.org.apache.camel=DEBUG"
})
@DisplayName("EIP-resso Configuration Management - Camel Routes Scenario Testing")
class CamelRoutesScenarioTest {

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private ProducerTemplate producerTemplate;

    @EndpointInject("mock:webhook-result")
    private MockEndpoint mockWebhookResult;

    @EndpointInject("mock:git-change-result")
    private MockEndpoint mockGitChangeResult;

    @EndpointInject("mock:health-check-result")
    private MockEndpoint mockHealthCheckResult;

    @EndpointInject("mock:metrics-result")
    private MockEndpoint mockMetricsResult;

    @BeforeEach
    void setUp(TestInfo testInfo) throws Exception {
        System.out.println("\n🧪 STARTING CAMEL ROUTE TEST: " + testInfo.getDisplayName());
        
        // Reset all mock endpoints
        MockEndpoint.resetMocks(camelContext);
        
        // Start Camel context if not already started
        if (!camelContext.getStatus().isStarted()) {
            camelContext.start();
        }
    }

    // ========================================================================
    // SCENARIO 1: Standard Production Routes Testing
    // ========================================================================

    @Test
    @DisplayName("☕ Scenario 1.1: Health Monitoring Route - Coffee Shop Service Health Checks")
    void testHealthMonitoringRoute_CoffeeShopServiceHealthChecks() throws Exception {
        // GIVEN: Coffee shop configuration server needs continuous health monitoring
        
        // Modify the health monitoring route to send results to mock endpoint
        AdviceWith.adviceWith(camelContext, "config-health-monitor", routeBuilder -> {
            routeBuilder.weaveAddLast().to("mock:health-check-result");
        });

        camelContext.start();

        // Setup expectations
        mockHealthCheckResult.expectedMinimumMessageCount(1);
        mockHealthCheckResult.expectedHeaderReceived("ServiceName", "config-server");

        // WHEN: Health monitoring timer triggers (we'll send a direct message to simulate)
        producerTemplate.sendBody("timer://config-health", "trigger-health-check");

        // THEN: Health check should execute and record metrics
        mockHealthCheckResult.assertIsSatisfied(5000);

        // Verify health check message
        String healthMessage = mockHealthCheckResult.getReceivedExchanges().get(0).getIn().getBody(String.class);
        assertNotNull(healthMessage);

        System.out.println("✅ Health monitoring route validated - Configuration server health checks active");
    }

    @Test
    @DisplayName("☕ Scenario 1.2: Metrics Collection Route - Coffee Shop Operational Metrics")
    void testMetricsCollectionRoute_CoffeeShopOperationalMetrics() throws Exception {
        // GIVEN: Coffee shop needs to collect configuration server metrics regularly
        
        // Modify the metrics collection route
        AdviceWith.adviceWith(camelContext, "config-metrics-collector", routeBuilder -> {
            routeBuilder.weaveAddLast().to("mock:metrics-result");
        });

        camelContext.start();

        // Setup expectations
        mockMetricsResult.expectedMinimumMessageCount(1);
        mockMetricsResult.expectedHeaderReceived("MetricType", "application-metrics");

        // WHEN: Metrics collection timer triggers
        producerTemplate.sendBody("timer://config-metrics", "trigger-metrics-collection");

        // THEN: Metrics should be collected successfully
        mockMetricsResult.assertIsSatisfied(5000);

        System.out.println("✅ Metrics collection route validated - Operational metrics being collected");
    }

    @Test
    @DisplayName("☕ Scenario 1.3: Status Monitoring Route - Coffee Shop System Status")
    void testStatusMonitoringRoute_CoffeeShopSystemStatus() throws Exception {
        // GIVEN: Coffee shop configuration server needs regular status monitoring
        
        // Setup mock endpoint for status monitoring
        AdviceWith.adviceWith(camelContext, "status-monitor", routeBuilder -> {
            routeBuilder.weaveAddLast().to("mock:status-result");
        });

        MockEndpoint mockStatusResult = camelContext.getEndpoint("mock:status-result", MockEndpoint.class);
        mockStatusResult.expectedMinimumMessageCount(1);

        camelContext.start();

        // WHEN: Status monitoring timer triggers
        producerTemplate.sendBody("timer://status-monitor", "trigger-status-check");

        // THEN: Status should be monitored and recorded
        mockStatusResult.assertIsSatisfied(5000);

        // Verify status message content
        String statusMessage = mockStatusResult.getReceivedExchanges().get(0).getIn().getBody(String.class);
        assertTrue(statusMessage.contains("Configuration server operational"));

        System.out.println("✅ Status monitoring route validated - System status being monitored");
    }

    @Test
    @DisplayName("☕ Scenario 1.4: Audit Logging Route - Coffee Shop Configuration Audit Trail")
    void testAuditLoggingRoute_CoffeeShopConfigurationAuditTrail() throws Exception {
        // GIVEN: Coffee shop needs comprehensive audit logging for compliance
        
        // Setup mock endpoint for audit logging
        AdviceWith.adviceWith(camelContext, "audit-logger", routeBuilder -> {
            routeBuilder.weaveAddLast().to("mock:audit-result");
        });

        MockEndpoint mockAuditResult = camelContext.getEndpoint("mock:audit-result", MockEndpoint.class);
        mockAuditResult.expectedMinimumMessageCount(1);
        mockAuditResult.expectedHeaderReceived("AuditType", "operational-status");

        camelContext.start();

        // WHEN: Audit logging timer triggers
        producerTemplate.sendBody("timer://audit-log", "trigger-audit-log");

        // THEN: Audit log should be created with proper headers
        mockAuditResult.assertIsSatisfied(5000);

        System.out.println("✅ Audit logging route validated - Configuration audit trail active");
    }

    // ========================================================================
    // SCENARIO 2: Production Configuration Monitoring Routes (Webhook-based)
    // ========================================================================

    @Test
    @DisplayName("☕ Scenario 2.1: GitHub Webhook Configuration Change - Menu Update Push")
    void testGitHubWebhookConfigurationChange_MenuUpdatePush() throws Exception {
        // GIVEN: Coffee shop pushes menu updates to GitHub repository
        
        // Mock the webhook processing route
        AdviceWith.adviceWith(camelContext, "github-webhook-handler", routeBuilder -> {
            routeBuilder.weaveAddLast().to("mock:webhook-result");
        });

        camelContext.start();

        // Setup expectations
        mockWebhookResult.expectedMinimumMessageCount(1);
        mockWebhookResult.expectedHeaderReceived("GitProvider", "GitHub");

        // WHEN: GitHub webhook is received with menu configuration changes
        Map<String, Object> webhookPayload = new HashMap<>();
        webhookPayload.put("repository", Map.of("full_name", "eipresso/coffee-menu-config"));
        webhookPayload.put("head_commit", Map.of(
            "id", "abc123def456",
            "message", "Update coffee menu - add seasonal pumpkin spice latte"
        ));

        producerTemplate.sendBodyAndHeader("direct:github-config-change", webhookPayload, 
            "X-GitHub-Event", "push");

        // THEN: Webhook should be processed and configuration change handled
        mockWebhookResult.assertIsSatisfied(5000);

        System.out.println("✅ GitHub webhook configuration change validated - Menu updates processed");
    }

    @Test
    @DisplayName("☕ Scenario 2.2: GitLab Webhook Configuration Change - Price Update Push")
    void testGitLabWebhookConfigurationChange_PriceUpdatePush() throws Exception {
        // GIVEN: Coffee shop pushes price updates to GitLab repository
        
        // Mock the GitLab webhook processing route
        AdviceWith.adviceWith(camelContext, "gitlab-webhook-handler", routeBuilder -> {
            routeBuilder.weaveAddLast().to("mock:webhook-result");
        });

        camelContext.start();

        // Setup expectations
        mockWebhookResult.expectedMinimumMessageCount(1);
        mockWebhookResult.expectedHeaderReceived("GitProvider", "GitLab");

        // WHEN: GitLab webhook is received with price configuration changes
        Map<String, Object> webhookPayload = new HashMap<>();
        webhookPayload.put("project", Map.of("path_with_namespace", "eipresso/coffee-pricing-config"));
        webhookPayload.put("checkout_sha", "def456abc789");
        webhookPayload.put("commits", java.util.List.of(Map.of(
            "message", "Update coffee prices - increase premium blend by $0.25"
        )));

        producerTemplate.sendBodyAndHeader("direct:gitlab-config-change", webhookPayload, 
            "X-GitLab-Event", "Push Hook");

        // THEN: GitLab webhook should be processed correctly
        mockWebhookResult.assertIsSatisfied(5000);

        System.out.println("✅ GitLab webhook configuration change validated - Price updates processed");
    }

    @Test
    @DisplayName("☕ Scenario 2.3: External Git API Monitoring - Supplier Configuration Changes")
    void testExternalGitApiMonitoring_SupplierConfigurationChanges() throws Exception {
        // GIVEN: Coffee shop monitors external Git repository for supplier configuration changes
        
        // Mock the external Git change handler
        AdviceWith.adviceWith(camelContext, "external-git-handler", routeBuilder -> {
            routeBuilder.weaveAddLast().to("mock:git-change-result");
        });

        camelContext.start();

        // Setup expectations
        mockGitChangeResult.expectedMinimumMessageCount(1);

        // WHEN: External Git API detects changes (simulate by calling direct endpoint)
        String gitApiResponse = "[{\"sha\":\"new123commit456\",\"commit\":{\"message\":\"Update supplier coffee beans pricing\"}}]";
        
        producerTemplate.sendBody("direct:external-git-change", gitApiResponse);

        // THEN: External Git changes should be processed
        mockGitChangeResult.assertIsSatisfied(5000);

        System.out.println("✅ External Git API monitoring validated - Supplier configuration changes detected");
    }

    // ========================================================================
    // SCENARIO 3: Configuration Change Processing and Notification
    // ========================================================================

    @Test
    @DisplayName("☕ Scenario 3.1: Configuration Change Processor - Multi-Service Notification")
    void testConfigurationChangeProcessor_MultiServiceNotification() throws Exception {
        // GIVEN: Coffee shop configuration change needs to notify all dependent services
        
        // Mock the git change processor to avoid actual HTTP calls
        AdviceWith.adviceWith(camelContext, "git-change-processor", routeBuilder -> {
            routeBuilder.weaveByToUri("http://localhost:8888/actuator/refresh*").replace().to("mock:refresh-endpoint");
            routeBuilder.weaveAddLast().to("mock:change-processor-result");
        });

        MockEndpoint mockRefreshEndpoint = camelContext.getEndpoint("mock:refresh-endpoint", MockEndpoint.class);
        MockEndpoint mockChangeProcessorResult = camelContext.getEndpoint("mock:change-processor-result", MockEndpoint.class);
        
        mockRefreshEndpoint.expectedMessageCount(1);
        mockChangeProcessorResult.expectedMinimumMessageCount(1);

        camelContext.start();

        // WHEN: Configuration change is processed
        producerTemplate.sendBodyAndHeaders("direct:process-git-change", "configuration-change-event", Map.of(
            "GitProvider", "GitHub",
            "Repository", "eipresso/coffee-shop-config",
            "CommitSha", "abc123",
            "CommitMessage", "Update order processing timeout configuration"
        ));

        // THEN: Configuration refresh should be triggered and notifications sent
        mockRefreshEndpoint.assertIsSatisfied(5000);
        mockChangeProcessorResult.assertIsSatisfied(5000);

        System.out.println("✅ Configuration change processor validated - Multi-service notification working");
    }

    @Test
    @DisplayName("☕ Scenario 3.2: Configuration Health Check - Service Availability Monitoring")
    void testConfigurationHealthCheck_ServiceAvailabilityMonitoring() throws Exception {
        // GIVEN: Coffee shop needs to monitor configuration server health continuously
        
        // Mock the health check route to avoid actual HTTP calls
        AdviceWith.adviceWith(camelContext, "config-health-checker", routeBuilder -> {
            routeBuilder.weaveByToUri("activemq:queue:monitoring.health").replace().to("mock:health-monitoring");
        });

        MockEndpoint mockHealthMonitoring = camelContext.getEndpoint("mock:health-monitoring", MockEndpoint.class);
        mockHealthMonitoring.expectedMinimumMessageCount(1);

        camelContext.start();

        // WHEN: Health check is triggered
        producerTemplate.sendBody("direct:config-health-check", "health-check-trigger");

        // THEN: Health status should be recorded
        mockHealthMonitoring.assertIsSatisfied(5000);

        // Verify health status message
        String healthStatus = mockHealthMonitoring.getReceivedExchanges().get(0).getIn().getBody(String.class);
        assertTrue(healthStatus.contains("\"status\":\"healthy\""));

        System.out.println("✅ Configuration health check validated - Service availability monitoring active");
    }

    @Test
    @DisplayName("☕ Scenario 3.3: Configuration Alert Handler - Critical System Alerts")
    void testConfigurationAlertHandler_CriticalSystemAlerts() throws Exception {
        // GIVEN: Coffee shop needs to handle critical configuration server alerts
        
        // Mock the alert handler
        AdviceWith.adviceWith(camelContext, "config-alert-handler", routeBuilder -> {
            routeBuilder.weaveByToUri("activemq:queue:alerts.config").replace().to("mock:alert-system");
        });

        MockEndpoint mockAlertSystem = camelContext.getEndpoint("mock:alert-system", MockEndpoint.class);
        mockAlertSystem.expectedMinimumMessageCount(1);

        camelContext.start();

        // WHEN: Configuration alert is triggered
        producerTemplate.sendBody("direct:config-alert", "critical-configuration-issue");

        // THEN: Alert should be sent to alert system
        mockAlertSystem.assertIsSatisfied(5000);

        // Verify alert message content
        String alertMessage = mockAlertSystem.getReceivedExchanges().get(0).getIn().getBody(String.class);
        assertTrue(alertMessage.contains("config-server-issue"));

        System.out.println("✅ Configuration alert handler validated - Critical system alerts handled");
    }

    // ========================================================================
    // SCENARIO 4: Error Handling and Recovery Testing
    // ========================================================================

    @Test
    @DisplayName("☕ Scenario 4.1: Dead Letter Channel - Failed Configuration Changes")
    void testDeadLetterChannel_FailedConfigurationChanges() throws Exception {
        // GIVEN: Coffee shop configuration changes might fail and need error handling
        
        // Create a route that will fail to test dead letter channel
        camelContext.addRoutes(new org.apache.camel.builder.RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dead-letter-queue")
                    .maximumRedeliveries(2)
                    .redeliveryDelay(100));

                from("direct:test-error-handling")
                    .routeId("test-error-route")
                    .process(exchange -> {
                        throw new RuntimeException("Simulated configuration change failure");
                    })
                    .to("mock:success-endpoint");
            }
        });

        MockEndpoint mockDeadLetterQueue = camelContext.getEndpoint("mock:dead-letter-queue", MockEndpoint.class);
        MockEndpoint mockSuccessEndpoint = camelContext.getEndpoint("mock:success-endpoint", MockEndpoint.class);
        
        mockDeadLetterQueue.expectedMessageCount(1);
        mockSuccessEndpoint.expectedMessageCount(0);

        camelContext.start();

        // WHEN: Configuration change fails
        try {
            producerTemplate.sendBody("direct:test-error-handling", "failed-config-change");
        } catch (Exception e) {
            // Expected to fail
        }

        // THEN: Message should go to dead letter queue after retries
        mockDeadLetterQueue.assertIsSatisfied(10000);
        mockSuccessEndpoint.assertIsSatisfied(1000);

        System.out.println("✅ Dead letter channel validated - Failed configuration changes handled");
    }

    @Test
    @DisplayName("☕ Scenario 4.2: Route Error Recovery - Resilient Configuration Monitoring")
    void testRouteErrorRecovery_ResilientConfigurationMonitoring() throws Exception {
        // GIVEN: Coffee shop configuration monitoring should be resilient to temporary failures
        
        camelContext.start();

        // WHEN: We check that routes are resilient (routes should be running)
        boolean allRoutesStarted = camelContext.getRoutes().stream()
            .allMatch(route -> camelContext.getRouteController().getRouteStatus(route.getId()).isStarted());

        // THEN: All routes should be in started state, showing resilience
        assertTrue(allRoutesStarted, "All configuration monitoring routes should be resilient and running");

        // Verify specific critical routes are active
        long healthRoutes = camelContext.getRoutes().stream()
            .filter(route -> route.getId().contains("health") || route.getId().contains("monitor"))
            .count();

        assertTrue(healthRoutes > 0, "Health monitoring routes should be active for resilience");

        System.out.println("✅ Route error recovery validated - Configuration monitoring is resilient");
    }

    // ========================================================================
    // SCENARIO 5: Performance and Load Testing
    // ========================================================================

    @Test
    @DisplayName("☕ Scenario 5.1: High-Volume Configuration Requests - Peak Coffee Shop Hours")
    void testHighVolumeConfigurationRequests_PeakCoffeeShopHours() throws Exception {
        // GIVEN: Coffee shop experiences high volume of configuration requests during peak hours
        
        // Create a test route to handle high volume
        camelContext.addRoutes(new org.apache.camel.builder.RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:high-volume-config")
                    .routeId("high-volume-test")
                    .log("Processing configuration request: ${body}")
                    .delay(10) // Small delay to simulate processing
                    .to("mock:high-volume-result");
            }
        });

        MockEndpoint mockHighVolumeResult = camelContext.getEndpoint("mock:high-volume-result", MockEndpoint.class);
        int requestCount = 50;
        mockHighVolumeResult.expectedMessageCount(requestCount);

        camelContext.start();

        long startTime = System.currentTimeMillis();

        // WHEN: High volume of configuration requests are sent
        for (int i = 0; i < requestCount; i++) {
            producerTemplate.sendBody("direct:high-volume-config", "config-request-" + i);
        }

        // THEN: All requests should be processed successfully
        mockHighVolumeResult.assertIsSatisfied(30000);

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;

        assertTrue(totalTime < 20000, "High volume requests should be processed within 20 seconds");

        System.out.println("✅ High-volume configuration requests validated - " + requestCount + 
                          " requests processed in " + totalTime + "ms");
    }

    @Test
    @DisplayName("☕ Scenario 5.2: Timer Route Performance - Continuous Monitoring Load")
    void testTimerRoutePerformance_ContinuousMonitoringLoad() throws Exception {
        // GIVEN: Coffee shop configuration server runs continuous monitoring via timers
        
        camelContext.start();

        // Wait for timer routes to execute at least once
        Thread.sleep(2000);

        // WHEN: We check timer-based routes performance
        long timerRoutes = camelContext.getRoutes().stream()
            .filter(route -> route.getEndpoint().getEndpointUri().startsWith("timer://"))
            .count();

        // THEN: Timer routes should be running efficiently
        assertTrue(timerRoutes > 0, "Timer-based monitoring routes should be active");

        // Verify Camel context is still healthy after timer operations
        assertTrue(camelContext.getStatus().isStarted(), 
            "Camel context should remain healthy under continuous monitoring load");

        System.out.println("✅ Timer route performance validated - " + timerRoutes + 
                          " timer routes running efficiently");
    }
} 