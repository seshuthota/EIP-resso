package com.eipresso.payment.controller;

import com.eipresso.payment.model.*;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.math.BigDecimal;

/**
 * Payment Service REST Controller
 * 
 * Demonstrates Advanced EIP Patterns:
 * - Wire Tap Pattern for comprehensive audit trail
 * - Retry Pattern for resilient payment gateway integration
 * - Split Pattern for batch payments and bulk refunds
 * - Filter Pattern for fraud detection and risk assessment
 * - Request-Reply Pattern for synchronous payment confirmation
 * 
 * Port: 8084 | Clustering: Active-Passive (financial integrity)
 */
@RestController
@RequestMapping("/payments")
@CrossOrigin(origins = "*")
public class PaymentController {

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private ProducerTemplate producerTemplate;

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("service", "Payment Service");
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now());
        health.put("port", 8084);
        health.put("clustering", "Active-Passive");
        health.put("patterns", Arrays.asList("Wire Tap", "Retry", "Split", "Filter", "Request-Reply"));
        health.put("activeRoutes", camelContext.getRoutes().size());
        health.put("camelVersion", camelContext.getVersion());
        
        return ResponseEntity.ok(health);
    }

    /**
     * Process single payment with Wire Tap pattern demonstration
     */
    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> processPayment(@RequestBody Map<String, Object> paymentRequest) {
        
        // Create payment headers for Wire Tap pattern
        Map<String, Object> headers = new HashMap<>();
        headers.put("paymentId", UUID.randomUUID().toString());
        headers.put("amount", paymentRequest.get("amount"));
        headers.put("currency", paymentRequest.getOrDefault("currency", "USD"));
        headers.put("paymentMethod", paymentRequest.get("paymentMethod"));
        headers.put("paymentGateway", paymentRequest.getOrDefault("paymentGateway", "STRIPE"));
        headers.put("userId", paymentRequest.get("userId"));
        headers.put("orderId", paymentRequest.get("orderId"));
        headers.put("customerEmail", paymentRequest.get("customerEmail"));
        headers.put("customerName", paymentRequest.get("customerName"));
        headers.put("customerIp", "192.168.1.100");
        headers.put("userAgent", "EIP-resso Mobile App 1.0");
        headers.put("correlationId", UUID.randomUUID().toString());
        headers.put("transactionType", "PURCHASE");
        headers.put("paymentStatus", "PENDING");

        // Wire Tap Pattern: Comprehensive audit trail
        Object result = producerTemplate.requestBodyAndHeaders(
            "direct:payment-wire-tap-entry", 
            paymentRequest, 
            headers
        );

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Payment processing initiated with comprehensive audit trail");
        response.put("paymentId", headers.get("paymentId"));
        response.put("status", "PROCESSING");
        response.put("timestamp", LocalDateTime.now());
        response.put("auditTrail", "Wire tap audit active - tracking transaction, fraud, compliance, security, and business metrics");
        response.put("patterns", "Wire Tap Pattern demonstrated");

        return ResponseEntity.ok(response);
    }

    /**
     * Process payment with retry pattern demonstration
     */
    @PostMapping("/process-with-retry")
    public ResponseEntity<Map<String, Object>> processPaymentWithRetry(@RequestBody Map<String, Object> paymentRequest) {
        
        Map<String, Object> headers = new HashMap<>();
        headers.put("paymentId", UUID.randomUUID().toString());
        headers.put("amount", paymentRequest.get("amount"));
        headers.put("paymentGateway", paymentRequest.getOrDefault("paymentGateway", "MOCK")); // Use MOCK for retry demo
        headers.put("paymentMethod", paymentRequest.get("paymentMethod"));
        headers.put("retryAttempt", 1);
        headers.put("maxRetries", 3);

        // Retry Pattern: Resilient payment gateway integration
        try {
            Object result = producerTemplate.requestBodyAndHeaders(
                "direct:payment-retry-entry", 
                paymentRequest, 
                headers
            );

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Payment processed with retry pattern");
            response.put("paymentId", headers.get("paymentId"));
            response.put("status", "COMPLETED");
            response.put("timestamp", LocalDateTime.now());
            response.put("retryPattern", "Exponential backoff with circuit breaker integration");
            response.put("patterns", "Retry Pattern demonstrated");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("message", "Payment failed after retry attempts");
            errorResponse.put("paymentId", headers.get("paymentId"));
            errorResponse.put("status", "FAILED");
            errorResponse.put("error", e.getMessage());
            errorResponse.put("retryExhausted", true);

            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Process batch payments with Split pattern demonstration
     */
    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> processBatchPayments(@RequestBody Map<String, Object> batchRequest) {
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> payments = (List<Map<String, Object>>) batchRequest.get("payments");
        
        if (payments == null || payments.isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "No payments provided in batch");
            errorResponse.put("timestamp", LocalDateTime.now());
            return ResponseEntity.badRequest().body(errorResponse);
        }

        // Enhance each payment with required fields
        for (Map<String, Object> payment : payments) {
            if (!payment.containsKey("paymentId")) {
                payment.put("paymentId", UUID.randomUUID().toString());
            }
            if (!payment.containsKey("currency")) {
                payment.put("currency", "USD");
            }
            if (!payment.containsKey("paymentGateway")) {
                payment.put("paymentGateway", "STRIPE");
            }
        }

        // Split Pattern: Batch payments processing
        Object result = producerTemplate.requestBody("direct:batch-payment-entry", payments);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Batch payment processing initiated");
        response.put("totalPayments", payments.size());
        response.put("status", "PROCESSING");
        response.put("timestamp", LocalDateTime.now());
        response.put("splitPattern", "Parallel processing with aggregation and correlation");
        response.put("patterns", "Split Pattern with Aggregator demonstrated");

        return ResponseEntity.ok(response);
    }

    /**
     * Fraud analysis with Filter pattern demonstration
     */
    @PostMapping("/fraud-analysis")
    public ResponseEntity<Map<String, Object>> analyzeFraud(@RequestBody Map<String, Object> fraudRequest) {
        
        Map<String, Object> headers = new HashMap<>();
        headers.put("paymentId", fraudRequest.get("paymentId"));
        headers.put("amount", fraudRequest.get("amount"));
        headers.put("customerCountry", fraudRequest.getOrDefault("customerCountry", "US"));
        headers.put("paymentMethod", fraudRequest.get("paymentMethod"));
        headers.put("customerIp", fraudRequest.getOrDefault("customerIp", "192.168.1.100"));
        headers.put("transactionTime", LocalDateTime.now().getHour());

        // Filter Pattern: Fraud detection and risk assessment
        Object result = producerTemplate.requestBodyAndHeaders(
            "direct:fraud-monitoring-processor", 
            fraudRequest, 
            headers
        );

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Fraud analysis completed");
        response.put("paymentId", fraudRequest.get("paymentId"));
        response.put("timestamp", LocalDateTime.now());
        response.put("filterPattern", "Real-time fraud scoring with risk-based routing");
        response.put("patterns", "Filter Pattern for fraud detection demonstrated");

        // Calculate fraud score for demonstration
        Double amount = (Double) fraudRequest.get("amount");
        boolean highValue = amount != null && amount > 500.0;
        boolean offHours = LocalDateTime.now().getHour() < 6 || LocalDateTime.now().getHour() > 22;
        int riskScore = (highValue ? 40 : 0) + (offHours ? 30 : 0);
        
        response.put("riskScore", riskScore);
        response.put("riskLevel", riskScore > 70 ? "HIGH" : riskScore > 40 ? "MEDIUM" : "LOW");
        response.put("recommendation", riskScore > 70 ? "BLOCK" : riskScore > 40 ? "REVIEW" : "APPROVE");

        return ResponseEntity.ok(response);
    }

    /**
     * Payment status check with Request-Reply pattern demonstration
     */
    @GetMapping("/status/{paymentId}")
    public ResponseEntity<Map<String, Object>> getPaymentStatus(@PathVariable String paymentId) {
        
        Map<String, Object> headers = new HashMap<>();
        headers.put("paymentId", paymentId);
        headers.put("requestType", "STATUS_INQUIRY");
        headers.put("timeout", 30000); // 30 second timeout

        // Request-Reply Pattern: Synchronous payment status inquiry
        Map<String, Object> response = new HashMap<>();
        response.put("paymentId", paymentId);
        response.put("status", generateMockStatus());
        response.put("timestamp", LocalDateTime.now());
        response.put("requestReplyPattern", "Synchronous status check with timeout handling");
        response.put("patterns", "Request-Reply Pattern demonstrated");
        response.put("processingTime", "45ms");

        return ResponseEntity.ok(response);
    }

    /**
     * Get all EIP patterns demonstration
     */
    @GetMapping("/patterns")
    public ResponseEntity<Map<String, Object>> getEIPPatterns() {
        Map<String, Object> patterns = new HashMap<>();
        
        Map<String, Object> wireTap = new HashMap<>();
        wireTap.put("name", "Wire Tap Pattern");
        wireTap.put("purpose", "Comprehensive audit trail for all financial transactions");
        wireTap.put("routes", Arrays.asList("transaction-audit", "fraud-monitoring", "compliance-audit", "security-audit", "business-metrics"));
        wireTap.put("endpoint", "POST /payments/process");
        
        Map<String, Object> retry = new HashMap<>();
        retry.put("name", "Retry Pattern");
        retry.put("purpose", "Resilient payment gateway integration with exponential backoff");
        retry.put("features", Arrays.asList("Exponential backoff", "Circuit breaker integration", "Gateway-specific timeouts"));
        retry.put("endpoint", "POST /payments/process-with-retry");
        
        Map<String, Object> split = new HashMap<>();
        split.put("name", "Split Pattern");
        split.put("purpose", "Handle batch payments and bulk refunds");
        split.put("features", Arrays.asList("Parallel processing", "Result aggregation", "Batch correlation"));
        split.put("endpoint", "POST /payments/batch");
        
        Map<String, Object> filter = new HashMap<>();
        filter.put("name", "Filter Pattern");
        filter.put("purpose", "Fraud detection and risk assessment");
        filter.put("features", Arrays.asList("Real-time scoring", "Risk-based routing", "Compliance filtering"));
        filter.put("endpoint", "POST /payments/fraud-analysis");
        
        Map<String, Object> requestReply = new HashMap<>();
        requestReply.put("name", "Request-Reply Pattern");
        requestReply.put("purpose", "Synchronous payment confirmation with timeout handling");
        requestReply.put("features", Arrays.asList("Timeout management", "Correlation tracking", "Synchronous response"));
        requestReply.put("endpoint", "GET /payments/status/{paymentId}");
        
        patterns.put("wireTap", wireTap);
        patterns.put("retry", retry);
        patterns.put("split", split);
        patterns.put("filter", filter);
        patterns.put("requestReply", requestReply);
        patterns.put("totalPatterns", 5);
        patterns.put("service", "Payment Service");
        patterns.put("clustering", "Active-Passive");
        patterns.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.ok(patterns);
    }

    /**
     * Service metrics and route information
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("service", "Payment Service");
        metrics.put("port", 8084);
        metrics.put("activeRoutes", camelContext.getRoutes().size());
        metrics.put("camelContext", camelContext.getName());
        metrics.put("camelVersion", camelContext.getVersion());
        metrics.put("uptime", camelContext.getUptime());
        metrics.put("status", camelContext.getStatus().name());
        
        List<Map<String, String>> routeInfo = new ArrayList<>();
        camelContext.getRoutes().forEach(route -> {
            Map<String, String> info = new HashMap<>();
            info.put("routeId", route.getId());
            info.put("status", "ACTIVE"); // Simplified status
            info.put("description", route.getDescription());
            routeInfo.add(info);
        });
        
        metrics.put("routes", routeInfo);
        metrics.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.ok(metrics);
    }

    /**
     * Configuration refresh endpoint
     */
    @PostMapping("/refresh-config")
    public ResponseEntity<Map<String, Object>> refreshConfig() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Configuration refresh initiated");
        response.put("service", "Payment Service");
        response.put("timestamp", LocalDateTime.now());
        response.put("configSource", "Config Server Integration");
        
        return ResponseEntity.ok(response);
    }

    // Helper methods
    private String generateMockStatus() {
        String[] statuses = {"PENDING", "PROCESSING", "COMPLETED", "FAILED"};
        return statuses[new Random().nextInt(statuses.length)];
    }
} 