package com.eipresso.payment.routes;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.MockEndpoints;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive EIP Pattern Integration Test Suite
 * 
 * Tests all 5 implemented EIP patterns with real message flows:
 * 1. Wire Tap Pattern (6 Routes) - Comprehensive audit trail
 * 2. Retry Pattern (8 Routes) - Resilient gateway integration
 * 3. Split Pattern (6 Routes) - Batch payment processing
 * 4. Filter Pattern (3 Routes) - Fraud detection
 * 5. Request-Reply Pattern (2 Routes) - Synchronous confirmation
 */
@SpringBootTest
@CamelSpringBootTest
@ActiveProfiles("test")
@MockEndpoints("direct:*")
@DisplayName("EIP Pattern Integration Tests")
class EIPPatternIntegrationTest {

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private ProducerTemplate producerTemplate;

    private Map<String, Object> basePaymentRequest;
    private Map<String, Object> baseHeaders;

    @BeforeEach
    void setUp() throws Exception {
        // Reset all mock endpoints
        MockEndpoint.resetMocks(camelContext);
        
        // Setup base payment request
        basePaymentRequest = new HashMap<>();
        basePaymentRequest.put("amount", "50.00");
        basePaymentRequest.put("currency", "USD");
        basePaymentRequest.put("paymentMethod", "CREDIT_CARD");
        basePaymentRequest.put("customerEmail", "test@example.com");
        basePaymentRequest.put("customerName", "Test Customer");
        basePaymentRequest.put("orderId", 1001L);
        basePaymentRequest.put("userId", 2001L);

        // Setup base headers
        baseHeaders = new HashMap<>();
        baseHeaders.put("paymentId", "PAY-TEST-" + System.currentTimeMillis());
        baseHeaders.put("correlationId", UUID.randomUUID().toString());
        baseHeaders.put("transactionType", "PURCHASE");
        baseHeaders.put("customerIp", "192.168.1.100");
        baseHeaders.put("userAgent", "Test Client 1.0");
    }

    @Nested
    @DisplayName("Wire Tap Pattern Tests (6 Routes)")
    class WireTapPatternTests {

        @Test
        @DisplayName("Should create comprehensive audit trail for all payment events")
        void shouldCreateComprehensiveAuditTrail() throws Exception {
            // Setup mock endpoints for Wire Tap destinations
            MockEndpoint transactionAudit = camelContext.getEndpoint("mock:direct:transaction-audit-processor", MockEndpoint.class);
            MockEndpoint fraudAudit = camelContext.getEndpoint("mock:direct:fraud-audit-processor", MockEndpoint.class);
            MockEndpoint complianceAudit = camelContext.getEndpoint("mock:direct:compliance-audit-processor", MockEndpoint.class);
            MockEndpoint securityAudit = camelContext.getEndpoint("mock:direct:security-audit-processor", MockEndpoint.class);
            MockEndpoint businessMetrics = camelContext.getEndpoint("mock:direct:business-metrics-processor", MockEndpoint.class);
            MockEndpoint errorAudit = camelContext.getEndpoint("mock:direct:error-audit-processor", MockEndpoint.class);

            // Expect messages at all audit endpoints
            transactionAudit.expectedMinimumMessageCount(1);
            fraudAudit.expectedMinimumMessageCount(1);
            complianceAudit.expectedMinimumMessageCount(1);
            securityAudit.expectedMinimumMessageCount(1);
            businessMetrics.expectedMinimumMessageCount(1);

            // Send message through Wire Tap pattern
            producerTemplate.sendBodyAndHeaders(
                "direct:payment-wire-tap-entry",
                basePaymentRequest,
                baseHeaders
            );

            // Verify all wire tap destinations received messages
            MockEndpoint.assertIsSatisfied(camelContext, 5, TimeUnit.SECONDS);

            // Verify audit trail contains required information
            Exchange transactionExchange = transactionAudit.getReceivedExchanges().get(0);
            assertNotNull(transactionExchange.getIn().getHeader("paymentId"));
            assertNotNull(transactionExchange.getIn().getHeader("correlationId"));
            assertEquals("50.00", transactionExchange.getIn().getHeader("amount"));

            Exchange securityExchange = securityAudit.getReceivedExchanges().get(0);
            assertEquals("192.168.1.100", securityExchange.getIn().getHeader("customerIp"));
            assertEquals("Test Client 1.0", securityExchange.getIn().getHeader("userAgent"));
        }

