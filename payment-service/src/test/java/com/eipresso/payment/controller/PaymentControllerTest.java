package com.eipresso.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive Payment Controller REST API Test Suite
 * 
 * Tests all REST endpoints and their integration with EIP patterns:
 * - Wire Tap Pattern endpoints
 * - Retry Pattern endpoints  
 * - Split Pattern endpoints
 * - Filter Pattern endpoints
 * - Request-Reply Pattern endpoints
 * - Health and monitoring endpoints
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Payment Controller API Tests")
class PaymentControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProducerTemplate producerTemplate;

    @MockBean
    private CamelContext camelContext;

    private Map<String, Object> validPaymentRequest;
    private List<Map<String, Object>> validBatchRequest;

    @BeforeEach
    void setUp() {
        // Setup MockMvc
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        
        // Setup valid payment request
        validPaymentRequest = new HashMap<>();
        validPaymentRequest.put("amount", "50.00");
        validPaymentRequest.put("currency", "USD");
        validPaymentRequest.put("paymentMethod", "CREDIT_CARD");
        validPaymentRequest.put("paymentGateway", "STRIPE");
        validPaymentRequest.put("customerEmail", "test@example.com");
        validPaymentRequest.put("customerName", "Test Customer");
        validPaymentRequest.put("orderId", 1001L);
        validPaymentRequest.put("userId", 2001L);

        // Setup valid batch request
        validBatchRequest = Arrays.asList(
            createPaymentRequest("PAY-001", "25.00", "CREDIT_CARD"),
            createPaymentRequest("PAY-002", "35.50", "DEBIT_CARD"),
            createPaymentRequest("PAY-003", "100.00", "PAYPAL")
        );

        // Mock Camel Context
        when(camelContext.getRoutes()).thenReturn(Collections.emptyList());
        when(camelContext.getVersion()).thenReturn("4.0.0");
    }

    @Nested
    @DisplayName("Health and Monitoring Endpoints")
    class HealthAndMonitoringTests {

        @Test
        @DisplayName("Should return service health information")
        void shouldReturnServiceHealthInformation() throws Exception {
            when(camelContext.getRoutes()).thenReturn(Collections.emptyList());
            when(camelContext.getVersion()).thenReturn("4.0.0");

            MvcResult result = mockMvc.perform(get("/payments/health"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.service").value("Payment Service"))
                    .andExpect(jsonPath("$.status").value("UP"))
                    .andExpect(jsonPath("$.port").value(8084))
                    .andExpect(jsonPath("$.clustering").value("Active-Passive"))
                    .andExpect(jsonPath("$.patterns").isArray())
                    .andExpect(jsonPath("$.patterns[0]").value("Wire Tap"))
                    .andExpect(jsonPath("$.activeRoutes").value(0))
                    .andExpect(jsonPath("$.camelVersion").value("4.0.0"))
                    .andDo(print())
                    .andReturn();

            String responseContent = result.getResponse().getContentAsString();
            Map<?, ?> response = objectMapper.readValue(responseContent, Map.class);
            
            assertNotNull(response.get("timestamp"));
            assertTrue(response.get("patterns") instanceof List);
            assertEquals(5, ((List<?>) response.get("patterns")).size());
        }

        @Test
        @DisplayName("Should return EIP patterns information")
        void shouldReturnEIPPatternsInformation() throws Exception {
            when(camelContext.getRoutes()).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/payments/patterns"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.implemented").isArray())
                    .andExpect(jsonPath("$.implemented[0].pattern").value("Wire Tap"))
                    .andExpect(jsonPath("$.implemented[0].routes").value(6))
                    .andExpect(jsonPath("$.implemented[0].description").exists())
                    .andExpect(jsonPath("$.implemented[1].pattern").value("Retry"))
                    .andExpect(jsonPath("$.implemented[1].routes").value(8))
                    .andExpect(jsonPath("$.implemented[2].pattern").value("Split"))
                    .andExpect(jsonPath("$.implemented[3].pattern").value("Filter"))
                    .andExpect(jsonPath("$.implemented[4].pattern").value("Request-Reply"))
                    .andDo(print());
        }

        @Test
        @DisplayName("Should return service metrics")
        void shouldReturnServiceMetrics() throws Exception {
            when(camelContext.getRoutes()).thenReturn(Collections.emptyList());

            MvcResult result = mockMvc.perform(get("/payments/metrics"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.activeRoutes").exists())
                    .andExpect(jsonPath("$.totalExchanges").exists())
                    .andExpect(jsonPath("$.successfulExchanges").exists())
                    .andExpect(jsonPath("$.failedExchanges").exists())
                    .andExpect(jsonPath("$.averageProcessingTime").exists())
                    .andExpect(jsonPath("$.circuitBreakerStatus").exists())
                    .andExpect(jsonPath("$.eipPatterns").isArray())
                    .andDo(print())
                    .andReturn();

            String responseContent = result.getResponse().getContentAsString();
            Map<?, ?> response = objectMapper.readValue(responseContent, Map.class);
            
            assertNotNull(response.get("timestamp"));
            assertTrue(response.get("eipPatterns") instanceof List);
        }

        @Test
        @DisplayName("Should refresh configuration dynamically")
        void shouldRefreshConfigurationDynamically() throws Exception {
            mockMvc.perform(post("/payments/refresh-config"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message").value("Configuration refreshed successfully"))
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.configServer").value("Connected"))
                    .andDo(print());
        }
    }

    @Nested
    @DisplayName("Wire Tap Pattern Endpoint Tests")
    class WireTapPatternTests {

        @Test
        @DisplayName("Should process payment with comprehensive audit trail")
        void shouldProcessPaymentWithComprehensiveAuditTrail() throws Exception {
            // Mock successful processing
            when(producerTemplate.requestBodyAndHeaders(
                    eq("direct:payment-wire-tap-entry"),
                    any(),
                    any(Map.class)
            )).thenReturn("SUCCESS");

            MvcResult result = mockMvc.perform(post("/payments/process")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validPaymentRequest)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message").value("Payment processing initiated with comprehensive audit trail"))
                    .andExpect(jsonPath("$.status").value("PROCESSING"))
                    .andExpect(jsonPath("$.paymentId").exists())
                    .andExpect(jsonPath("$.auditTrail").exists())
                    .andExpect(jsonPath("$.patterns").value("Wire Tap Pattern demonstrated"))
                    .andDo(print())
                    .andReturn();

            // Verify producer template was called with correct parameters
            verify(producerTemplate, times(1)).requestBodyAndHeaders(
                    eq("direct:payment-wire-tap-entry"),
                    eq(validPaymentRequest),
                    any(Map.class)
            );

            String responseContent = result.getResponse().getContentAsString();
            Map<?, ?> response = objectMapper.readValue(responseContent, Map.class);
            
            assertNotNull(response.get("timestamp"));
            assertTrue(response.get("paymentId").toString().startsWith("PAY-"));
        }

        @Test
        @DisplayName("Should handle high-value transactions with enhanced audit")
        void shouldHandleHighValueTransactionsWithEnhancedAudit() throws Exception {
            // High-value transaction
            validPaymentRequest.put("amount", "1500.00");
            
            when(producerTemplate.requestBodyAndHeaders(
                    eq("direct:payment-wire-tap-entry"),
                    any(),
                    any(Map.class)
            )).thenReturn("SUCCESS");

            mockMvc.perform(post("/payments/process")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validPaymentRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Payment processing initiated with comprehensive audit trail"))
                    .andExpect(jsonPath("$.auditTrail").exists())
                    .andDo(print());

            // Verify enhanced audit headers for high-value transaction
            verify(producerTemplate, times(1)).requestBodyAndHeaders(
                    eq("direct:payment-wire-tap-entry"),
                    eq(validPaymentRequest),
                    argThat(headers -> {
                        Map<String, Object> headerMap = (Map<String, Object>) headers;
                        return "1500.00".equals(headerMap.get("amount"));
                    })
            );
        }
    }

    @Nested
    @DisplayName("Retry Pattern Endpoint Tests")
    class RetryPatternTests {

        @Test
        @DisplayName("Should process payment with retry pattern successfully")
        void shouldProcessPaymentWithRetryPatternSuccessfully() throws Exception {
            when(producerTemplate.requestBodyAndHeaders(
                    eq("direct:payment-retry-entry"),
                    any(),
                    any(Map.class)
            )).thenReturn("SUCCESS");

            mockMvc.perform(post("/payments/process-with-retry")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validPaymentRequest)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message").value("Payment processed with retry pattern"))
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.retryPattern").value("Exponential backoff with circuit breaker integration"))
                    .andExpect(jsonPath("$.patterns").value("Retry Pattern demonstrated"))
                    .andDo(print());

            verify(producerTemplate, times(1)).requestBodyAndHeaders(
                    eq("direct:payment-retry-entry"),
                    eq(validPaymentRequest),
                    any(Map.class)
            );
        }

        @Test
        @DisplayName("Should handle retry failures and return error response")
        void shouldHandleRetryFailuresAndReturnErrorResponse() throws Exception {
            when(producerTemplate.requestBodyAndHeaders(
                    eq("direct:payment-retry-entry"),
                    any(),
                    any(Map.class)
            )).thenThrow(new RuntimeException("Gateway timeout after retries"));

            mockMvc.perform(post("/payments/process-with-retry")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validPaymentRequest)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message").value("Payment failed after retry attempts"))
                    .andExpect(jsonPath("$.status").value("FAILED"))
                    .andExpect(jsonPath("$.error").value("Gateway timeout after retries"))
                    .andExpect(jsonPath("$.retryExhausted").value(true))
                    .andDo(print());
        }

        @Test
        @DisplayName("Should use MOCK gateway for retry demonstration")
        void shouldUseMockGatewayForRetryDemonstration() throws Exception {
            when(producerTemplate.requestBodyAndHeaders(
                    eq("direct:payment-retry-entry"),
                    any(),
                    any(Map.class)
            )).thenReturn("SUCCESS");

            mockMvc.perform(post("/payments/process-with-retry")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validPaymentRequest)))
                    .andExpect(status().isOk())
                    .andDo(print());

            // Verify MOCK gateway is used for retry demo
            verify(producerTemplate, times(1)).requestBodyAndHeaders(
                    eq("direct:payment-retry-entry"),
                    eq(validPaymentRequest),
                    argThat(headers -> {
                        Map<String, Object> headerMap = (Map<String, Object>) headers;
                        return "MOCK".equals(headerMap.get("paymentGateway"));
                    })
            );
        }
    }

    @Nested
    @DisplayName("Split Pattern Endpoint Tests")
    class SplitPatternTests {

        @Test
        @DisplayName("Should process batch payments successfully")
        void shouldProcessBatchPaymentsSuccessfully() throws Exception {
            Map<String, Object> batchRequest = Map.of("payments", validBatchRequest);
            
            when(producerTemplate.requestBody(
                    eq("direct:batch-payment-entry"),
                    any(Object.class)
            )).thenReturn("BATCH_SUCCESS");

            MvcResult result = mockMvc.perform(post("/payments/batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(batchRequest)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message").value("Batch payment processing initiated"))
                    .andExpect(jsonPath("$.totalPayments").value(3))
                    .andExpect(jsonPath("$.status").value("PROCESSING"))
                    .andExpect(jsonPath("$.splitPattern").value("Parallel processing with aggregation and correlation"))
                    .andExpect(jsonPath("$.patterns").value("Split Pattern with Aggregator demonstrated"))
                    .andDo(print())
                    .andReturn();

            // Verify payments were enhanced with required fields
            verify(producerTemplate, times(1)).requestBody(
                    eq("direct:batch-payment-entry"),
                    any(Object.class)
            );
        }

        @Test
        @DisplayName("Should return error for empty batch request")
        void shouldReturnErrorForEmptyBatchRequest() throws Exception {
            Map<String, Object> emptyBatchRequest = Map.of("payments", Collections.emptyList());

            mockMvc.perform(post("/payments/batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(emptyBatchRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.error").value("No payments provided in batch"))
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andDo(print());

            // Verify producer template was not called
            verify(producerTemplate, never()).requestBody(any(String.class), any());
        }

        @Test
        @DisplayName("Should return error for missing payments field")
        void shouldReturnErrorForMissingPaymentsField() throws Exception {
            Map<String, Object> invalidRequest = Map.of("invalid", "request");

            mockMvc.perform(post("/payments/batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.error").value("No payments provided in batch"))
                    .andDo(print());
        }

        @Test
        @DisplayName("Should enhance payments with default values")
        void shouldEnhancePaymentsWithDefaultValues() throws Exception {
            // Create payment without optional fields
            List<Map<String, Object>> minimalPayments = Arrays.asList(
                Map.of("amount", "25.00", "paymentMethod", "CREDIT_CARD"),
                Map.of("amount", "35.50", "paymentMethod", "DEBIT_CARD")
            );
            
            Map<String, Object> batchRequest = Map.of("payments", minimalPayments);
            
            when(producerTemplate.requestBody(
                    eq("direct:batch-payment-entry"),
                    any(List.class)
            )).thenReturn("SUCCESS");

            mockMvc.perform(post("/payments/batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(batchRequest)))
                    .andExpect(status().isOk())
                    .andDo(print());

            // Verify default values were added
            verify(producerTemplate, times(1)).requestBody(
                    eq("direct:batch-payment-entry"),
                    any(List.class)
            );
        }
    }

    @Nested
    @DisplayName("Filter Pattern Endpoint Tests")
    class FilterPatternTests {

        @Test
        @DisplayName("Should analyze fraud risk and return assessment")
        void shouldAnalyzeFraudRiskAndReturnAssessment() throws Exception {
            Map<String, Object> fraudRequest = new HashMap<>(validPaymentRequest);
            fraudRequest.put("customerCountry", "US");
            fraudRequest.put("customerIp", "192.168.1.100");
            
            when(producerTemplate.requestBodyAndHeaders(
                    eq("direct:fraud-monitoring-processor"),
                    any(),
                    any(Map.class)
            )).thenReturn("LOW_RISK");

            mockMvc.perform(post("/payments/fraud-analysis")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(fraudRequest)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message").value("Fraud analysis completed"))
                    .andExpect(jsonPath("$.riskLevel").exists())
                    .andExpect(jsonPath("$.patterns").value("Filter Pattern demonstrated"))
                    .andDo(print());

            // Verify fraud analysis headers
            verify(producerTemplate, times(1)).requestBodyAndHeaders(
                    eq("direct:fraud-monitoring-processor"),
                    eq(fraudRequest),
                    argThat(headers -> {
                        Map<String, Object> headerMap = (Map<String, Object>) headers;
                        return "US".equals(headerMap.get("customerCountry")) &&
                               "192.168.1.100".equals(headerMap.get("customerIp"));
                    })
            );
        }

        @Test
        @DisplayName("Should include transaction time in fraud analysis")
        void shouldIncludeTransactionTimeInFraudAnalysis() throws Exception {
            when(producerTemplate.requestBodyAndHeaders(
                    eq("direct:fraud-monitoring-processor"),
                    any(),
                    any(Map.class)
            )).thenReturn("MEDIUM_RISK");

            mockMvc.perform(post("/payments/fraud-analysis")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validPaymentRequest)))
                    .andExpect(status().isOk())
                    .andDo(print());

            // Verify transaction time header is included
            verify(producerTemplate, times(1)).requestBodyAndHeaders(
                    eq("direct:fraud-monitoring-processor"),
                    any(),
                    argThat(headers -> {
                        Map<String, Object> headerMap = (Map<String, Object>) headers;
                        return headerMap.containsKey("transactionTime");
                    })
            );
        }
    }

    @Nested
    @DisplayName("Payment Status Endpoint Tests")
    class PaymentStatusTests {

        @Test
        @DisplayName("Should return payment status for valid payment ID")
        void shouldReturnPaymentStatusForValidPaymentId() throws Exception {
            String paymentId = "PAY-TEST-12345";

            mockMvc.perform(get("/payments/status/{paymentId}", paymentId))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.paymentId").value(paymentId))
                    .andExpect(jsonPath("$.status").exists())
                    .andExpect(jsonPath("$.lastUpdated").exists())
                    .andExpect(jsonPath("$.patterns").value("Request-Reply Pattern demonstrated"))
                    .andDo(print());
        }

        @Test
        @DisplayName("Should handle payment ID with special characters")
        void shouldHandlePaymentIdWithSpecialCharacters() throws Exception {
            String paymentId = "PAY-TEST-12345-ABC";

            mockMvc.perform(get("/payments/status/{paymentId}", paymentId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paymentId").value(paymentId))
                    .andDo(print());
        }
    }

    @Nested
    @DisplayName("Request Validation Tests")
    class RequestValidationTests {

        @Test
        @DisplayName("Should handle malformed JSON gracefully")
        void shouldHandleMalformedJsonGracefully() throws Exception {
            String malformedJson = "{ \"amount\": \"invalid_json\" ";

            mockMvc.perform(post("/payments/process")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(malformedJson))
                    .andExpect(status().isBadRequest())
                    .andDo(print());
        }

        @Test
        @DisplayName("Should handle empty request body")
        void shouldHandleEmptyRequestBody() throws Exception {
            mockMvc.perform(post("/payments/process")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                    .andExpect(status().isOk()) // Controller should handle empty fields gracefully
                    .andDo(print());
        }

        @Test
        @DisplayName("Should handle missing content type")
        void shouldHandleMissingContentType() throws Exception {
            mockMvc.perform(post("/payments/process")
                    .content(objectMapper.writeValueAsString(validPaymentRequest)))
                    .andExpect(status().isUnsupportedMediaType())
                    .andDo(print());
        }
    }

    private Map<String, Object> createPaymentRequest(String paymentId, String amount, String paymentMethod) {
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