package com.eipresso.payment.routes;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.aggregate.GroupedExchangeAggregationStrategy;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Split Pattern Implementation for Payment Service
 * 
 * EIP Pattern: Split Pattern
 * Purpose: Handle batch payments and bulk refunds
 * Clustering: Active-Passive compatible (batch consistency)
 * 
 * Routes:
 * 1. batch-payment-entry: Main batch payment entry point
 * 2. split-payment-processor: Split batch into individual payments
 * 3. individual-payment-processor: Process individual payments
 * 4. aggregate-payment-results: Aggregate payment results
 * 5. bulk-refund-processor: Handle bulk refund operations
 */
@Component
public class SplitRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        
        // Global error handling for Split pattern
        errorHandler(deadLetterChannel("direct:split-dead-letter")
            .maximumRedeliveries(3)
            .redeliveryDelay(2000)
            .retryAttemptedLogLevel(LoggingLevel.WARN)
            .useExponentialBackOff()
            .backOffMultiplier(2)
            .maximumRedeliveryDelay(15000));

        /**
         * Route 1: Batch Payment Entry Point
         */
        from("direct:batch-payment-entry")
            .routeId("batch-payment-entry")
            .description("Split Pattern: Main batch payment entry point")
            .log("📦 Processing batch payment request")
            
            .process(exchange -> {
                String batchId = UUID.randomUUID().toString();
                exchange.getIn().setHeader("batchId", batchId);
                exchange.getIn().setHeader("batchStartTime", LocalDateTime.now());
                exchange.getIn().setHeader("batchType", "PAYMENT");
                
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> payments = exchange.getIn().getBody(List.class);
                int totalPayments = payments != null ? payments.size() : 0;
                
                exchange.getIn().setHeader("totalPayments", totalPayments);
                exchange.getIn().setHeader("processedPayments", 0);
                exchange.getIn().setHeader("successfulPayments", 0);
                exchange.getIn().setHeader("failedPayments", 0);
                
                log.info("📦 Batch payment initiated: {} with {} payments", batchId, totalPayments);
            })
            
            .choice()
                .when(simple("${header.totalPayments} > 0"))
                    .to("direct:split-payment-processor")
                .otherwise()
                    .log(LoggingLevel.WARN, "⚠️ Empty batch payment request")
                    .process(exchange -> {
                        exchange.getIn().setHeader("batchStatus", "EMPTY");
                    })
            .end()
            
            .log("✅ Batch payment processing initiated");

        /**
         * Route 2: Split Payment Processor
         */
        from("direct:split-payment-processor")
            .routeId("split-payment-processor")
            .description("Split Pattern: Split batch into individual payments")
            .log("🔀 Splitting batch payments: ${header.batchId}")
            
            .process(exchange -> {
                String batchId = exchange.getIn().getHeader("batchId", String.class);
                
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> payments = exchange.getIn().getBody(List.class);
                
                // Add batch context to each payment
                for (int i = 0; i < payments.size(); i++) {
                    Map<String, Object> payment = payments.get(i);
                    payment.put("batchId", batchId);
                    payment.put("paymentIndex", i + 1);
                    payment.put("totalInBatch", payments.size());
                    payment.put("splitTimestamp", LocalDateTime.now());
                    
                    // Generate individual payment ID if not exists
                    if (!payment.containsKey("paymentId")) {
                        payment.put("paymentId", UUID.randomUUID().toString());
                    }
                }
                
                log.info("🔀 Split batch {} into {} individual payments", batchId, payments.size());
            })
            
            // Split the list into individual payments
            .split(body())
                .parallelProcessing()
                .streaming()
                .to("direct:individual-payment-processor")
            .end()
            
            // Aggregate results after all payments are processed
            .to("direct:aggregate-payment-results")
            .log("✅ Batch payment splitting completed");

        /**
         * Route 3: Individual Payment Processor
         */
        from("direct:individual-payment-processor")
            .routeId("individual-payment-processor")
            .description("Split Pattern: Process individual payments")
            .log("💳 Processing individual payment: ${body[paymentId]}")
            
            .process(exchange -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> payment = exchange.getIn().getBody(Map.class);
                String paymentId = (String) payment.get("paymentId");
                String batchId = (String) payment.get("batchId");
                Integer paymentIndex = (Integer) payment.get("paymentIndex");
                
                // Set headers for downstream processing
                exchange.getIn().setHeader("paymentId", paymentId);
                exchange.getIn().setHeader("batchId", batchId);
                exchange.getIn().setHeader("paymentIndex", paymentIndex);
                exchange.getIn().setHeader("amount", payment.get("amount"));
                exchange.getIn().setHeader("currency", payment.get("currency"));
                exchange.getIn().setHeader("paymentMethod", payment.get("paymentMethod"));
                exchange.getIn().setHeader("paymentGateway", payment.get("paymentGateway"));
                exchange.getIn().setHeader("userId", payment.get("userId"));
                exchange.getIn().setHeader("orderId", payment.get("orderId"));
                
                payment.put("processingStartTime", LocalDateTime.now());
                payment.put("status", "PROCESSING");
                
                log.info("💳 Individual payment processing started: {} (batch: {}, index: {})", 
                        paymentId, batchId, paymentIndex);
            })
            
            .doTry()
                // Wire tap for audit trail
                .wireTap("direct:payment-wire-tap-entry")
                
                // Validate payment
                .to("direct:validate-individual-payment")
                
                // Process payment with retry logic
                .to("direct:payment-retry-entry")
                
                .process(exchange -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payment = exchange.getIn().getBody(Map.class);
                    payment.put("status", "COMPLETED");
                    payment.put("processingEndTime", LocalDateTime.now());
                    payment.put("result", "SUCCESS");
                    
                    log.info("✅ Individual payment completed: {}", payment.get("paymentId"));
                })
                
            .endDoTry()
            .doCatch(Exception.class)
                .log(LoggingLevel.ERROR, "❌ Individual payment failed: ${exception.message}")
                .process(exchange -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payment = exchange.getIn().getBody(Map.class);
                    payment.put("status", "FAILED");
                    payment.put("processingEndTime", LocalDateTime.now());
                    payment.put("result", "FAILURE");
                    payment.put("errorMessage", exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class).getMessage());
                    
                    log.error("❌ Individual payment failed: {}", payment.get("paymentId"));
                })
            .end()
            
            .log("📋 Individual payment processing completed: ${body[paymentId]}");

        /**
         * Route 4: Aggregate Payment Results
         */
        from("direct:aggregate-payment-results")
            .routeId("aggregate-payment-results")
            .description("Split Pattern: Aggregate payment results")
            .log("📊 Aggregating payment results for batch: ${header.batchId}")
            
            .aggregate(header("batchId"), new GroupedExchangeAggregationStrategy())
                .completionTimeout(60000) // 60 seconds timeout
                .completionSize(header("totalPayments"))
                .to("direct:process-aggregated-results")
            .end();

        /**
         * Route 5: Process Aggregated Results
         */
        from("direct:process-aggregated-results")
            .routeId("process-aggregated-results")
            .description("Split Pattern: Process aggregated results")
            .log("📈 Processing aggregated batch results: ${header.batchId}")
            
            .process(exchange -> {
                String batchId = exchange.getIn().getHeader("batchId", String.class);
                @SuppressWarnings("unchecked")
                List<Exchange> groupedExchanges = exchange.getIn().getBody(List.class);
                
                int totalPayments = groupedExchanges.size();
                int successfulPayments = 0;
                int failedPayments = 0;
                double totalAmount = 0.0;
                double successfulAmount = 0.0;
                
                Map<String, Object> batchSummary = new HashMap<>();
                batchSummary.put("batchId", batchId);
                batchSummary.put("totalPayments", totalPayments);
                batchSummary.put("batchEndTime", LocalDateTime.now());
                
                // Analyze individual payment results
                for (Exchange ex : groupedExchanges) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payment = ex.getIn().getBody(Map.class);
                    String result = (String) payment.get("result");
                    Double amount = (Double) payment.get("amount");
                    
                    totalAmount += amount != null ? amount : 0.0;
                    
                    if ("SUCCESS".equals(result)) {
                        successfulPayments++;
                        successfulAmount += amount != null ? amount : 0.0;
                    } else {
                        failedPayments++;
                    }
                }
                
                batchSummary.put("successfulPayments", successfulPayments);
                batchSummary.put("failedPayments", failedPayments);
                batchSummary.put("successRate", (double) successfulPayments / totalPayments * 100);
                batchSummary.put("totalAmount", totalAmount);
                batchSummary.put("successfulAmount", successfulAmount);
                batchSummary.put("failedAmount", totalAmount - successfulAmount);
                
                // Determine overall batch status
                String batchStatus;
                if (successfulPayments == totalPayments) {
                    batchStatus = "COMPLETED_SUCCESS";
                } else if (successfulPayments == 0) {
                    batchStatus = "COMPLETED_FAILURE";
                } else {
                    batchStatus = "COMPLETED_PARTIAL";
                }
                batchSummary.put("batchStatus", batchStatus);
                
                exchange.getIn().setBody(batchSummary);
                exchange.getIn().setHeader("batchStatus", batchStatus);
                
                log.info("📈 Batch aggregation completed: {} - {}/{} successful ({}%)", 
                        batchId, successfulPayments, totalPayments, 
                        String.format("%.1f", (double) successfulPayments / totalPayments * 100));
            })
            
            .to("mock:batch-results")
            .log("✅ Aggregated results processing completed");

        // Helper validation routes
        from("direct:validate-individual-payment")
            .routeId("validate-individual-payment")
            .description("Split Pattern: Validate individual payment")
            .log("✅ Validating individual payment: ${header.paymentId}")
            
            .process(exchange -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> payment = exchange.getIn().getBody(Map.class);
                
                // Basic validation
                if (payment.get("amount") == null || (Double) payment.get("amount") <= 0) {
                    throw new IllegalArgumentException("Invalid payment amount");
                }
                
                if (payment.get("paymentMethod") == null) {
                    throw new IllegalArgumentException("Payment method is required");
                }
                
                if (payment.get("paymentGateway") == null) {
                    throw new IllegalArgumentException("Payment gateway is required");
                }
                
                log.info("✅ Payment validation passed: {}", payment.get("paymentId"));
            })
            
            .log("✅ Individual payment validation completed");

        /**
         * Dead Letter Channel
         */
        from("direct:split-dead-letter")
            .routeId("split-dead-letter")
            .description("Split Pattern: Dead letter channel")
            .log(LoggingLevel.ERROR, "💀 Split pattern processing failed: ${exception.message}")
            .process(exchange -> {
                String failureId = UUID.randomUUID().toString();
                exchange.getIn().setHeader("failureId", failureId);
                exchange.getIn().setHeader("failureTimestamp", LocalDateTime.now());
                log.error("💀 Split failure logged: {}", failureId);
            })
            .log("💾 Split failure logged for analysis");
    }
} 