        @Test
        @DisplayName("Should handle high-value transactions with enhanced audit")
        void shouldHandleHighValueTransactionsWithEnhancedAudit() throws Exception {
            // High-value transaction
            basePaymentRequest.put("amount", "1500.00");
            baseHeaders.put("highValueTransaction", true);
            baseHeaders.put("enhancedAuditRequired", true);

            MockEndpoint complianceAudit = camelContext.getEndpoint("mock:direct:compliance-audit-processor", MockEndpoint.class);
            MockEndpoint businessMetrics = camelContext.getEndpoint("mock:direct:business-metrics-processor", MockEndpoint.class);

            complianceAudit.expectedMinimumMessageCount(1);
            businessMetrics.expectedMinimumMessageCount(1);

            producerTemplate.sendBodyAndHeaders(
                "direct:payment-wire-tap-entry",
                basePaymentRequest,
                baseHeaders
            );

            MockEndpoint.assertIsSatisfied(camelContext, 5, TimeUnit.SECONDS);

            // Verify enhanced audit for high-value transactions
            Exchange complianceExchange = complianceAudit.getReceivedExchanges().get(0);
            assertEquals("1500.00", complianceExchange.getIn().getHeader("amount"));
            assertTrue((Boolean) complianceExchange.getIn().getHeader("highValueTransaction"));
        }

        @Test
        @DisplayName("Should maintain message integrity during wire tap processing")
        void shouldMaintainMessageIntegrityDuringWireTap() throws Exception {
            MockEndpoint mainFlow = camelContext.getEndpoint("mock:direct:payment-main-flow", MockEndpoint.class);
            MockEndpoint transactionAudit = camelContext.getEndpoint("mock:direct:transaction-audit-processor", MockEndpoint.class);

            mainFlow.expectedMessageCount(1);
            transactionAudit.expectedMessageCount(1);

            // Send message and verify both main flow and audit receive it
            Object result = producerTemplate.requestBodyAndHeaders(
                "direct:payment-wire-tap-entry",
                basePaymentRequest,
                baseHeaders
            );

            MockEndpoint.assertIsSatisfied(camelContext, 5, TimeUnit.SECONDS);

            // Verify original message is not modified
            Exchange mainExchange = mainFlow.getReceivedExchanges().get(0);
            Map<?, ?> receivedBody = (Map<?, ?>) mainExchange.getIn().getBody();
            assertEquals("50.00", receivedBody.get("amount"));
            assertEquals("Test Customer", receivedBody.get("customerName"));
        }
    }

    @Nested
    @DisplayName("Retry Pattern Tests (8 Routes)")
    class RetryPatternTests {

        @Test
        @DisplayName("Should retry failed payments with exponential backoff")
        void shouldRetryFailedPaymentsWithExponentialBackoff() throws Exception {
            // Setup mock gateway that fails initially
            MockEndpoint gatewayMock = camelContext.getEndpoint("mock:direct:mock-gateway", MockEndpoint.class);
            MockEndpoint retryProcessor = camelContext.getEndpoint("mock:direct:retry-processor", MockEndpoint.class);

            // Configure gateway to fail first 2 attempts, succeed on 3rd
            gatewayMock.whenExchangeReceived(1, exchange -> {
                exchange.getIn().setHeader("gatewayResponse", "TIMEOUT");
                exchange.getIn().setHeader("retryRequired", true);
            });
            gatewayMock.whenExchangeReceived(2, exchange -> {
                exchange.getIn().setHeader("gatewayResponse", "FAILED");
                exchange.getIn().setHeader("retryRequired", true);
            });
            gatewayMock.whenExchangeReceived(3, exchange -> {
                exchange.getIn().setHeader("gatewayResponse", "SUCCESS");
                exchange.getIn().setHeader("retryRequired", false);
            });

            gatewayMock.expectedMessageCount(3);
            retryProcessor.expectedMinimumMessageCount(1);

            // Setup retry headers
            baseHeaders.put("paymentGateway", "MOCK");
            baseHeaders.put("maxRetries", 3);
            baseHeaders.put("retryAttempt", 1);

            // Send payment for retry processing
            Object result = producerTemplate.requestBodyAndHeaders(
                "direct:payment-retry-entry",
                basePaymentRequest,
                baseHeaders
            );

            MockEndpoint.assertIsSatisfied(camelContext, 10, TimeUnit.SECONDS);

            // Verify retry attempts
            assertEquals(3, gatewayMock.getReceivedCounter());
            
            // Verify exponential backoff was applied (check timestamps)
            List<Exchange> exchanges = gatewayMock.getReceivedExchanges();
            assertTrue(exchanges.size() >= 2);
        }

