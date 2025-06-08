package com.eipresso.analytics.routes;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simplified Aggregator Pattern Implementation for Analytics Service
 */
@Component
public class AggregatorRouteSimple extends RouteBuilder {

    private final Map<String, AtomicLong> aggregationCounters = new ConcurrentHashMap<>();
    private final Map<String, Double> aggregationSums = new ConcurrentHashMap<>();

    @Override
    public void configure() throws Exception {
        
        errorHandler(deadLetterChannel("direct:aggregator-dead-letter-simple")
            .maximumRedeliveries(3)
            .redeliveryDelay(1000)
            .retryAttemptedLogLevel(LoggingLevel.WARN));

        /**
         * Route 1: Aggregator Entry Point
         */
        from("direct:aggregator-entry-simple")
            .routeId("aggregator-entry-simple")
            .description("Aggregator Pattern: Simple aggregation entry point")
            .log("📊 Starting simple aggregation for event: ${header.eventType}")
            
            .process(exchange -> {
                String eventType = exchange.getIn().getHeader("eventType", String.class);
                LocalDateTime timestamp = LocalDateTime.now();
                
                exchange.getIn().setHeader("aggregationId", 
                    java.util.UUID.randomUUID().toString());
                exchange.getIn().setHeader("aggregationTimestamp", timestamp);
                
                log.info("📊 Simple aggregation initiated for event {}", eventType);
            })
            
            .to("direct:business-metric-aggregator-simple")
            .log("✅ Simple aggregation processing completed");

        /**
         * Route 2: Business Metric Aggregator
         */
        from("direct:business-metric-aggregator-simple")
            .routeId("business-metric-aggregator-simple")
            .description("Aggregator Pattern: Simple business metrics aggregation")
            .log("💼 Processing simple business metric aggregations")
            
            .choice()
                .when(header("eventType").startsWith("ORDER_"))
                    .to("direct:order-metrics-aggregator-simple")
                .when(header("eventType").startsWith("USER_"))
                    .to("direct:user-metrics-aggregator-simple")
                .otherwise()
                    .to("direct:generic-metrics-aggregator-simple")
            .end()
            
            .log("✅ Simple business metric aggregations processed");

        /**
         * Route 3: Order Metrics Aggregator
         */
        from("direct:order-metrics-aggregator-simple")
            .routeId("order-metrics-aggregator-simple")
            .description("Aggregator Pattern: Simple order metrics aggregation")
            .log("🛒 Processing simple order metrics aggregation")
            
            .process(exchange -> {
                String eventType = exchange.getIn().getHeader("eventType", String.class);
                
                updateAggregationCounter("orders.total");
                
                if ("ORDER_CREATED".equals(eventType)) {
                    updateAggregationCounter("orders.created");
                } else if ("ORDER_PAID".equals(eventType)) {
                    updateAggregationCounter("orders.paid");
                }
                
                Map<String, Object> orderMetrics = new HashMap<>();
                orderMetrics.put("eventType", eventType);
                orderMetrics.put("totalOrders", getAggregationCount("orders.total"));
                orderMetrics.put("aggregationTimestamp", LocalDateTime.now());
                
                exchange.getIn().setBody(orderMetrics);
                
                log.info("🛒 Simple order aggregation: {} - Total Orders: {}", 
                        eventType, getAggregationCount("orders.total"));
            })
            
            .log("✅ Simple order metrics aggregation completed");

        /**
         * Route 4: User Metrics Aggregator
         */
        from("direct:user-metrics-aggregator-simple")
            .routeId("user-metrics-aggregator-simple")
            .description("Aggregator Pattern: Simple user metrics aggregation")
            .log("👤 Processing simple user metrics aggregation")
            
            .process(exchange -> {
                String eventType = exchange.getIn().getHeader("eventType", String.class);
                
                updateAggregationCounter("users.total");
                
                if ("USER_REGISTERED".equals(eventType)) {
                    updateAggregationCounter("users.registered");
                } else if ("USER_LOGIN".equals(eventType)) {
                    updateAggregationCounter("users.logins");
                }
                
                Map<String, Object> userMetrics = new HashMap<>();
                userMetrics.put("eventType", eventType);
                userMetrics.put("totalUsers", getAggregationCount("users.total"));
                userMetrics.put("aggregationTimestamp", LocalDateTime.now());
                
                exchange.getIn().setBody(userMetrics);
                
                log.info("👤 Simple user aggregation: {} - Total Users: {}", 
                        eventType, getAggregationCount("users.total"));
            })
            
            .log("✅ Simple user metrics aggregation completed");

        /**
         * Dead Letter Channel
         */
        from("direct:aggregator-dead-letter-simple")
            .routeId("aggregator-dead-letter-simple")
            .description("Aggregator Pattern: Simple dead letter channel")
            .log(LoggingLevel.ERROR, "💀 Simple aggregation failed: ${exception.message}")
            .log("💾 Failed simple aggregation logged");

        // Mock processors
        from("direct:generic-metrics-aggregator-simple")
            .routeId("generic-metrics-aggregator-simple")
            .log("🔧 Mock: Simple generic metrics aggregator - ${body}");
    }

    // Helper methods
    private void updateAggregationCounter(String key) {
        aggregationCounters.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
    }

    private long getAggregationCount(String key) {
        return aggregationCounters.getOrDefault(key, new AtomicLong(0)).get();
    }
} 