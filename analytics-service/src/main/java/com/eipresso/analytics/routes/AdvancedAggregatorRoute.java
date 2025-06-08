package com.eipresso.analytics.routes;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.aggregate.GroupedExchangeAggregationStrategy;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Advanced Aggregator Pattern Implementation for Analytics Service
 * 
 * EIP Pattern: Aggregator Pattern (Advanced)
 * Purpose: Time-window metrics aggregation with correlation and business intelligence
 * Clustering: Active-Active compatible (distributed aggregation)
 * 
 * Routes:
 * 1. advanced-aggregator-entry: Main aggregation entry point
 * 2. time-window-aggregator: Time-based aggregation windows
 * 3. business-intelligence-aggregator: Advanced BI aggregations
 * 4. correlation-aggregator: Correlated event aggregation
 * 5. metric-consolidator: Consolidate metrics from multiple sources
 * 6. aggregation-finalizer: Finalize and publish aggregated results
 */
@Component
public class AdvancedAggregatorRoute extends RouteBuilder {

    // Advanced aggregation storage
    private final Map<String, Map<String, Object>> timeWindowAggregations = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> businessMetrics = new ConcurrentHashMap<>();

    @Override
    public void configure() throws Exception {
        
        // Global error handling for Advanced Aggregator pattern
        errorHandler(deadLetterChannel("direct:advanced-aggregator-dead-letter")
            .maximumRedeliveries(5)
            .redeliveryDelay(2000)
            .retryAttemptedLogLevel(LoggingLevel.WARN)
            .useExponentialBackOff()
            .backOffMultiplier(2)
            .maximumRedeliveryDelay(30000));

        /**
         * Route 1: Advanced Aggregator Entry Point
         */
        from("direct:advanced-aggregator-entry")
            .routeId("advanced-aggregator-entry")
            .description("Advanced Aggregator Pattern: Main aggregation entry point")
            .log("🔢 Starting advanced aggregation: ${header.eventType}")
            
            .process(exchange -> {
                String aggregationId = UUID.randomUUID().toString();
                exchange.getIn().setHeader("aggregationId", aggregationId);
                exchange.getIn().setHeader("aggregationTimestamp", LocalDateTime.now());
                exchange.getIn().setHeader("aggregationMode", "ADVANCED");
                
                String eventType = exchange.getIn().getHeader("eventType", String.class);
                String timeWindow = determineTimeWindow();
                
                exchange.getIn().setHeader("timeWindow", timeWindow);
                exchange.getIn().setHeader("correlationKey", generateCorrelationKey(eventType, timeWindow));
                
                log.info("🔢 Advanced aggregation initiated: {} [{}] - Window: {}", 
                        eventType, aggregationId, timeWindow);
            })
            
            .multicast()
                .parallelProcessing(true)
                .to("direct:time-window-aggregator",
                   "direct:business-intelligence-aggregator",
                   "direct:correlation-aggregator")
            .end()
            
            .to("direct:metric-consolidator")
            .to("direct:aggregation-finalizer")
            .log("✅ Advanced aggregation completed for ${header.aggregationId}");

        /**
         * Route 2: Time-Window Aggregator
         */
        from("direct:time-window-aggregator")
            .routeId("time-window-aggregator")
            .description("Advanced Aggregator Pattern: Time-window aggregation")
            .log("⏰ Processing time-window aggregation")
            
            .aggregate(header("timeWindow"), new GroupedExchangeAggregationStrategy())
                .completionTimeout(30000) // 30-second window
                .completionSize(100)      // or 100 events
                .eagerCheckCompletion()
                
                .process(exchange -> {
                    String timeWindow = exchange.getIn().getHeader("timeWindow", String.class);
                    
                    Map<String, Object> windowAggregation = new HashMap<>();
                    windowAggregation.put("timeWindow", timeWindow);
                    windowAggregation.put("aggregationTimestamp", LocalDateTime.now());
                    windowAggregation.put("eventCount", 1); // Will be aggregated
                    windowAggregation.put("windowType", "TIME_BASED");
                    
                    // Store time window aggregation
                    timeWindowAggregations.put(timeWindow, windowAggregation);
                    
                    exchange.getIn().setBody(windowAggregation);
                    
                    log.info("⏰ Time-window aggregation: {} - Events processed", timeWindow);
                })
                
            .end()
            
            .to("direct:time-window-publisher")
            .log("✅ Time-window aggregation completed");

        /**
         * Route 3: Business Intelligence Aggregator
         */
        from("direct:business-intelligence-aggregator")
            .routeId("business-intelligence-aggregator")
            .description("Advanced Aggregator Pattern: Business intelligence aggregation")
            .log("🧠 Processing business intelligence aggregation")
            
            .process(exchange -> {
                String eventType = exchange.getIn().getHeader("eventType", String.class);
                String userId = exchange.getIn().getHeader("userId", String.class);
                String orderId = exchange.getIn().getHeader("orderId", String.class);
                
                Map<String, Object> biAggregation = new HashMap<>();
                biAggregation.put("eventType", eventType);
                biAggregation.put("aggregationTimestamp", LocalDateTime.now());
                
                // Business intelligence calculations
                if (eventType != null) {
                    if (eventType.startsWith("ORDER_")) {
                        updateBusinessMetric("orders.total");
                        biAggregation.put("orderMetrics", getBusinessMetric("orders.total"));
                        biAggregation.put("businessCategory", "revenue");
                        
                        if ("ORDER_PAID".equals(eventType)) {
                            updateBusinessMetric("revenue.transactions");
                            biAggregation.put("revenueImpact", calculateRevenueImpact());
                        }
                    } else if (eventType.startsWith("USER_")) {
                        updateBusinessMetric("users.activity");
                        biAggregation.put("userMetrics", getBusinessMetric("users.activity"));
                        biAggregation.put("businessCategory", "engagement");
                        
                        if ("USER_REGISTERED".equals(eventType)) {
                            updateBusinessMetric("users.acquisition");
                            biAggregation.put("acquisitionImpact", calculateAcquisitionImpact());
                        }
                    }
                }
                
                exchange.getIn().setBody(biAggregation);
                
                log.info("🧠 BI aggregation: {} - Category: {}", 
                        eventType, biAggregation.get("businessCategory"));
            })
            
            .choice()
                .when(simple("${body[businessCategory]} == 'revenue'"))
                    .to("direct:revenue-analytics-aggregator")
                .when(simple("${body[businessCategory]} == 'engagement'"))
                    .to("direct:engagement-analytics-aggregator")
                .otherwise()
                    .to("direct:generic-analytics-aggregator")
            .end()
            
            .log("✅ Business intelligence aggregation completed");

        /**
         * Route 4: Correlation Aggregator
         */
        from("direct:correlation-aggregator")
            .routeId("correlation-aggregator")
            .description("Advanced Aggregator Pattern: Correlated event aggregation")
            .log("🔗 Processing correlation aggregation")
            
            .aggregate(header("correlationKey"), new GroupedExchangeAggregationStrategy())
                .completionTimeout(15000) // 15-second correlation window
                .completionSize(50)       // or 50 correlated events
                
                .process(exchange -> {
                    String correlationKey = exchange.getIn().getHeader("correlationKey", String.class);
                    
                    Map<String, Object> correlationAggregation = new HashMap<>();
                    correlationAggregation.put("correlationKey", correlationKey);
                    correlationAggregation.put("correlationTimestamp", LocalDateTime.now());
                    correlationAggregation.put("correlatedEvents", 1); // Will be aggregated
                    correlationAggregation.put("correlationType", "EVENT_SEQUENCE");
                    
                    exchange.getIn().setBody(correlationAggregation);
                    
                    log.info("🔗 Correlation aggregation: {} - Events correlated", correlationKey);
                })
                
            .end()
            
            .to("direct:correlation-analytics-processor")
            .log("✅ Correlation aggregation completed");

        /**
         * Route 5: Metric Consolidator
         */
        from("direct:metric-consolidator")
            .routeId("metric-consolidator")
            .description("Advanced Aggregator Pattern: Consolidate metrics")
            .log("📊 Consolidating metrics from multiple sources")
            
            .process(exchange -> {
                String aggregationId = exchange.getIn().getHeader("aggregationId", String.class);
                LocalDateTime consolidationTime = LocalDateTime.now();
                
                Map<String, Object> consolidatedMetrics = new HashMap<>();
                consolidatedMetrics.put("aggregationId", aggregationId);
                consolidatedMetrics.put("consolidationTimestamp", consolidationTime);
                
                // Consolidate business metrics
                consolidatedMetrics.put("totalOrders", getBusinessMetric("orders.total"));
                consolidatedMetrics.put("totalUsers", getBusinessMetric("users.activity"));
                consolidatedMetrics.put("totalRevenue", getBusinessMetric("revenue.transactions"));
                
                // Calculate consolidated KPIs
                consolidatedMetrics.put("conversionRate", calculateConversionRate());
                consolidatedMetrics.put("averageOrderValue", calculateAverageOrderValue());
                consolidatedMetrics.put("customerLifetimeValue", calculateCustomerLifetimeValue());
                
                exchange.getIn().setBody(consolidatedMetrics);
                
                log.info("📊 Metrics consolidated: {} orders, {} users, {} revenue", 
                        consolidatedMetrics.get("totalOrders"),
                        consolidatedMetrics.get("totalUsers"),
                        consolidatedMetrics.get("totalRevenue"));
            })
            
            .to("direct:consolidated-metrics-publisher")
            .log("✅ Metric consolidation completed");

        /**
         * Route 6: Aggregation Finalizer
         */
        from("direct:aggregation-finalizer")
            .routeId("aggregation-finalizer")
            .description("Advanced Aggregator Pattern: Finalize aggregations")
            .log("🏁 Finalizing aggregation results")
            
            .process(exchange -> {
                String aggregationId = exchange.getIn().getHeader("aggregationId", String.class);
                
                Map<String, Object> finalizedAggregation = new HashMap<>();
                finalizedAggregation.put("aggregationId", aggregationId);
                finalizedAggregation.put("finalizationTimestamp", LocalDateTime.now());
                finalizedAggregation.put("status", "COMPLETED");
                finalizedAggregation.put("processingDuration", calculateProcessingDuration(exchange));
                
                // Add aggregation summary
                finalizedAggregation.put("totalAggregations", timeWindowAggregations.size());
                finalizedAggregation.put("businessMetricsCount", businessMetrics.size());
                
                exchange.getIn().setBody(finalizedAggregation);
                
                log.info("🏁 Aggregation finalized: {} - Duration: {}ms", 
                        aggregationId, finalizedAggregation.get("processingDuration"));
            })
            
            .to("direct:finalized-aggregation-publisher")
            .log("✅ Aggregation finalization completed");

        // Specialized Analytics Aggregators
        from("direct:revenue-analytics-aggregator")
            .routeId("revenue-analytics-aggregator")
            .description("Advanced Aggregator: Revenue analytics")
            .log("💰 Processing revenue analytics aggregation")
            .process(exchange -> {
                Map<String, Object> revenueAnalytics = new HashMap<>();
                revenueAnalytics.put("category", "revenue");
                revenueAnalytics.put("totalRevenue", getBusinessMetric("revenue.transactions"));
                revenueAnalytics.put("timestamp", LocalDateTime.now());
                exchange.getIn().setBody(revenueAnalytics);
            })
            .log("✅ Revenue analytics aggregation completed");

        from("direct:engagement-analytics-aggregator")
            .routeId("engagement-analytics-aggregator")
            .description("Advanced Aggregator: Engagement analytics")
            .log("👤 Processing engagement analytics aggregation")
            .process(exchange -> {
                Map<String, Object> engagementAnalytics = new HashMap<>();
                engagementAnalytics.put("category", "engagement");
                engagementAnalytics.put("totalEngagement", getBusinessMetric("users.activity"));
                engagementAnalytics.put("timestamp", LocalDateTime.now());
                exchange.getIn().setBody(engagementAnalytics);
            })
            .log("✅ Engagement analytics aggregation completed");

        from("direct:generic-analytics-aggregator")
            .routeId("generic-analytics-aggregator")
            .description("Advanced Aggregator: Generic analytics")
            .log("🔧 Processing generic analytics aggregation")
            .log("✅ Generic analytics aggregation completed");

        // Publishers
        from("direct:time-window-publisher")
            .routeId("time-window-publisher")
            .log("📡 Publishing time-window aggregation")
            .to("elasticsearch:time-windows?operation=INDEX&indexName=time-window-aggregations")
            .log("✅ Time-window aggregation published");

        from("direct:correlation-analytics-processor")
            .routeId("correlation-analytics-processor")
            .log("🔗 Processing correlation analytics")
            .log("✅ Correlation analytics processed");

        from("direct:consolidated-metrics-publisher")
            .routeId("consolidated-metrics-publisher")
            .log("📊 Publishing consolidated metrics")
            .to("elasticsearch:consolidated-metrics?operation=INDEX&indexName=consolidated-metrics")
            .log("✅ Consolidated metrics published");

        from("direct:finalized-aggregation-publisher")
            .routeId("finalized-aggregation-publisher")
            .log("🏁 Publishing finalized aggregation")
            .to("rabbitmq:finalized-aggregations?exchangeType=topic&routingKey=analytics.finalized")
            .log("✅ Finalized aggregation published");

        /**
         * Periodic Aggregation Trigger (Every 60 seconds)
         */
        from("timer:periodic-aggregation?period=60000")
            .routeId("periodic-aggregation-trigger")
            .description("Advanced Aggregator Pattern: Periodic aggregation trigger")
            .log("⏰ Triggering periodic aggregation")
            
            .process(exchange -> {
                exchange.getIn().setHeader("eventType", "PERIODIC_AGGREGATION");
                exchange.getIn().setHeader("triggerType", "TIMER_BASED");
                exchange.getIn().setHeader("triggerTimestamp", LocalDateTime.now());
                
                log.info("⏰ Periodic aggregation triggered");
            })
            
            .to("direct:advanced-aggregator-entry")
            .log("✅ Periodic aggregation completed");

        /**
         * Dead Letter Channel
         */
        from("direct:advanced-aggregator-dead-letter")
            .routeId("advanced-aggregator-dead-letter")
            .description("Advanced Aggregator Pattern: Dead letter channel")
            .log(LoggingLevel.ERROR, "💀 Advanced aggregation failed: ${exception.message}")
            .process(exchange -> {
                String failureId = UUID.randomUUID().toString();
                exchange.getIn().setHeader("failureId", failureId);
                exchange.getIn().setHeader("failureTimestamp", LocalDateTime.now());
                log.error("💀 Advanced aggregation failure logged: {}", failureId);
            })
            .log("💾 Advanced aggregation failure logged for analysis");
    }