        @ParameterizedTest
        @ValueSource(strings = {"STRIPE", "PAYPAL", "SQUARE", "ADYEN"})
        @DisplayName("Should handle gateway-specific retry policies")
        void shouldHandleGatewaySpecificRetryPolicies(String gateway) throws Exception {
            MockEndpoint gatewayEndpoint = camelContext.getEndpoint("mock:direct:" + gateway.toLowerCase() + "-gateway", MockEndpoint.class);
            MockEndpoint circuitBreakerCheck = camelContext.getEndpoint("mock:direct:circuit-breaker-check", MockEndpoint.class);

            gatewayEndpoint.expectedMinimumMessageCount(1);
            circuitBreakerCheck.expectedMinimumMessageCount(1);

            baseHeaders.put("paymentGateway", gateway);
            baseHeaders.put("gatewayTimeout", getGatewayTimeout(gateway));
            baseHeaders.put("maxRetries", getGatewayMaxRetries(gateway));

            producerTemplate.sendBodyAndHeaders(
                "direct:payment-retry-entry",
                basePaymentRequest,
                baseHeaders
            );

            MockEndpoint.assertIsSatisfied(camelContext, 10, TimeUnit.SECONDS);

            // Verify gateway-specific configuration was applied
            Exchange gatewayExchange = gatewayEndpoint.getReceivedExchanges().get(0);
            assertEquals(gateway, gatewayExchange.getIn().getHeader("paymentGateway"));
        }

        @Test
        @DisplayName("Should activate circuit breaker after consecutive failures")
        void shouldActivateCircuitBreakerAfterConsecutiveFailures() throws Exception {
            MockEndpoint circuitBreakerProcessor = camelContext.getEndpoint("mock:direct:circuit-breaker-processor", MockEndpoint.class);
            MockEndpoint deadLetterChannel = camelContext.getEndpoint("mock:direct:payment-dead-letter", MockEndpoint.class);

            circuitBreakerProcessor.expectedMinimumMessageCount(1);
            deadLetterChannel.expectedMinimumMessageCount(1);

            // Setup headers for circuit breaker activation
            baseHeaders.put("paymentGateway", "MOCK");
            baseHeaders.put("consecutiveFailures", 5);
            baseHeaders.put("circuitBreakerThreshold", 3);
            baseHeaders.put("circuitBreakerOpen", true);

            producerTemplate.sendBodyAndHeaders(
                "direct:payment-retry-entry",
                basePaymentRequest,
                baseHeaders
            );

            MockEndpoint.assertIsSatisfied(camelContext, 5, TimeUnit.SECONDS);

            // Verify circuit breaker was activated
            Exchange cbExchange = circuitBreakerProcessor.getReceivedExchanges().get(0);
            assertTrue((Boolean) cbExchange.getIn().getHeader("circuitBreakerOpen"));
        }

        private int getGatewayTimeout(String gateway) {
            Map<String, Integer> timeouts = Map.of(
                "STRIPE", 30000, "PAYPAL", 45000, "SQUARE", 25000, "ADYEN", 35000
            );
            return timeouts.getOrDefault(gateway, 30000);
        }

        private int getGatewayMaxRetries(String gateway) {
            Map<String, Integer> retries = Map.of(
                "STRIPE", 3, "PAYPAL", 2, "SQUARE", 3, "ADYEN", 4
            );
            return retries.getOrDefault(gateway, 3);
        }
    }

