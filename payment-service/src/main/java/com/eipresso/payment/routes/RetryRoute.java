package com.eipresso.payment.routes;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Retry Pattern Implementation for Payment Service
 * 
 * EIP Pattern: Retry Pattern
 * Purpose: Resilient payment gateway integration with exponential backoff
 * Clustering: Active-Passive compatible (retry state management)
 * 
 * Routes:
 * 1. payment-retry-entry: Main retry entry point
 * 2. gateway-retry-processor: Gateway-specific retry logic
 * 3. exponential-backoff-processor: Exponential backoff calculation
 * 4. retry-exhausted-processor: Handle retry exhaustion
 * 5. circuit-breaker-integration: Circuit breaker coordination
 */
@Component
public class RetryRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        
        // Global error handling for Retry pattern
        errorHandler(deadLetterChannel("direct:retry-dead-letter")
            .maximumRedeliveries(5)
            .redeliveryDelay(2000)
            .retryAttemptedLogLevel(LoggingLevel.WARN)
            .useExponentialBackOff()
            .backOffMultiplier(2.0)
            .maximumRedeliveryDelay(30000));

        /**
         * Route 1: Payment Retry Entry Point
         */
        from("direct:payment-retry-entry")
            .routeId("payment-retry-entry")
            .description("Retry Pattern: Main retry entry point")
            .log("🔄 Initiating payment retry: ${header.paymentId}")
            
            .process(exchange -> {
                String retryId = UUID.randomUUID().toString();
                String paymentId = exchange.getIn().getHeader("paymentId", String.class);
                Integer currentAttempt = exchange.getIn().getHeader("retryAttempt", Integer.class);
                if (currentAttempt == null) currentAttempt = 1;
                
                exchange.getIn().setHeader("retryId", retryId);
                exchange.getIn().setHeader("retryAttempt", currentAttempt);
                exchange.getIn().setHeader("retryStartTime", LocalDateTime.now());
                exchange.getIn().setHeader("maxRetries", 3);
                
                log.info("🔄 Payment retry initiated: {} for payment {} (attempt {}/3)", 
                        retryId, paymentId, currentAttempt);
            })
            
            .choice()
                .when(simple("${header.retryAttempt} <= ${header.maxRetries}"))
                    .to("direct:gateway-retry-processor")
                .otherwise()
                    .to("direct:retry-exhausted-processor")
            .end();

        /**
         * Route 2: Gateway Retry Processor
         */
        from("direct:gateway-retry-processor")
            .routeId("gateway-retry-processor")
            .description("Retry Pattern: Gateway-specific retry logic")
            .log("🚀 Processing gateway retry attempt ${header.retryAttempt}: ${header.paymentId}")
            
            .process(exchange -> {
                String gateway = exchange.getIn().getHeader("paymentGateway", String.class);
                Integer attempt = exchange.getIn().getHeader("retryAttempt", Integer.class);
                
                // Calculate retry delay based on gateway and attempt
                long retryDelay = calculateRetryDelay(gateway, attempt);
                exchange.getIn().setHeader("retryDelay", retryDelay);
                
                // Set gateway-specific timeout
                int timeout = getGatewayTimeout(gateway);
                exchange.getIn().setHeader("gatewayTimeout", timeout);
                
                log.info("🚀 Gateway {} retry attempt {}, delay: {}ms, timeout: {}s", 
                        gateway, attempt, retryDelay, timeout);
            })
            
            .delay(simple("${header.retryDelay}"))
            
            .doTry()
                .to("direct:exponential-backoff-processor")
                .to("direct:process-payment-gateway-call")
                .log("✅ Payment gateway retry successful: ${header.paymentId}")
                
            .doCatch(Exception.class)
                .log(LoggingLevel.WARN, "❌ Payment gateway retry failed: ${exception.message}")
                .process(exchange -> {
                    Integer attempt = exchange.getIn().getHeader("retryAttempt", Integer.class);
                    exchange.getIn().setHeader("retryAttempt", attempt + 1);
                    
                    String reason = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class).getMessage();
                    exchange.getIn().setHeader("lastRetryFailureReason", reason);
                })
                .choice()
                    .when(simple("${header.retryAttempt} <= ${header.maxRetries}"))
                        .to("direct:payment-retry-entry")
                    .otherwise()
                        .to("direct:retry-exhausted-processor")
                .end()
            .end();

        /**
         * Route 3: Exponential Backoff Processor
         */
        from("direct:exponential-backoff-processor")
            .routeId("exponential-backoff-processor")
            .description("Retry Pattern: Exponential backoff calculation")
            .log("⏰ Calculating exponential backoff: ${header.paymentId}")
            
            .process(exchange -> {
                String gateway = exchange.getIn().getHeader("paymentGateway", String.class);
                Integer attempt = exchange.getIn().getHeader("retryAttempt", Integer.class);
                
                // Exponential backoff calculation
                long baseDelay = getGatewayBaseDelay(gateway);
                double multiplier = getGatewayBackoffMultiplier(gateway);
                long maxDelay = getGatewayMaxDelay(gateway);
                
                long calculatedDelay = (long) (baseDelay * Math.pow(multiplier, attempt - 1));
                long finalDelay = Math.min(calculatedDelay, maxDelay);
                
                // Add jitter to prevent thundering herd
                long jitter = (long) (finalDelay * 0.1 * Math.random());
                finalDelay += jitter;
                
                exchange.getIn().setHeader("exponentialBackoffDelay", finalDelay);
                exchange.getIn().setHeader("backoffMultiplier", multiplier);
                exchange.getIn().setHeader("jitterApplied", jitter);
                
                Map<String, Object> backoffMetrics = new HashMap<>();
                backoffMetrics.put("gateway", gateway);
                backoffMetrics.put("attempt", attempt);
                backoffMetrics.put("baseDelay", baseDelay);
                backoffMetrics.put("calculatedDelay", calculatedDelay);
                backoffMetrics.put("finalDelay", finalDelay);
                backoffMetrics.put("jitter", jitter);
                backoffMetrics.put("timestamp", LocalDateTime.now());
                
                exchange.getIn().setBody(backoffMetrics);
                
                log.info("⏰ Exponential backoff calculated: {}ms for gateway {} (attempt {})", 
                        finalDelay, gateway, attempt);
            })
            
            .to("mock:backoff-metrics")
            .log("✅ Exponential backoff processing completed");

        /**
         * Route 4: Process Payment Gateway Call
         */
        from("direct:process-payment-gateway-call")
            .routeId("process-payment-gateway-call")
            .description("Retry Pattern: Actual payment gateway call")
            .log("🏦 Processing payment gateway call: ${header.paymentId}")
            
            .process(exchange -> {
                String gateway = exchange.getIn().getHeader("paymentGateway", String.class);
                String paymentId = exchange.getIn().getHeader("paymentId", String.class);
                Integer timeout = exchange.getIn().getHeader("gatewayTimeout", Integer.class);
                
                // Simulate gateway call with timeout
                exchange.getIn().setHeader("gatewayCallStart", System.currentTimeMillis());
                
                Map<String, Object> gatewayRequest = new HashMap<>();
                gatewayRequest.put("paymentId", paymentId);
                gatewayRequest.put("gateway", gateway);
                gatewayRequest.put("amount", exchange.getIn().getHeader("amount"));
                gatewayRequest.put("currency", exchange.getIn().getHeader("currency"));
                gatewayRequest.put("timeout", timeout);
                gatewayRequest.put("requestTime", LocalDateTime.now());
                
                exchange.getIn().setBody(gatewayRequest);
                
                log.info("🏦 Gateway call prepared for {}: payment {}", gateway, paymentId);
            })
            
            .choice()
                .when(simple("${header.paymentGateway} == 'STRIPE'"))
                    .to("direct:stripe-gateway-call")
                .when(simple("${header.paymentGateway} == 'PAYPAL'"))
                    .to("direct:paypal-gateway-call")
                .when(simple("${header.paymentGateway} == 'MOCK'"))
                    .to("direct:mock-gateway-call")
                .otherwise()
                    .to("direct:default-gateway-call")
            .end()
            
            .process(exchange -> {
                long startTime = exchange.getIn().getHeader("gatewayCallStart", Long.class);
                long duration = System.currentTimeMillis() - startTime;
                exchange.getIn().setHeader("gatewayCallDuration", duration);
                
                log.info("🏦 Gateway call completed in {}ms", duration);
            })
            
            .log("✅ Payment gateway call processing completed");

        /**
         * Route 5: Retry Exhausted Processor
         */
        from("direct:retry-exhausted-processor")
            .routeId("retry-exhausted-processor")
            .description("Retry Pattern: Handle retry exhaustion")
            .log(LoggingLevel.ERROR, "❌ Payment retry exhausted: ${header.paymentId}")
            
            .process(exchange -> {
                String paymentId = exchange.getIn().getHeader("paymentId", String.class);
                Integer finalAttempt = exchange.getIn().getHeader("retryAttempt", Integer.class);
                String lastFailure = exchange.getIn().getHeader("lastRetryFailureReason", String.class);
                
                Map<String, Object> exhaustionRecord = new HashMap<>();
                exhaustionRecord.put("paymentId", paymentId);
                exhaustionRecord.put("finalAttempt", finalAttempt);
                exhaustionRecord.put("exhaustionTime", LocalDateTime.now());
                exhaustionRecord.put("lastFailureReason", lastFailure);
                exhaustionRecord.put("status", "RETRY_EXHAUSTED");
                
                exchange.getIn().setBody(exhaustionRecord);
                exchange.getIn().setHeader("paymentStatus", "FAILED");
                exchange.getIn().setHeader("failureReason", "Retry attempts exhausted");
                
                log.error("❌ Payment retry exhausted for {}: {} attempts, last failure: {}", 
                         paymentId, finalAttempt, lastFailure);
            })
            
            .to("mock:retry-exhausted")
            .to("direct:circuit-breaker-integration")
            .log("💀 Retry exhaustion processing completed");

        /**
         * Route 6: Circuit Breaker Integration
         */
        from("direct:circuit-breaker-integration")
            .routeId("circuit-breaker-integration")
            .description("Retry Pattern: Circuit breaker coordination")
            .log("🔌 Processing circuit breaker integration: ${header.paymentId}")
            
            .process(exchange -> {
                String gateway = exchange.getIn().getHeader("paymentGateway", String.class);
                String paymentId = exchange.getIn().getHeader("paymentId", String.class);
                
                // Update circuit breaker state
                Map<String, Object> circuitBreakerEvent = new HashMap<>();
                circuitBreakerEvent.put("gateway", gateway);
                circuitBreakerEvent.put("paymentId", paymentId);
                circuitBreakerEvent.put("eventType", "RETRY_EXHAUSTED");
                circuitBreakerEvent.put("timestamp", LocalDateTime.now());
                circuitBreakerEvent.put("failureCount", exchange.getIn().getHeader("retryAttempt"));
                
                exchange.getIn().setBody(circuitBreakerEvent);
                
                log.info("🔌 Circuit breaker event created for gateway {}", gateway);
            })
            
            .to("mock:circuit-breaker-events")
            .log("✅ Circuit breaker integration completed");

        // Gateway-specific call routes
        from("direct:stripe-gateway-call")
            .routeId("stripe-gateway-call")
            .description("Stripe gateway call")
            .log("🔵 Processing Stripe gateway call")
            .to("mock:stripe-gateway")
            .log("✅ Stripe gateway call completed");

        from("direct:paypal-gateway-call")
            .routeId("paypal-gateway-call")
            .description("PayPal gateway call")
            .log("🟡 Processing PayPal gateway call")
            .to("mock:paypal-gateway")
            .log("✅ PayPal gateway call completed");

        from("direct:mock-gateway-call")
            .routeId("mock-gateway-call")
            .description("Mock gateway call")
            .log("⚫ Processing Mock gateway call")
            .process(exchange -> {
                // Simulate random failures for testing
                if (Math.random() < 0.3) { // 30% failure rate
                    throw new RuntimeException("Mock gateway failure for testing");
                }
            })
            .to("mock:mock-gateway")
            .log("✅ Mock gateway call completed");

        from("direct:default-gateway-call")
            .routeId("default-gateway-call")
            .description("Default gateway call")
            .log("⚪ Processing default gateway call")
            .to("mock:default-gateway")
            .log("✅ Default gateway call completed");

        /**
         * Dead Letter Channel
         */
        from("direct:retry-dead-letter")
            .routeId("retry-dead-letter")
            .description("Retry Pattern: Dead letter channel")
            .log(LoggingLevel.ERROR, "💀 Payment retry processing failed: ${exception.message}")
            .process(exchange -> {
                String failureId = UUID.randomUUID().toString();
                exchange.getIn().setHeader("failureId", failureId);
                exchange.getIn().setHeader("failureTimestamp", LocalDateTime.now());
                log.error("💀 Retry failure logged: {}", failureId);
            })
            .log("💾 Retry failure logged for analysis");
    }

    // Helper methods
    private long calculateRetryDelay(String gateway, int attempt) {
        return getGatewayBaseDelay(gateway) * (long) Math.pow(2, attempt - 1);
    }

    private long getGatewayBaseDelay(String gateway) {
        switch (gateway) {
            case "STRIPE": return 1000L; // 1 second
            case "PAYPAL": return 2000L; // 2 seconds
            case "SQUARE": return 1500L; // 1.5 seconds
            case "MOCK": return 500L;    // 0.5 seconds
            default: return 1000L;
        }
    }

    private double getGatewayBackoffMultiplier(String gateway) {
        switch (gateway) {
            case "STRIPE": return 2.0;
            case "PAYPAL": return 1.5;
            case "SQUARE": return 2.0;
            case "MOCK": return 1.2;
            default: return 2.0;
        }
    }

    private long getGatewayMaxDelay(String gateway) {
        switch (gateway) {
            case "STRIPE": return 30000L; // 30 seconds
            case "PAYPAL": return 60000L; // 60 seconds
            case "SQUARE": return 25000L; // 25 seconds
            case "MOCK": return 5000L;    // 5 seconds
            default: return 30000L;
        }
    }

    private int getGatewayTimeout(String gateway) {
        switch (gateway) {
            case "STRIPE": return 30;
            case "PAYPAL": return 45;
            case "SQUARE": return 25;
            case "MOCK": return 5;
            default: return 30;
        }
    }
} 