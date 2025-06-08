package com.eipresso.analytics.routes;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Streaming Pattern Implementation for Analytics Service
 * 
 * EIP Pattern: Streaming Pattern
 * Purpose: Real-time event processing for live dashboards and instant analytics
 * Clustering: Active-Active compatible (distributed stream processing)
 * 
 * Routes:
 * 1. streaming-entry: Main streaming entry point
 * 2. real-time-processor: Process events in real-time
 * 3. live-dashboard-streamer: Stream data to live dashboards
 * 4. event-window-processor: Time-window based processing
 * 5. real-time-aggregator: Real-time metrics aggregation
 * 6. streaming-analytics-processor: Streaming analytics calculations
 */
@Component
public class StreamingRoute extends RouteBuilder {

    // Real-time metrics storage
    private final Map<String, AtomicLong> realtimeCounters = new ConcurrentHashMap<>();
    private final Map<String, Double> realtimeSums = new ConcurrentHashMap<>();
    private final Map<String, Object> liveMetrics = new ConcurrentHashMap<>();

    @Override
    public void configure() throws Exception {
        
        // Global error handling for Streaming pattern
        errorHandler(deadLetterChannel("direct:streaming-dead-letter")
            .maximumRedeliveries(3)
            .redeliveryDelay(500)
            .retryAttemptedLogLevel(LoggingLevel.WARN)
            .useExponentialBackOff()
            .backOffMultiplier(1.5)
            .maximumRedeliveryDelay(10000));

        /**
         * Route 1: Streaming Entry Point
         */
        from("direct:streaming-entry")
            .routeId("streaming-entry")
            .description("Streaming Pattern: Main streaming entry point")
            .log("🌊 Starting real-time stream processing: ${header.eventType}")
            
            .process(exchange -> {
                String streamId = UUID.randomUUID().toString();
                exchange.getIn().setHeader("streamId", streamId);
                exchange.getIn().setHeader("streamTimestamp", LocalDateTime.now());
                exchange.getIn().setHeader("processingMode", "REAL_TIME");
                
                String eventType = exchange.getIn().getHeader("eventType", String.class);
                exchange.getIn().setHeader("streamingEnabled", true);
                
                log.info("🌊 Stream processing initiated: {} [{}]", eventType, streamId);
            })
            
            .to("direct:real-time-processor")
            .to("direct:live-dashboard-streamer")
            .log("✅ Stream processing completed for ${header.streamId}");

        /**
         * Route 2: Real-Time Processor
         */
        from("direct:real-time-processor")
            .routeId("real-time-processor")
            .description("Streaming Pattern: Real-time event processing")
            .log("⚡ Processing event in real-time: ${header.eventType}")
            
            .process(exchange -> {
                String eventType = exchange.getIn().getHeader("eventType", String.class);
                LocalDateTime processingTime = LocalDateTime.now();
                
                // Update real-time counters
                updateRealtimeCounter("events.total");
                updateRealtimeCounter("events." + eventType.toLowerCase());
                
                exchange.getIn().setHeader("realtimeProcessed", true);
                exchange.getIn().setHeader("processingLatency", System.currentTimeMillis());
                
                log.info("⚡ Real-time processing: {} - Total Events: {}", 
                        eventType, getRealtimeCount("events.total"));
            })
            
            .multicast()
                .parallelProcessing(true)
                .to("direct:event-window-processor",
                   "direct:real-time-aggregator",
                   "direct:streaming-analytics-processor")
            .end()
            
            .log("✅ Real-time processing completed");

        /**
         * Route 3: Live Dashboard Streamer
         */
        from("direct:live-dashboard-streamer")
            .routeId("live-dashboard-streamer")
            .description("Streaming Pattern: Stream data to live dashboards")
            .log("📊 Streaming to live dashboards")
            
            .process(exchange -> {
                String eventType = exchange.getIn().getHeader("eventType", String.class);
                
                Map<String, Object> dashboardData = new HashMap<>();
                dashboardData.put("eventType", eventType);
                dashboardData.put("timestamp", LocalDateTime.now());
                dashboardData.put("totalEvents", getRealtimeCount("events.total"));
                dashboardData.put("eventTypeCount", getRealtimeCount("events." + eventType.toLowerCase()));
                dashboardData.put("streamingMode", "LIVE");
                
                // Store live metrics for dashboard consumption
                liveMetrics.put("latest_event", dashboardData);
                liveMetrics.put("last_update", LocalDateTime.now());
                
                exchange.getIn().setBody(dashboardData);
                
                log.info("📊 Dashboard streaming: {} - Live Update", eventType);
            })
            
            .choice()
                .when(header("eventType").startsWith("ORDER_"))
                    .to("direct:order-dashboard-streamer")
                .when(header("eventType").startsWith("USER_"))
                    .to("direct:user-dashboard-streamer")
                .otherwise()
                    .to("direct:generic-dashboard-streamer")
            .end()
            
            .log("✅ Live dashboard streaming completed");

        /**
         * Route 4: Event Window Processor
         */
        from("direct:event-window-processor")
            .routeId("event-window-processor")
            .description("Streaming Pattern: Time-window based processing")
            .log("⏰ Processing time-window analytics")
            
            .process(exchange -> {
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime windowStart = now.minusMinutes(5); // 5-minute window
                
                exchange.getIn().setHeader("windowStart", windowStart);
                exchange.getIn().setHeader("windowEnd", now);
                exchange.getIn().setHeader("windowSize", "5_MINUTES");
                
                // Calculate window metrics
                long windowEventCount = getRealtimeCount("events.total");
                double eventRate = windowEventCount / 5.0; // events per minute
                
                Map<String, Object> windowMetrics = new HashMap<>();
                windowMetrics.put("windowStart", windowStart);
                windowMetrics.put("windowEnd", now);
                windowMetrics.put("eventCount", windowEventCount);
                windowMetrics.put("eventRate", eventRate);
                
                exchange.getIn().setBody(windowMetrics);
                
                log.info("⏰ Window metrics: {} events, {} per minute", 
                        windowEventCount, String.format("%.2f", eventRate));
            })
            
            .to("direct:window-analytics-aggregator")
            .log("✅ Time-window processing completed");

        /**
         * Route 5: Real-Time Aggregator
         */
        from("direct:real-time-aggregator")
            .routeId("real-time-aggregator")
            .description("Streaming Pattern: Real-time metrics aggregation")
            .log("📊 Real-time aggregation processing")
            
            .process(exchange -> {
                String eventType = exchange.getIn().getHeader("eventType", String.class);
                
                Map<String, Object> realtimeAggregation = new HashMap<>();
                realtimeAggregation.put("eventType", eventType);
                realtimeAggregation.put("aggregationTimestamp", LocalDateTime.now());
                realtimeAggregation.put("totalEvents", getRealtimeCount("events.total"));
                
                // Event type specific aggregations
                if (eventType.startsWith("ORDER_")) {
                    realtimeAggregation.put("orderEvents", getRealtimeCount("events.order_"));
                    realtimeAggregation.put("category", "order");
                } else if (eventType.startsWith("USER_")) {
                    realtimeAggregation.put("userEvents", getRealtimeCount("events.user_"));
                    realtimeAggregation.put("category", "user");
                }
                
                exchange.getIn().setBody(realtimeAggregation);
                
                log.info("📊 Real-time aggregation: {} - {}", 
                        eventType, realtimeAggregation.get("category"));
            })
            
            .to("direct:aggregation-stream-publisher")
            .log("✅ Real-time aggregation completed");

        /**
         * Route 6: Streaming Analytics Processor
         */
        from("direct:streaming-analytics-processor")
            .routeId("streaming-analytics-processor")
            .description("Streaming Pattern: Streaming analytics calculations")
            .log("🧮 Processing streaming analytics calculations")
            
            .process(exchange -> {
                String eventType = exchange.getIn().getHeader("eventType", String.class);
                LocalDateTime timestamp = LocalDateTime.now();
                
                // Calculate streaming analytics
                Map<String, Object> streamingAnalytics = new HashMap<>();
                streamingAnalytics.put("eventType", eventType);
                streamingAnalytics.put("calculationTimestamp", timestamp);
                
                // Performance metrics
                long totalEvents = getRealtimeCount("events.total");
                streamingAnalytics.put("totalEvents", totalEvents);
                streamingAnalytics.put("processingThroughput", calculateThroughput());
                streamingAnalytics.put("systemLoad", calculateSystemLoad());
                
                // Business metrics
                if (eventType.contains("ORDER")) {
                    streamingAnalytics.put("orderVelocity", calculateOrderVelocity());
                    streamingAnalytics.put("revenueRate", calculateRevenueRate());
                }
                
                exchange.getIn().setBody(streamingAnalytics);
                
                log.info("🧮 Streaming analytics: {} - Throughput: {}", 
                        eventType, streamingAnalytics.get("processingThroughput"));
            })
            
            .to("direct:analytics-stream-publisher")
            .log("✅ Streaming analytics processing completed");

        // Specialized Dashboard Streamers
        from("direct:order-dashboard-streamer")
            .routeId("order-dashboard-streamer")
            .description("Streaming: Order dashboard streaming")
            .log("🛒 Streaming order data to dashboard")
            .process(exchange -> {
                Map<String, Object> orderDashboard = new HashMap<>();
                orderDashboard.put("category", "order");
                orderDashboard.put("liveOrderCount", getRealtimeCount("events.order_"));
                orderDashboard.put("updateTimestamp", LocalDateTime.now());
                exchange.getIn().setBody(orderDashboard);
            })
            .log("✅ Order dashboard streaming completed");

        from("direct:user-dashboard-streamer")
            .routeId("user-dashboard-streamer")
            .description("Streaming: User dashboard streaming")
            .log("👤 Streaming user data to dashboard")
            .process(exchange -> {
                Map<String, Object> userDashboard = new HashMap<>();
                userDashboard.put("category", "user");
                userDashboard.put("liveUserActivity", getRealtimeCount("events.user_"));
                userDashboard.put("updateTimestamp", LocalDateTime.now());
                exchange.getIn().setBody(userDashboard);
            })
            .log("✅ User dashboard streaming completed");

        from("direct:generic-dashboard-streamer")
            .routeId("generic-dashboard-streamer")
            .description("Streaming: Generic dashboard streaming")
            .log("🔧 Streaming generic data to dashboard")
            .log("✅ Generic dashboard streaming completed");

        // Stream Publishers
        from("direct:aggregation-stream-publisher")
            .routeId("aggregation-stream-publisher")
            .description("Streaming: Publish aggregation streams")
            .log("📡 Publishing aggregation stream")
            .to("rabbitmq:aggregation-stream?exchangeType=topic&routingKey=analytics.aggregation")
            .log("✅ Aggregation stream published");

        from("direct:analytics-stream-publisher")
            .routeId("analytics-stream-publisher")
            .description("Streaming: Publish analytics streams")
            .log("📡 Publishing analytics stream")
            .to("rabbitmq:analytics-stream?exchangeType=topic&routingKey=analytics.streaming")
            .log("✅ Analytics stream published");

        from("direct:window-analytics-aggregator")
            .routeId("window-analytics-aggregator")
            .description("Streaming: Window analytics aggregation")
            .log("📊 Processing window analytics aggregation")
            .log("✅ Window analytics aggregation completed");

        /**
         * Timer-based Stream Processing (Every 30 seconds)
         */
        from("timer:streaming-metrics?period=30000")
            .routeId("streaming-metrics-timer")
            .description("Streaming Pattern: Periodic metrics streaming")
            .log("⏱️ Publishing periodic streaming metrics")
            
            .process(exchange -> {
                Map<String, Object> periodicMetrics = new HashMap<>();
                periodicMetrics.put("timestamp", LocalDateTime.now());
                periodicMetrics.put("totalEvents", getRealtimeCount("events.total"));
                periodicMetrics.put("systemThroughput", calculateThroughput());
                periodicMetrics.put("activeStreams", realtimeCounters.size());
                
                exchange.getIn().setBody(periodicMetrics);
                
                log.info("⏱️ Periodic metrics: {} total events, {} streams active", 
                        periodicMetrics.get("totalEvents"), periodicMetrics.get("activeStreams"));
            })
            
            .to("direct:live-dashboard-streamer")
            .log("✅ Periodic streaming metrics published");

        /**
         * Dead Letter Channel
         */
        from("direct:streaming-dead-letter")
            .routeId("streaming-dead-letter")
            .description("Streaming Pattern: Dead letter channel")
            .log(LoggingLevel.ERROR, "💀 Streaming processing failed: ${exception.message}")
            .process(exchange -> {
                String failureId = UUID.randomUUID().toString();
                exchange.getIn().setHeader("failureId", failureId);
                exchange.getIn().setHeader("failureTimestamp", LocalDateTime.now());
                log.error("💀 Streaming failure logged: {}", failureId);
            })
            .log("💾 Streaming failure logged for analysis");
    }

    // Helper methods for real-time metrics
    private void updateRealtimeCounter(String key) {
        realtimeCounters.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
    }

    private long getRealtimeCount(String key) {
        return realtimeCounters.getOrDefault(key, new AtomicLong(0)).get();
    }

    private double calculateThroughput() {
        return getRealtimeCount("events.total") / 60.0; // events per second (simplified)
    }

    private double calculateSystemLoad() {
        return Math.min(calculateThroughput() / 100.0, 1.0); // Simple load calculation
    }

    private double calculateOrderVelocity() {
        return getRealtimeCount("events.order_") / 3600.0; // orders per hour
    }

    private double calculateRevenueRate() {
        return calculateOrderVelocity() * 25.0; // Assume $25 average order
    }
} 