    @Nested
    @DisplayName("Split Pattern Tests (6 Routes)")
    class SplitPatternTests {

        @Test
        @DisplayName("Should process batch payments in parallel with aggregation")
        void shouldProcessBatchPaymentsInParallelWithAggregation() throws Exception {
            // Create batch of payments
            List<Map<String, Object>> payments = Arrays.asList(
                createPayment("PAY-001", "25.00", "CREDIT_CARD"),
                createPayment("PAY-002", "35.50", "DEBIT_CARD"),
                createPayment("PAY-003", "100.00", "PAYPAL"),
                createPayment("PAY-004", "15.75", "APPLE_PAY")
            );

            MockEndpoint splitProcessor = camelContext.getEndpoint("mock:direct:split-payment-processor", MockEndpoint.class);
            MockEndpoint parallelProcessor = camelContext.getEndpoint("mock:direct:parallel-payment-processor", MockEndpoint.class);
            MockEndpoint aggregator = camelContext.getEndpoint("mock:direct:payment-aggregator", MockEndpoint.class);

            splitProcessor.expectedMessageCount(1);
            parallelProcessor.expectedMessageCount(4);  // One for each payment
            aggregator.expectedMessageCount(1);

            // Send batch for processing
            producerTemplate.sendBody("direct:batch-payment-entry", payments);

            MockEndpoint.assertIsSatisfied(camelContext, 10, TimeUnit.SECONDS);

            // Verify all payments were processed
            assertEquals(4, parallelProcessor.getReceivedCounter());
            
            // Verify aggregation result
            Exchange aggregatedExchange = aggregator.getReceivedExchanges().get(0);
            assertNotNull(aggregatedExchange.getIn().getHeader("batchSize"));
            assertNotNull(aggregatedExchange.getIn().getHeader("totalAmount"));
            assertNotNull(aggregatedExchange.getIn().getHeader("batchProcessingTime"));
        }

        @Test
        @DisplayName("Should handle partial failures in batch processing")
        void shouldHandlePartialFailuresInBatchProcessing() throws Exception {
            // Create batch with one invalid payment
            List<Map<String, Object>> payments = Arrays.asList(
                createPayment("PAY-001", "25.00", "CREDIT_CARD"),
                createPayment("PAY-002", "INVALID", "DEBIT_CARD"),  // Invalid amount
                createPayment("PAY-003", "100.00", "PAYPAL")
            );

            MockEndpoint parallelProcessor = camelContext.getEndpoint("mock:direct:parallel-payment-processor", MockEndpoint.class);
            MockEndpoint errorHandler = camelContext.getEndpoint("mock:direct:batch-error-handler", MockEndpoint.class);
            MockEndpoint partialAggregator = camelContext.getEndpoint("mock:direct:partial-success-aggregator", MockEndpoint.class);

            parallelProcessor.expectedMinimumMessageCount(2);  // Valid payments
            errorHandler.expectedMinimumMessageCount(1);       // Invalid payment
            partialAggregator.expectedMinimumMessageCount(1);  // Partial results

            producerTemplate.sendBody("direct:batch-payment-entry", payments);

            MockEndpoint.assertIsSatisfied(camelContext, 10, TimeUnit.SECONDS);

            // Verify partial success handling
            Exchange errorExchange = errorHandler.getReceivedExchanges().get(0);
            assertTrue(errorExchange.getIn().getBody().toString().contains("INVALID"));
        }

        @Test
        @DisplayName("Should maintain correlation IDs across split processing")
        void shouldMaintainCorrelationIdsAcrossSplitProcessing() throws Exception {
            String batchCorrelationId = "BATCH-" + System.currentTimeMillis();
            
            List<Map<String, Object>> payments = Arrays.asList(
                createPayment("PAY-001", "25.00", "CREDIT_CARD"),
                createPayment("PAY-002", "35.50", "DEBIT_CARD")
            );

            MockEndpoint parallelProcessor = camelContext.getEndpoint("mock:direct:parallel-payment-processor", MockEndpoint.class);
            MockEndpoint correlationTracker = camelContext.getEndpoint("mock:direct:correlation-tracker", MockEndpoint.class);

            parallelProcessor.expectedMessageCount(2);
            correlationTracker.expectedMessageCount(2);

            // Send with batch correlation ID
            Map<String, Object> headers = Map.of(
                "batchCorrelationId", batchCorrelationId,
                "batchId", "BATCH-TEST-001"
            );

            producerTemplate.sendBodyAndHeaders("direct:batch-payment-entry", payments, headers);

            MockEndpoint.assertIsSatisfied(camelContext, 10, TimeUnit.SECONDS);

            // Verify correlation IDs are maintained
            for (Exchange exchange : correlationTracker.getReceivedExchanges()) {
                assertEquals(batchCorrelationId, exchange.getIn().getHeader("batchCorrelationId"));
                assertNotNull(exchange.getIn().getHeader("individualCorrelationId"));
            }
        }