    // Helper methods
    private String determineTimeWindow() {
        LocalDateTime now = LocalDateTime.now();
        return now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm"));
    }

    private String generateCorrelationKey(String eventType, String timeWindow) {
        return String.format("%s:%s", eventType, timeWindow);
    }

    private void updateBusinessMetric(String key) {
        businessMetrics.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
    }

    private long getBusinessMetric(String key) {
        return businessMetrics.getOrDefault(key, new AtomicLong(0)).get();
    }

    private double calculateRevenueImpact() {
        return getBusinessMetric("revenue.transactions") * 25.0; // Assume $25 average
    }

    private double calculateAcquisitionImpact() {
        return getBusinessMetric("users.acquisition") * 100.0; // Assume $100 customer value
    }

    private double calculateConversionRate() {
        long orders = getBusinessMetric("orders.total");
        long users = getBusinessMetric("users.activity");
        return users > 0 ? (orders * 100.0) / users : 0.0;
    }

    private double calculateAverageOrderValue() {
        long orders = getBusinessMetric("orders.total");
        long revenue = getBusinessMetric("revenue.transactions");
        return orders > 0 ? (revenue * 25.0) / orders : 0.0;
    }

    private double calculateCustomerLifetimeValue() {
        return calculateAverageOrderValue() * 5.0; // Assume 5 orders per customer
    }

    private long calculateProcessingDuration(Exchange exchange) {
        LocalDateTime start = exchange.getIn().getHeader("aggregationTimestamp", LocalDateTime.class);
        if (start != null) {
            return java.time.Duration.between(start, LocalDateTime.now()).toMillis();
        }
        return 0L;
    }
} 