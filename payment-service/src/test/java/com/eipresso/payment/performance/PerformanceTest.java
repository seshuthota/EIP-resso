package com.eipresso.payment.performance;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Payment Service Performance and Load Test Suite
 * 
 * Tests high-volume scenarios and verifies EIP patterns perform
 * under stress conditions:
 * - Concurrent payment processing
 * - Batch payment performance
 * - Memory usage under load
 * - Circuit breaker behavior under stress
 * - Error handling at scale
 */
@SpringBootTest
@CamelSpringBootTest
@ActiveProfiles("test")
@DisplayName("Payment Service Performance Tests")
@EnabledIfEnvironmentVariable(named = "RUN_PERFORMANCE_TESTS", matches = "true")
class PerformanceTest {

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private ProducerTemplate producerTemplate;

    private ExecutorService executorService;
    private final int THREAD_POOL_SIZE = 10;
    private final int HIGH_VOLUME_COUNT = 1000;
    private final int STRESS_TEST_COUNT = 5000;

    @BeforeEach
    void setUp() {
        executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    }

    @Test
    @DisplayName("Should handle concurrent payment processing efficiently")
    void shouldHandleConcurrentPaymentProcessingEfficiently() throws Exception {
        int concurrentRequests = 100;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(concurrentRequests);
        
        Instant startTime = Instant.now();
        
        // Submit concurrent payment requests
        IntStream.range(0, concurrentRequests).forEach(i -> {
            executorService.submit(() -> {
                try {
                    Map<String, Object> paymentRequest = createPaymentRequest(
                        "PAY-PERF-" + i, 
                        "25.00", 
                        "CREDIT_CARD"
                    );
                    
                    Map<String, Object> headers = createHeaders("CONCURRENT-" + i);
                    
                    producerTemplate.sendBodyAndHeaders(
                        "direct:payment-wire-tap-entry",
                        paymentRequest,
                        headers
                    );
                    
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    System.err.println("Payment processing error: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        });
        
        // Wait for all requests to complete (max 30 seconds)
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        Instant endTime = Instant.now();
        
        Duration totalTime = Duration.between(startTime, endTime);
        double throughput = (double) successCount.get() / totalTime.getSeconds();
        
        // Performance assertions
        assertTrue(completed, "All concurrent requests should complete within timeout");
        assertTrue(successCount.get() > concurrentRequests * 0.95, 
                  "At least 95% of requests should succeed");
        assertTrue(throughput > 10, "Throughput should be at least 10 requests/second");
        assertTrue(totalTime.getSeconds() < 25, "Total processing time should be under 25 seconds");
        
        System.out.printf("Concurrent Performance Results:%n" +
                         "- Requests: %d%n" +
                         "- Successful: %d%n" +
                         "- Errors: %d%n" +
                         "- Total Time: %d seconds%n" +
                         "- Throughput: %.2f req/sec%n",
                         concurrentRequests, successCount.get(), errorCount.get(),
                         totalTime.getSeconds(), throughput);
    }

    @Test
    @DisplayName("Should process high-volume batch payments efficiently")
    void shouldProcessHighVolumeBatchPaymentsEfficiently() throws Exception {
        int batchSize = 500;
        List<Map<String, Object>> payments = new ArrayList<>();
        
        // Create large batch
        for (int i = 0; i < batchSize; i++) {
            payments.add(createPaymentRequest(
                "BATCH-" + i, 
                String.valueOf(10.00 + (i % 100)), 
                getRandomPaymentMethod()
            ));
        }
        
        Instant startTime = Instant.now();
        
        // Process batch
        Object result = producerTemplate.requestBody(
            "direct:batch-payment-entry", 
            payments
        );
        
        Instant endTime = Instant.now();
        Duration processingTime = Duration.between(startTime, endTime);
        
        // Performance assertions
        assertNotNull(result);
        assertTrue(processingTime.getSeconds() < 60, 
                  "Batch processing should complete within 60 seconds");
        
        double paymentsPerSecond = (double) batchSize / processingTime.getSeconds();
        assertTrue(paymentsPerSecond > 20, 
                  "Should process at least 20 payments per second");
        
        System.out.printf("Batch Performance Results:%n" +
                         "- Batch Size: %d%n" +
                         "- Processing Time: %d seconds%n" +
                         "- Rate: %.2f payments/sec%n",
                         batchSize, processingTime.getSeconds(), paymentsPerSecond);
    }

    @Test
    @DisplayName("Should maintain performance under memory pressure")
    void shouldMaintainPerformanceUnderMemoryPressure() throws Exception {
        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        int iterations = 200;
        AtomicLong totalProcessingTime = new AtomicLong(0);
        
        for (int i = 0; i < iterations; i++) {
            Map<String, Object> paymentRequest = createPaymentRequest(
                "MEM-" + i, 
                "100.00", 
                "CREDIT_CARD"
            );
            
            // Add large description to increase memory usage
            paymentRequest.put("description", generateLargeDescription(1000));
            
            Instant start = Instant.now();
            
            producerTemplate.sendBodyAndHeaders(
                "direct:payment-wire-tap-entry",
                paymentRequest,
                createHeaders("MEMORY-" + i)
            );
            
            Instant end = Instant.now();
            totalProcessingTime.addAndGet(Duration.between(start, end).toMillis());
            
            // Force garbage collection every 50 iterations
            if (i % 50 == 0) {
                System.gc();
                Thread.sleep(100); // Allow GC to complete
            }
        }
        
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - initialMemory;
        double avgProcessingTime = (double) totalProcessingTime.get() / iterations;
        
        // Memory and performance assertions
        assertTrue(memoryIncrease < 100 * 1024 * 1024, 
                  "Memory increase should be less than 100MB");
        assertTrue(avgProcessingTime < 1000, 
                  "Average processing time should be under 1 second");
        
        System.out.printf("Memory Performance Results:%n" +
                         "- Iterations: %d%n" +
                         "- Memory Increase: %.2f MB%n" +
                         "- Avg Processing Time: %.2f ms%n",
                         iterations, memoryIncrease / (1024.0 * 1024.0), avgProcessingTime);
    }

    @Test
    @DisplayName("Should handle retry pattern under high load")
    void shouldHandleRetryPatternUnderHighLoad() throws Exception {
        int requestCount = 300;
        AtomicInteger retryCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(requestCount);
        
        Instant startTime = Instant.now();
        
        IntStream.range(0, requestCount).forEach(i -> {
            executorService.submit(() -> {
                try {
                    Map<String, Object> paymentRequest = createPaymentRequest(
                        "RETRY-" + i, 
                        "50.00", 
                        "CREDIT_CARD"
                    );
                    
                    Map<String, Object> headers = createHeaders("RETRY-LOAD-" + i);
                    headers.put("paymentGateway", "MOCK"); // Use mock gateway for retry simulation
                    headers.put("maxRetries", 2);
                    
                    // 30% chance of triggering retry scenario
                    if (i % 3 == 0) {
                        headers.put("simulateFailure", true);
                        retryCount.incrementAndGet();
                    }
                    
                    producerTemplate.sendBodyAndHeaders(
                        "direct:payment-retry-entry",
                        paymentRequest,
                        headers
                    );
                    
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // Expected for some retry scenarios
                    System.err.println("Retry load test error: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        });
        
        boolean completed = latch.await(120, TimeUnit.SECONDS);
        Instant endTime = Instant.now();
        Duration totalTime = Duration.between(startTime, endTime);
        
        // Retry performance assertions
        assertTrue(completed, "All retry requests should complete within timeout");
        assertTrue(successCount.get() > requestCount * 0.8, 
                  "At least 80% of requests should eventually succeed");
        assertTrue(totalTime.getSeconds() < 100, 
                  "Retry processing should complete within 100 seconds");
        
        System.out.printf("Retry Load Test Results:%n" +
                         "- Total Requests: %d%n" +
                         "- Retry Scenarios: %d%n" +
                         "- Successful: %d%n" +
                         "- Total Time: %d seconds%n",
                         requestCount, retryCount.get(), successCount.get(), totalTime.getSeconds());
    }

    @Test
    @DisplayName("Should handle fraud detection at scale")
    void shouldHandleFraudDetectionAtScale() throws Exception {
        int requestCount = 400;
        AtomicInteger highRiskCount = new AtomicInteger(0);
        AtomicInteger lowRiskCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(requestCount);
        
        Instant startTime = Instant.now();
        
        IntStream.range(0, requestCount).forEach(i -> {
            executorService.submit(() -> {
                try {
                    Map<String, Object> paymentRequest = createPaymentRequest(
                        "FRAUD-" + i, 
                        String.valueOf(50.00 + (i * 10)), // Varying amounts
                        "CREDIT_CARD"
                    );
                    
                    Map<String, Object> headers = createHeaders("FRAUD-SCALE-" + i);
                    
                    // Create fraud scenarios for 20% of requests
                    if (i % 5 == 0) {
                        headers.put("customerCountry", "HIGH_RISK_COUNTRY");
                        headers.put("suspiciousIpAddress", "203.0.113." + (i % 255));
                        headers.put("transactionTime", 3); // 3 AM
                        paymentRequest.put("amount", "5000.00"); // High amount
                        highRiskCount.incrementAndGet();
                    } else {
                        headers.put("customerCountry", "US");
                        headers.put("customerIp", "192.168.1." + (i % 255));
                        headers.put("transactionTime", 14); // 2 PM
                        lowRiskCount.incrementAndGet();
                    }
                    
                    producerTemplate.sendBodyAndHeaders(
                        "direct:fraud-monitoring-processor",
                        paymentRequest,
                        headers
                    );
                    
                } catch (Exception e) {
                    System.err.println("Fraud detection error: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        });
        
        boolean completed = latch.await(60, TimeUnit.SECONDS);
        Instant endTime = Instant.now();
        Duration totalTime = Duration.between(startTime, endTime);
        
        // Fraud detection performance assertions
        assertTrue(completed, "All fraud detection requests should complete");
        assertTrue(totalTime.getSeconds() < 50, 
                  "Fraud detection should complete within 50 seconds");
        
        double detectionRate = (double) requestCount / totalTime.getSeconds();
        assertTrue(detectionRate > 10, 
                  "Should process at least 10 fraud checks per second");
        
        System.out.printf("Fraud Detection Scale Results:%n" +
                         "- Total Requests: %d%n" +
                         "- High Risk Scenarios: %d%n" +
                         "- Low Risk Scenarios: %d%n" +
                         "- Detection Rate: %.2f checks/sec%n" +
                         "- Total Time: %d seconds%n",
                         requestCount, highRiskCount.get(), lowRiskCount.get(), 
                         detectionRate, totalTime.getSeconds());
    }

    @Test
    @Disabled("Long-running stress test - enable for full performance validation")
    @DisplayName("Should survive extended stress testing")
    void shouldSurviveExtendedStressTesting() throws Exception {
        int duration = 300; // 5 minutes
        AtomicInteger requestCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);
        
        Instant startTime = Instant.now();
        Instant endTime = startTime.plusSeconds(duration);
        
        // Schedule continuous payment requests
        ScheduledFuture<?> paymentGenerator = scheduler.scheduleAtFixedRate(() -> {
            if (Instant.now().isBefore(endTime)) {
                try {
                    int count = requestCount.incrementAndGet();
                    Map<String, Object> paymentRequest = createPaymentRequest(
                        "STRESS-" + count, 
                        "25.00", 
                        getRandomPaymentMethod()
                    );
                    
                    producerTemplate.sendBodyAndHeaders(
                        "direct:payment-wire-tap-entry",
                        paymentRequest,
                        createHeaders("STRESS-" + count)
                    );
                    
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                }
            }
        }, 0, 100, TimeUnit.MILLISECONDS); // 10 requests per second
        
        // Wait for stress test duration
        Thread.sleep(duration * 1000);
        paymentGenerator.cancel(false);
        scheduler.shutdown();
        
        Instant actualEndTime = Instant.now();
        Duration actualDuration = Duration.between(startTime, actualEndTime);
        
        // Stress test assertions
        assertTrue(requestCount.get() > duration * 8, 
                  "Should process at least 8 requests per second");
        assertTrue(errorCount.get() < requestCount.get() * 0.05, 
                  "Error rate should be less than 5%");
        
        double avgThroughput = (double) requestCount.get() / actualDuration.getSeconds();
        
        System.out.printf("Stress Test Results:%n" +
                         "- Duration: %d seconds%n" +
                         "- Total Requests: %d%n" +
                         "- Errors: %d%n" +
                         "- Error Rate: %.2f%%%n" +
                         "- Average Throughput: %.2f req/sec%n",
                         actualDuration.getSeconds(), requestCount.get(), errorCount.get(),
                         (double) errorCount.get() / requestCount.get() * 100, avgThroughput);
    }

    @Test
    @DisplayName("Should validate route performance metrics")
    void shouldValidateRoutePerformanceMetrics() throws Exception {
        // Warm up the routes
        for (int i = 0; i < 10; i++) {
            Map<String, Object> warmupRequest = createPaymentRequest(
                "WARMUP-" + i, "10.00", "CREDIT_CARD"
            );
            producerTemplate.sendBodyAndHeaders(
                "direct:payment-wire-tap-entry",
                warmupRequest,
                createHeaders("WARMUP-" + i)
            );
        }
        
        // Measure route performance
        int testRequests = 100;
        long totalTime = 0;
        long minTime = Long.MAX_VALUE;
        long maxTime = 0;
        
        for (int i = 0; i < testRequests; i++) {
            Map<String, Object> paymentRequest = createPaymentRequest(
                "METRIC-" + i, "25.00", "CREDIT_CARD"
            );
            
            long startTime = System.nanoTime();
            
            producerTemplate.sendBodyAndHeaders(
                "direct:payment-wire-tap-entry",
                paymentRequest,
                createHeaders("METRIC-" + i)
            );
            
            long endTime = System.nanoTime();
            long requestTime = (endTime - startTime) / 1_000_000; // Convert to milliseconds
            
            totalTime += requestTime;
            minTime = Math.min(minTime, requestTime);
            maxTime = Math.max(maxTime, requestTime);
        }
        
        double avgTime = (double) totalTime / testRequests;
        
        // Performance metric assertions
        assertTrue(avgTime < 500, "Average processing time should be under 500ms");
        assertTrue(maxTime < 2000, "Maximum processing time should be under 2 seconds");
        assertTrue(minTime < 100, "Minimum processing time should be under 100ms");
        
        System.out.printf("Route Performance Metrics:%n" +
                         "- Average Time: %.2f ms%n" +
                         "- Minimum Time: %d ms%n" +
                         "- Maximum Time: %d ms%n" +
                         "- Total Requests: %d%n",
                         avgTime, minTime, maxTime, testRequests);
    }

    // Helper methods
    
    private Map<String, Object> createPaymentRequest(String paymentId, String amount, String paymentMethod) {
        Map<String, Object> payment = new HashMap<>();
        payment.put("paymentId", paymentId);
        payment.put("amount", amount);
        payment.put("currency", "USD");
        payment.put("paymentMethod", paymentMethod);
        payment.put("paymentGateway", "STRIPE");
        payment.put("customerEmail", "perf@example.com");
        payment.put("customerName", "Performance Test Customer");
        payment.put("orderId", System.currentTimeMillis());
        payment.put("userId", 4001L);
        return payment;
    }
    
    private Map<String, Object> createHeaders(String correlationId) {
        Map<String, Object> headers = new HashMap<>();
        headers.put("correlationId", correlationId);
        headers.put("transactionType", "PERFORMANCE_TEST");
        headers.put("customerIp", "192.168.1.100");
        headers.put("userAgent", "Performance Test Client 1.0");
        return headers;
    }
    
    private String getRandomPaymentMethod() {
        String[] methods = {"CREDIT_CARD", "DEBIT_CARD", "PAYPAL", "APPLE_PAY", "GOOGLE_PAY"};
        return methods[new Random().nextInt(methods.length)];
    }
    
    private String generateLargeDescription(int sizeKB) {
        StringBuilder sb = new StringBuilder();
        String pattern = "This is a performance test description with extended content. ";
        
        while (sb.length() < sizeKB * 1024) {
            sb.append(pattern);
        }
        
        return sb.toString();
    }
}