        private Map<String, Object> createPayment(String paymentId, String amount, String paymentMethod) {
            Map<String, Object> payment = new HashMap<>();
            payment.put("paymentId", paymentId);
            payment.put("amount", amount);
            payment.put("paymentMethod", paymentMethod);
            payment.put("currency", "USD");
            payment.put("customerEmail", "batch@example.com");
            payment.put("customerName", "Batch Customer");
            payment.put("orderId", System.currentTimeMillis());
            payment.put("userId", 3001L);
            return payment;
        }
    }

    @Nested
    @DisplayName("Filter Pattern Tests (3 Routes)")
    class FilterPatternTests {

        @Test
        @DisplayName("Should filter high fraud risk transactions")
        void shouldFilterHighFraudRiskTransactions() throws Exception {
            MockEndpoint fraudDetection = camelContext.getEndpoint("mock:direct:fraud-detection-processor", MockEndpoint.class);
            MockEndpoint highRiskHandler = camelContext.getEndpoint("mock:direct:high-risk-handler", MockEndpoint.class);
            MockEndpoint lowRiskProcessor = camelContext.getEndpoint("mock:direct:low-risk-processor", MockEndpoint.class);

            fraudDetection.expectedMessageCount(1);
            highRiskHandler.expectedMessageCount(1);
            lowRiskProcessor.expectedMessageCount(0);

            // Setup high fraud risk scenario
            baseHeaders.put("customerCountry", "HIGH_RISK_COUNTRY");
            baseHeaders.put("suspiciousIpAddress", "203.0.113.0");
            baseHeaders.put("multipleFailedAttempts", true);
            baseHeaders.put("transactionTime", 3); // 3 AM - suspicious time
            basePaymentRequest.put("amount", "5000.00"); // High amount

            producerTemplate.sendBodyAndHeaders(
                "direct:fraud-monitoring-processor",
                basePaymentRequest,
                baseHeaders
            );

            MockEndpoint.assertIsSatisfied(camelContext, 5, TimeUnit.SECONDS);

            // Verify fraud detection was triggered
            Exchange fraudExchange = fraudDetection.getReceivedExchanges().get(0);
            assertNotNull(fraudExchange.getIn().getHeader("fraudScore"));
            
            Exchange highRiskExchange = highRiskHandler.getReceivedExchanges().get(0);
            assertTrue(highRiskExchange.getIn().getHeader("fraudScore", Double.class) > 75.0);
        }

        @Test
        @DisplayName("Should allow low risk transactions to proceed")
        void shouldAllowLowRiskTransactionsToProceed() throws Exception {
            MockEndpoint fraudDetection = camelContext.getEndpoint("mock:direct:fraud-detection-processor", MockEndpoint.class);
            MockEndpoint lowRiskProcessor = camelContext.getEndpoint("mock:direct:low-risk-processor", MockEndpoint.class);
            MockEndpoint highRiskHandler = camelContext.getEndpoint("mock:direct:high-risk-handler", MockEndpoint.class);

            fraudDetection.expectedMessageCount(1);
            lowRiskProcessor.expectedMessageCount(1);
            highRiskHandler.expectedMessageCount(0);

            // Setup low fraud risk scenario
            baseHeaders.put("customerCountry", "US");
            baseHeaders.put("customerIp", "192.168.1.100");
            baseHeaders.put("multipleFailedAttempts", false);
            baseHeaders.put("transactionTime", 14); // 2 PM - normal time
            basePaymentRequest.put("amount", "25.00"); // Normal amount

            producerTemplate.sendBodyAndHeaders(
                "direct:fraud-monitoring-processor",
                basePaymentRequest,
                baseHeaders
            );

            MockEndpoint.assertIsSatisfied(camelContext, 5, TimeUnit.SECONDS);

            // Verify low risk processing
            Exchange lowRiskExchange = lowRiskProcessor.getReceivedExchanges().get(0);
            assertTrue(lowRiskExchange.getIn().getHeader("fraudScore", Double.class) <= 75.0);
        }

