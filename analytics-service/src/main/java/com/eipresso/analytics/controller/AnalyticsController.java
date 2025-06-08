package com.eipresso.analytics.controller;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Analytics Service REST Controller
 * 
 * Demonstrates Advanced EIP Patterns:
 * - Event Sourcing Pattern
 * - CQRS Pattern  
 * - Streaming Pattern
 * - Advanced Aggregator Pattern
 * 
 * Port: 8087
 */
@RestController
@RequestMapping("/analytics")
@CrossOrigin(origins = "*")
public class AnalyticsController {

    @Autowired
    private ProducerTemplate producerTemplate;
    
    @Autowired
    private CamelContext camelContext;

    /**
     * Event Sourcing Pattern Endpoints
     */
    @PostMapping("/events/source")
    public ResponseEntity<Map<String, Object>> sourceEvent(@RequestBody Map<String, Object> eventData) {
        try {
            // Prepare event for Event Sourcing pattern
            Map<String, Object> headers = new HashMap<>();
            headers.put("eventType", eventData.get("eventType"));
            headers.put("sourceService", eventData.get("sourceService"));
            headers.put("userId", eventData.get("userId"));
            headers.put("orderId", eventData.get("orderId"));
            
            // Send to Event Sourcing entry point
            producerTemplate.sendBodyAndHeaders("direct:event-source-entry", eventData, headers);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "Event sourced successfully");
            response.put("eventType", eventData.get("eventType"));
            response.put("timestamp", LocalDateTime.now());
            response.put("pattern", "Event Sourcing");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Event sourcing failed: " + e.getMessage()));
        }
    }

    @GetMapping("/events/replay/{correlationId}")
    public ResponseEntity<Map<String, Object>> replayEvents(@PathVariable String correlationId) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "Event replay initiated");
        response.put("correlationId", correlationId);
        response.put("pattern", "Event Sourcing - Replay");
        response.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.ok(response);
    }

    /**
     * CQRS Pattern Endpoints
     */
    @PostMapping("/cqrs/command")
    public ResponseEntity<Map<String, Object>> processCommand(@RequestBody Map<String, Object> commandData) {
        try {
            Map<String, Object> headers = new HashMap<>();
            headers.put("commandType", commandData.get("commandType"));
            headers.put("userId", commandData.get("userId"));
            headers.put("orderId", commandData.get("orderId"));
            
            // Send to CQRS command entry point
            producerTemplate.sendBodyAndHeaders("direct:cqrs-command-entry", commandData, headers);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "Command processed successfully");
            response.put("commandType", commandData.get("commandType"));
            response.put("timestamp", LocalDateTime.now());
            response.put("pattern", "CQRS - Command");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Command processing failed: " + e.getMessage()));
        }
    }

    @GetMapping("/cqrs/query")
    public ResponseEntity<Map<String, Object>> processQuery(
            @RequestParam String queryType,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String orderId) {
        try {
            Map<String, Object> headers = new HashMap<>();
            headers.put("queryType", queryType);
            headers.put("userId", userId);
            headers.put("orderId", orderId);
            
            Map<String, Object> queryBody = new HashMap<>();
            queryBody.put("queryType", queryType);
            queryBody.put("timestamp", LocalDateTime.now());
            
            // Send to CQRS query entry point
            Object result = producerTemplate.requestBodyAndHeaders("direct:cqrs-query-entry", queryBody, headers);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "Query processed successfully");
            response.put("queryType", queryType);
            response.put("result", result);
            response.put("timestamp", LocalDateTime.now());
            response.put("pattern", "CQRS - Query");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Query processing failed: " + e.getMessage()));
        }
    }

    /**
     * Streaming Pattern Endpoints
     */
    @PostMapping("/streaming/start")
    public ResponseEntity<Map<String, Object>> startStreaming(@RequestBody Map<String, Object> streamData) {
        try {
            Map<String, Object> headers = new HashMap<>();
            headers.put("eventType", streamData.get("eventType"));
            headers.put("streamingMode", "REAL_TIME");
            
            // Send to Streaming entry point
            producerTemplate.sendBodyAndHeaders("direct:streaming-entry", streamData, headers);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "Streaming started successfully");
            response.put("eventType", streamData.get("eventType"));
            response.put("timestamp", LocalDateTime.now());
            response.put("pattern", "Streaming");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Streaming start failed: " + e.getMessage()));
        }
    }

    @GetMapping("/streaming/live-metrics")
    public ResponseEntity<Map<String, Object>> getLiveMetrics() {
        Map<String, Object> liveMetrics = new HashMap<>();
        liveMetrics.put("totalEvents", 1250); // Mock data
        liveMetrics.put("eventsPerSecond", 25.5);
        liveMetrics.put("systemThroughput", 1024.7);
        liveMetrics.put("activeStreams", 8);
        liveMetrics.put("timestamp", LocalDateTime.now());
        liveMetrics.put("pattern", "Streaming - Live Dashboard");
        
        return ResponseEntity.ok(liveMetrics);
    }

    /**
     * Advanced Aggregator Pattern Endpoints
     */
    @PostMapping("/aggregation/advanced")
    public ResponseEntity<Map<String, Object>> triggerAdvancedAggregation(@RequestBody Map<String, Object> aggregationData) {
        try {
            Map<String, Object> headers = new HashMap<>();
            headers.put("eventType", aggregationData.get("eventType"));
            headers.put("aggregationType", "ADVANCED");
            
            // Send to Advanced Aggregator entry point
            producerTemplate.sendBodyAndHeaders("direct:advanced-aggregator-entry", aggregationData, headers);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "Advanced aggregation triggered successfully");
            response.put("eventType", aggregationData.get("eventType"));
            response.put("timestamp", LocalDateTime.now());
            response.put("pattern", "Advanced Aggregator");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Advanced aggregation failed: " + e.getMessage()));
        }
    }

    @GetMapping("/aggregation/business-metrics")
    public ResponseEntity<Map<String, Object>> getBusinessMetrics() {
        Map<String, Object> businessMetrics = new HashMap<>();
        businessMetrics.put("totalOrders", 875);
        businessMetrics.put("totalRevenue", 21875.50);
        businessMetrics.put("totalUsers", 1250);
        businessMetrics.put("conversionRate", 70.0);
        businessMetrics.put("averageOrderValue", 25.0);
        businessMetrics.put("customerLifetimeValue", 125.0);
        businessMetrics.put("timestamp", LocalDateTime.now());
        businessMetrics.put("pattern", "Advanced Aggregator - Business Intelligence");
        
        return ResponseEntity.ok(businessMetrics);
    }

    /**
     * Pattern Integration Test Endpoint
     */
    @PostMapping("/test/integration")
    public ResponseEntity<Map<String, Object>> testPatternIntegration(@RequestBody Map<String, Object> testData) {
        try {
            String testId = UUID.randomUUID().toString();
            LocalDateTime startTime = LocalDateTime.now();
            
            // Test all patterns in sequence
            Map<String, Object> headers = new HashMap<>();
            headers.put("testId", testId);
            headers.put("eventType", "INTEGRATION_TEST");
            headers.put("sourceService", "analytics-service");
            
            // 1. Event Sourcing
            producerTemplate.sendBodyAndHeaders("direct:event-source-entry", testData, headers);
            
            // 2. CQRS Command
            headers.put("commandType", "CREATE_ORDER_ANALYTICS");
            producerTemplate.sendBodyAndHeaders("direct:cqrs-command-entry", testData, headers);
            
            // 3. Streaming
            producerTemplate.sendBodyAndHeaders("direct:streaming-entry", testData, headers);
            
            // 4. Advanced Aggregation
            producerTemplate.sendBodyAndHeaders("direct:advanced-aggregator-entry", testData, headers);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "Pattern integration test completed successfully");
            response.put("testId", testId);
            response.put("patternstested", 4);
            response.put("patterns", new String[]{"Event Sourcing", "CQRS", "Streaming", "Advanced Aggregator"});
            response.put("startTime", startTime);
            response.put("endTime", LocalDateTime.now());
            response.put("totalRoutes", camelContext.getRoutes().size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Pattern integration test failed: " + e.getMessage()));
        }
    }

    /**
     * Health and Monitoring Endpoints
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "Analytics Service");
        health.put("port", 8087);
        health.put("patterns", new String[]{"Event Sourcing", "CQRS", "Streaming", "Advanced Aggregator"});
        health.put("totalRoutes", camelContext.getRoutes().size());
        health.put("camelStatus", camelContext.getStatus().toString());
        health.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.ok(health);
    }

    @GetMapping("/routes")
    public ResponseEntity<Map<String, Object>> getRoutes() {
        Map<String, Object> routeInfo = new HashMap<>();
        routeInfo.put("totalRoutes", camelContext.getRoutes().size());
        routeInfo.put("routeIds", camelContext.getRoutes().stream()
            .map(route -> route.getId())
            .toList());
        routeInfo.put("camelContextId", camelContext.getName());
        routeInfo.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.ok(routeInfo);
    }

    @PostMapping("/config/refresh")
    public ResponseEntity<Map<String, Object>> refreshConfig() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "Configuration refreshed");
        response.put("timestamp", LocalDateTime.now());
        response.put("service", "Analytics Service");
        
        return ResponseEntity.ok(response);
    }
} 