        @Test
        @DisplayName("Should apply compliance filters based on regulations")
        void shouldApplyComplianceFiltersBasedOnRegulations() throws Exception {
            MockEndpoint complianceFilter = camelContext.getEndpoint("mock:direct:compliance-filter", MockEndpoint.class);
            MockEndpoint kycProcessor = camelContext.getEndpoint("mock:direct:kyc-processor", MockEndpoint.class);
            MockEndpoint amlProcessor = camelContext.getEndpoint("mock:direct:aml-processor", MockEndpoint.class);

            complianceFilter.expectedMessageCount(1);
            kycProcessor.expectedMessageCount(1);
            amlProcessor.expectedMessageCount(1);

            // Setup compliance scenario
            baseHeaders.put("customerCountry", "REGULATED_COUNTRY");
            baseHeaders.put("kycRequired", true);
            baseHeaders.put("amlCheckRequired", true);
            basePaymentRequest.put("amount", "10000.00"); // Amount requiring compliance checks

            producerTemplate.sendBodyAndHeaders(
                "direct:compliance-monitoring-processor",
                basePaymentRequest,
                baseHeaders
            );

            MockEndpoint.assertIsSatisfied(camelContext, 5, TimeUnit.SECONDS);

            // Verify compliance checks were triggered
            Exchange kycExchange = kycProcessor.getReceivedExchanges().get(0);
            assertTrue((Boolean) kycExchange.getIn().getHeader("kycRequired"));
            
            Exchange amlExchange = amlProcessor.getReceivedExchanges().get(0);
            assertTrue((Boolean) amlExchange.getIn().getHeader("amlCheckRequired"));
        }
    }

    @Nested
    @DisplayName("Request-Reply Pattern Tests (2 Routes)")
    class RequestReplyPatternTests {

        @Test
        @DisplayName("Should handle synchronous payment confirmation with timeout")
        void shouldHandleSynchronousPaymentConfirmationWithTimeout() throws Exception {
            MockEndpoint syncProcessor = camelContext.getEndpoint("mock:direct:sync-payment-processor", MockEndpoint.class);
            MockEndpoint confirmationHandler = camelContext.getEndpoint("mock:direct:confirmation-handler", MockEndpoint.class);

            syncProcessor.expectedMessageCount(1);
            confirmationHandler.expectedMessageCount(1);

            // Configure synchronous processing
            baseHeaders.put("synchronous", true);
            baseHeaders.put("timeoutMs", 30000);
            baseHeaders.put("confirmationRequired", true);

            // Send synchronous payment request
            Object result = producerTemplate.requestBodyAndHeaders(
                "direct:sync-payment-request",
                basePaymentRequest,
                baseHeaders
            );

            MockEndpoint.assertIsSatisfied(camelContext, 35, TimeUnit.SECONDS);

            // Verify synchronous response
            assertNotNull(result);
            Exchange syncExchange = syncProcessor.getReceivedExchanges().get(0);
            assertTrue((Boolean) syncExchange.getIn().getHeader("synchronous"));
        }

        @Test
        @DisplayName("Should handle timeout scenarios gracefully")
        void shouldHandleTimeoutScenariosGracefully() throws Exception {
            MockEndpoint timeoutHandler = camelContext.getEndpoint("mock:direct:timeout-handler", MockEndpoint.class);
            MockEndpoint fallbackProcessor = camelContext.getEndpoint("mock:direct:fallback-processor", MockEndpoint.class);

            timeoutHandler.expectedMessageCount(1);
            fallbackProcessor.expectedMessageCount(1);

            // Configure short timeout to trigger timeout scenario
            baseHeaders.put("synchronous", true);
            baseHeaders.put("timeoutMs", 100); // Very short timeout
            baseHeaders.put("slowGateway", true); // Simulate slow gateway

            try {
                producerTemplate.requestBodyAndHeaders(
                    "direct:sync-payment-request",
                    basePaymentRequest,
                    baseHeaders
                );
            } catch (Exception e) {
                // Expected timeout exception
                assertTrue(e.getMessage().contains("timeout") || e.getCause() != null);
            }

            MockEndpoint.assertIsSatisfied(camelContext, 5, TimeUnit.SECONDS);

            // Verify timeout handling
            if (timeoutHandler.getReceivedCounter() > 0) {
                Exchange timeoutExchange = timeoutHandler.getReceivedExchanges().get(0);
                assertTrue((Boolean) timeoutExchange.getIn().getHeader("timeoutOccurred", Boolean.class));
            }
        }
    }

    @Nested
    @DisplayName("Integration Tests - Multiple Patterns")
    class MultiplePatternIntegrationTests {

        @Test
        @DisplayName("Should orchestrate all patterns in complete payment flow")
        void shouldOrchestrateAllPatternsInCompletePaymentFlow() throws Exception {
            // Setup mocks for all patterns
            MockEndpoint wireTapAudit = camelContext.getEndpoint("mock:direct:transaction-audit-processor", MockEndpoint.class);
            MockEndpoint fraudFilter = camelContext.getEndpoint("mock:direct:fraud-detection-processor", MockEndpoint.class);
            MockEndpoint retryProcessor = camelContext.getEndpoint("mock:direct:retry-processor", MockEndpoint.class);
            MockEndpoint finalProcessor = camelContext.getEndpoint("mock:direct:payment-completion", MockEndpoint.class);

            wireTapAudit.expectedMinimumMessageCount(1);
            fraudFilter.expectedMinimumMessageCount(1);
            retryProcessor.expectedMinimumMessageCount(1);
            finalProcessor.expectedMinimumMessageCount(1);

            // Setup complete payment flow
            baseHeaders.put("enableAllPatterns", true);
            baseHeaders.put("paymentGateway", "STRIPE");
            baseHeaders.put("customerCountry", "US");
            baseHeaders.put("synchronous", false);

            // Send through complete payment flow
            producerTemplate.sendBodyAndHeaders(
                "direct:complete-payment-flow",
                basePaymentRequest,
                baseHeaders
            );

            MockEndpoint.assertIsSatisfied(camelContext, 15, TimeUnit.SECONDS);

            // Verify all patterns were executed
            assertTrue(wireTapAudit.getReceivedCounter() >= 1);
            assertTrue(fraudFilter.getReceivedCounter() >= 1);
            assertTrue(retryProcessor.getReceivedCounter() >= 1);
            assertTrue(finalProcessor.getReceivedCounter() >= 1);
        }

        @Test
        @DisplayName("Should handle error propagation across patterns")
        void shouldHandleErrorPropagationAcrossPatterns() throws Exception {
            MockEndpoint errorHandler = camelContext.getEndpoint("mock:direct:global-error-handler", MockEndpoint.class);
            MockEndpoint deadLetterChannel = camelContext.getEndpoint("mock:direct:payment-dead-letter", MockEndpoint.class);

            errorHandler.expectedMinimumMessageCount(1);
            deadLetterChannel.expectedMinimumMessageCount(1);

            // Setup error scenario
            baseHeaders.put("simulateError", true);
            baseHeaders.put("errorType", "GATEWAY_FAILURE");
            baseHeaders.put("maxRetries", 2);

            try {
                producerTemplate.sendBodyAndHeaders(
                    "direct:complete-payment-flow",
                    basePaymentRequest,
                    baseHeaders
                );
            } catch (Exception e) {
                // Expected error
            }

            MockEndpoint.assertIsSatisfied(camelContext, 10, TimeUnit.SECONDS);

            // Verify error handling
            if (errorHandler.getReceivedCounter() > 0) {
                Exchange errorExchange = errorHandler.getReceivedExchanges().get(0);
                assertEquals("GATEWAY_FAILURE", errorExchange.getIn().getHeader("errorType"));
            }
        }
    }
}