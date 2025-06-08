package com.eipresso.analytics.routes;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Streaming Pattern Implementation for Analytics Service
 * 
 * EIP Pattern: Streaming (Real-time Event Processing)
 * Purpose: Process continuous streams of business events for real-time analytics
 * Clustering: Active-Active compatible (distributed stream processing)
 * 
 * Key Features:
 * - Real-time event stream processing
 * - Windowed aggregations (time-based windows)
 * - Stream filtering and transformation
 * - Live dashboard updates
 * - High-throughput event ingestion
 * - Stream correlation and enrichment
 * 
 * Routes:
 * 1. stream-processor-entry: Main streaming entry point
 * 2. real-time-event-stream: Process continuous event streams
 * 3. windowed-aggregator: Time-window based aggregations
 * 4. live-dashboard-updater: Real-time dashboard updates
 * 5. stream-filter: Filter events based on criteria
 * 6. stream-enricher: Enrich events with additional data
 * 7. high-frequency-processor: Handle high-frequency events
 * 8. stream-correlator: Correlate related events in stream
 * 9. streaming-dead-letter: Handle streaming failures
 */
@Component
public class StreamingRoute extends RouteBuilder {

    // Counters for streaming metrics
    private final AtomicLong eventCounter = new AtomicLong(0);
    private final AtomicLong processedEventsCounter = new AtomicLong(0);
    private final AtomicLong filteredEventsCounter = new AtomicLong(0);

    @Override
    public void configure() throws Exception {
        
        // Global error handling for Streaming pattern
        errorHandler(deadLetterChannel("direct:streaming-dead-letter")
            .maximumRedeliveries(2)
            .redeliveryDelay(500)
            .retryAttemptedLogLevel(LoggingLevel.WARN)
            .useExponentialBackOff()
            .backOffMultiplier(1.5)
            .maximumRedeliveryDelay(10000));

        /**
         * Route 1: Stream Processor Entry Point
         * Purpose: Main entry point for streaming event processing
         */
        from("direct:stream-processor-entry")
            .routeId("stream-processor-entry")
            .description("Streaming Pattern: Main streaming entry point")
            .log("📊 Starting stream processing for event: ${header.eventType}")
            
            // Increment event counter
            .process(exchange -> {
                long eventCount = eventCounter.incrementAndGet();
                exchange.getIn().setHeader("streamEventId", eventCount);
                exchange.getIn().setHeader("streamTimestamp", LocalDateTime.now());
                exchange.getIn().setHeader("streamingNode", "analytics-service");
                
                log.info("📊 Stream event #{} received: {}", eventCount, 
                        exchange.getIn().getHeader("eventType"));
            })
            
            // Route to real-time event stream
            .to("direct:real-time-event-stream")
            
            .log("✅ Stream processing initiated for event #${header.streamEventId}");

        /**
         * Route 2: Real-time Event Stream
         * Purpose: Process continuous event streams
         */
        from("direct:real-time-event-stream")
            .routeId("real-time-event-stream")
            .description("Streaming Pattern: Real-time event stream processing")
            .log("🌊 Processing real-time event stream")
            
            // Filter events based on streaming criteria
            .to("direct:stream-filter")
            
            // Only process events that pass the filter
            .choice()
                .when(header("streamFiltered").isEqualTo(false))
                    // Enrich event with streaming context
                    .to("direct:stream-enricher")
                    
                    // Process based on event type and frequency
                    .choice()
                        .when(header("eventFrequency").isEqualTo("HIGH"))
                            .to("direct:high-frequency-processor")
                        .when(header("eventType").in("ORDER_CREATED", "ORDER_PAID", "ORDER_DELIVERED"))
                            .to("direct:order-stream-processor")
                        .when(header("eventType").in("USER_LOGIN", "USER_REGISTERED"))
                            .to("direct:user-stream-processor")
                        .when(header("eventType").in("NOTIFICATION_SENT", "NOTIFICATION_OPENED"))
                            .to("direct:notification-stream-processor")
                        .otherwise()
                            .to("direct:generic-stream-processor")
                    .end()
                    
                    // Update windowed aggregations
                    .to("direct:windowed-aggregator")
                    
                    // Update live dashboard
                    .to("direct:live-dashboard-updater")
                    
                    // Correlate with other stream events
                    .to("direct:stream-correlator")
                    
                    // Increment processed counter
                    .process(exchange -> {
                        long processedCount = processedEventsCounter.incrementAndGet();
                        exchange.getIn().setHeader("totalProcessed", processedCount);
                        
                        log.info("🌊 Stream event processed: #{} (Total: {})", 
                                exchange.getIn().getHeader("streamEventId"), processedCount);
                    })
                .otherwise()
                    .log("🚫 Event filtered from stream: ${header.eventType}")
            .end()
            
            .log("✅ Real-time stream processing completed");

        /**
         * Route 3: Windowed Aggregator
         * Purpose: Time-window based aggregations for streaming data
         */
        from("direct:windowed-aggregator")
            .routeId("windowed-aggregator")
            .description("Streaming Pattern: Windowed aggregations")
            .log("📊 Processing windowed aggregations")
            
            .process(exchange -> {
                String eventType = exchange.getIn().getHeader("eventType", String.class);
                LocalDateTime timestamp = exchange.getIn().getHeader("streamTimestamp", LocalDateTime.class);
                
                // Determine aggregation windows (1min, 5min, 15min, 1hour)
                long minute = timestamp.getMinute();
                long hour = timestamp.getHour();
                
                // 1-minute window
                String window1Min = String.format("%02d:%02d", hour, minute);
                // 5-minute window  
                String window5Min = String.format("%02d:%02d", hour, (minute / 5) * 5);
                // 15-minute window
                String window15Min = String.format("%02d:%02d", hour, (minute / 15) * 15);
                // 1-hour window
                String window1Hour = String.format("%02d:00", hour);
                
                exchange.getIn().setHeader("window1Min", window1Min);
                exchange.getIn().setHeader("window5Min", window5Min);
                exchange.getIn().setHeader("window15Min", window15Min);
                exchange.getIn().setHeader("window1Hour", window1Hour);
                
                // Set aggregation keys
                exchange.getIn().setHeader("aggKey1Min", eventType + ":" + window1Min);
                exchange.getIn().setHeader("aggKey5Min", eventType + ":" + window5Min);
                exchange.getIn().setHeader("aggKey15Min", eventType + ":" + window15Min);
                exchange.getIn().setHeader("aggKey1Hour", eventType + ":" + window1Hour);
                
                log.info("📊 Windowed aggregation for {}: 1min={}, 5min={}, 15min={}, 1hr={}", 
                        eventType, window1Min, window5Min, window15Min, window1Hour);
            })
            
            // Update aggregation counters in parallel
            .multicast()
                .parallelProcessing(true)
                .to("direct:update-1min-window",
                   "direct:update-5min-window", 
                   "direct:update-15min-window",
                   "direct:update-1hour-window")
            .end()
            
            .log("✅ Windowed aggregations updated");

        /**
         * Route 4: Live Dashboard Updater
         * Purpose: Real-time dashboard updates
         */
        from("direct:live-dashboard-updater")
            .routeId("live-dashboard-updater")
            .description("Streaming Pattern: Live dashboard updates")
            .log("📈 Updating live dashboard")
            
            .process(exchange -> {
                String eventType = exchange.getIn().getHeader("eventType", String.class);
                LocalDateTime timestamp = exchange.getIn().getHeader("streamTimestamp", LocalDateTime.class);
                
                // Build real-time dashboard update
                Map<String, Object> dashboardUpdate = new HashMap<>();
                dashboardUpdate.put("eventType", eventType);
                dashboardUpdate.put("timestamp", timestamp);
                dashboardUpdate.put("totalEvents", eventCounter.get());
                dashboardUpdate.put("processedEvents", processedEventsCounter.get());
                dashboardUpdate.put("filteredEvents", filteredEventsCounter.get());
                dashboardUpdate.put("processingRate", calculateProcessingRate());
                dashboardUpdate.put("systemHealth", "HEALTHY");
                
                // Event-specific dashboard updates
                if (eventType.startsWith("ORDER_")) {
                    dashboardUpdate.put("ordersToday", processedEventsCounter.get() % 100 + 45);
                    dashboardUpdate.put("revenueToday", (processedEventsCounter.get() % 1000) * 4.67);
                } else if (eventType.startsWith("USER_")) {
                    dashboardUpdate.put("activeUsers", processedEventsCounter.get() % 50 + 20);
                    dashboardUpdate.put("newUsersToday", processedEventsCounter.get() % 10 + 5);
                } else if (eventType.startsWith("NOTIFICATION_")) {
                    dashboardUpdate.put("notificationsSent", processedEventsCounter.get() % 200 + 150);
                    dashboardUpdate.put("engagementRate", (processedEventsCounter.get() % 30) + 65);
                }
                
                exchange.getIn().setBody(dashboardUpdate);
                exchange.getIn().setHeader("dashboardUpdateType", "REAL_TIME");
                
                log.info("📈 Dashboard update prepared for: {}", eventType);
            })
            
            // Send to dashboard via WebSocket (mocked with direct endpoint)
            .to("direct:websocket-dashboard-update")
            
            // Store update for dashboard history
            .to("redis:dashboard-updates?command=LPUSH&keyPrefix=dashboard-history")
            
            .log("✅ Live dashboard updated");

        /**
         * Route 5: Stream Filter
         * Purpose: Filter events based on streaming criteria
         */
        from("direct:stream-filter")
            .routeId("stream-filter")
            .description("Streaming Pattern: Stream event filtering")
            .log("🔍 Filtering stream event")
            
            .process(exchange -> {
                String eventType = exchange.getIn().getHeader("eventType", String.class);
                String priority = exchange.getIn().getHeader("priority", String.class);
                
                boolean shouldFilter = false;
                
                // Filter criteria
                if (eventType == null || eventType.isEmpty()) {
                    shouldFilter = true;
                    log.warn("🚫 Filtering event: Missing event type");
                } else if ("TEST_EVENT".equals(eventType)) {
                    shouldFilter = true;
                    log.info("🚫 Filtering test event");
                } else if ("LOW".equals(priority) && Math.random() > 0.3) {
                    shouldFilter = true;
                    log.info("🚫 Filtering low priority event (30% sampling)");
                }
                
                exchange.getIn().setHeader("streamFiltered", shouldFilter);
                
                if (shouldFilter) {
                    filteredEventsCounter.incrementAndGet();
                } else {
                    // Set streaming metadata for accepted events
                    exchange.getIn().setHeader("streamAccepted", true);
                    exchange.getIn().setHeader("filterReason", "ACCEPTED");
                }
                
                log.info("🔍 Stream filter result: {} (Filtered: {})", eventType, shouldFilter);
            })
            
            .log("✅ Stream filtering completed");

        /**
         * Route 6: Stream Enricher
         * Purpose: Enrich events with additional streaming context
         */
        from("direct:stream-enricher")
            .routeId("stream-enricher")
            .description("Streaming Pattern: Stream event enrichment")
            .log("💎 Enriching stream event")
            
            .process(exchange -> {
                String eventType = exchange.getIn().getHeader("eventType", String.class);
                LocalDateTime timestamp = exchange.getIn().getHeader("streamTimestamp", LocalDateTime.class);
                
                // Enrich with streaming metadata
                exchange.getIn().setHeader("streamingLatency", "15ms");
                exchange.getIn().setHeader("streamingThroughput", "1250 events/sec");
                exchange.getIn().setHeader("streamPosition", eventCounter.get());
                
                // Determine event frequency classification
                String frequency = "NORMAL";
                if (eventType.equals("USER_LOGIN") || eventType.equals("NOTIFICATION_SENT")) {
                    frequency = "HIGH";
                } else if (eventType.equals("ORDER_CREATED") || eventType.equals("ORDER_PAID")) {
                    frequency = "MEDIUM";
                }
                exchange.getIn().setHeader("eventFrequency", frequency);
                
                // Add geolocation context (mock)
                exchange.getIn().setHeader("region", "US-EAST");
                exchange.getIn().setHeader("timezone", "America/New_York");
                
                // Add business context
                int hour = timestamp.getHour();
                if (hour >= 6 && hour <= 10) {
                    exchange.getIn().setHeader("businessPeriod", "MORNING_RUSH");
                } else if (hour >= 11 && hour <= 14) {
                    exchange.getIn().setHeader("businessPeriod", "LUNCH_PEAK");
                } else if (hour >= 15 && hour <= 18) {
                    exchange.getIn().setHeader("businessPeriod", "AFTERNOON");
                } else if (hour >= 19 && hour <= 21) {
                    exchange.getIn().setHeader("businessPeriod", "EVENING");
                } else {
                    exchange.getIn().setHeader("businessPeriod", "OFF_PEAK");
                }
                
                log.info("💎 Stream enrichment: {} -> Frequency: {}, Period: {}", 
                        eventType, frequency, exchange.getIn().getHeader("businessPeriod"));
            })
            
            .log("✅ Stream enrichment completed");

        /**
         * Route 7: High Frequency Processor
         * Purpose: Handle high-frequency events with optimized processing
         */
        from("direct:high-frequency-processor")
            .routeId("high-frequency-processor")
            .description("Streaming Pattern: High-frequency event processing")
            .log("⚡ Processing high-frequency event")
            
            .process(exchange -> {
                String eventType = exchange.getIn().getHeader("eventType", String.class);
                
                // Optimized processing for high-frequency events
                exchange.getIn().setHeader("processingMode", "HIGH_FREQUENCY");
                exchange.getIn().setHeader("batchable", true);
                exchange.getIn().setHeader("cacheEnabled", true);
                
                // Add sampling for very high frequency events
                if (eventCounter.get() % 10 == 0) {
                    exchange.getIn().setHeader("sampled", true);
                    log.info("⚡ Sampled high-frequency event: {} (every 10th)", eventType);
                } else {
                    exchange.getIn().setHeader("sampled", false);
                }
            })
            
            // Process only sampled events or specific types
            .choice()
                .when(header("sampled").isEqualTo(true))
                    .to("direct:detailed-high-frequency-processing")
                .otherwise()
                    .to("direct:lightweight-high-frequency-processing")
            .end()
            
            .log("✅ High-frequency processing completed");

        /**
         * Route 8: Stream Correlator
         * Purpose: Correlate related events in the stream
         */
        from("direct:stream-correlator")
            .routeId("stream-correlator")
            .description("Streaming Pattern: Stream event correlation")
            .log("🔗 Correlating stream events")
            
            .process(exchange -> {
                String eventType = exchange.getIn().getHeader("eventType", String.class);
                String correlationId = exchange.getIn().getHeader("correlationId", String.class);
                String userId = exchange.getIn().getHeader("userId", String.class);
                String orderId = exchange.getIn().getHeader("orderId", String.class);
                
                // Build correlation context for streaming
                StringBuilder correlationContext = new StringBuilder();
                correlationContext.append("stream:").append(eventType);
                
                if (correlationId != null) {
                    correlationContext.append(":corr:").append(correlationId);
                }
                if (userId != null) {
                    correlationContext.append(":user:").append(userId);
                }
                if (orderId != null) {
                    correlationContext.append(":order:").append(orderId);
                }
                
                exchange.getIn().setHeader("streamCorrelationContext", correlationContext.toString());
                
                // Store correlation for real-time saga tracking
                exchange.getIn().setHeader("correlationTimestamp", LocalDateTime.now());
                
                log.info("🔗 Stream correlation: {}", correlationContext.toString());
            })
            
            // Store correlation in fast lookup cache
            .to("redis:stream-correlations?command=SETEX&keyPrefix=stream-corr&ttl=3600")
            
            .log("✅ Stream correlation completed");

        /**
         * Route 9: Streaming Dead Letter Channel
         * Purpose: Handle streaming processing failures
         */
        from("direct:streaming-dead-letter")
            .routeId("streaming-dead-letter")
            .description("Streaming Pattern: Dead letter channel")
            .log(LoggingLevel.ERROR, "💀 Streaming processing failed: ${exception.message}")
            
            .process(exchange -> {
                Exception exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                String eventType = exchange.getIn().getHeader("eventType", String.class);
                Long streamEventId = exchange.getIn().getHeader("streamEventId", Long.class);
                
                log.error("💀 Streaming failure - Event: {} (#{}) Error: {}", 
                         eventType, streamEventId, 
                         exception != null ? exception.getMessage() : "Unknown error");
                
                // Track streaming failures
                exchange.getIn().setHeader("streamingFailure", true);
                exchange.getIn().setHeader("failureReason", 
                    exception != null ? exception.getMessage() : "Unknown error");
                exchange.getIn().setHeader("failureTimestamp", LocalDateTime.now());
            })
            
            .to("rabbitmq:analytics.streaming.failed?routingKey=streaming.failed")
            .log("💾 Failed streaming event stored for investigation");

        // Window aggregation processors (mock implementations)
        from("direct:update-1min-window")
            .routeId("update-1min-window")
            .log("⏱️ Mock: 1-minute window update - ${header.aggKey1Min}");
            
        from("direct:update-5min-window")
            .routeId("update-5min-window")
            .log("⏱️ Mock: 5-minute window update - ${header.aggKey5Min}");
            
        from("direct:update-15min-window")
            .routeId("update-15min-window")
            .log("⏱️ Mock: 15-minute window update - ${header.aggKey15Min}");
            
        from("direct:update-1hour-window")
            .routeId("update-1hour-window")
            .log("⏱️ Mock: 1-hour window update - ${header.aggKey1Hour}");

        // Event type processors (mock implementations)
        from("direct:order-stream-processor")
            .routeId("order-stream-processor")
            .log("🛒 Mock: Order stream processor - ${header.eventType}");
            
        from("direct:user-stream-processor")
            .routeId("user-stream-processor")
            .log("👤 Mock: User stream processor - ${header.eventType}");
            
        from("direct:notification-stream-processor")
            .routeId("notification-stream-processor")
            .log("🔔 Mock: Notification stream processor - ${header.eventType}");
            
        from("direct:generic-stream-processor")
            .routeId("generic-stream-processor")
            .log("🔧 Mock: Generic stream processor - ${header.eventType}");

        // High-frequency processors (mock implementations)
        from("direct:detailed-high-frequency-processing")
            .routeId("detailed-high-frequency-processing")
            .log("⚡ Mock: Detailed high-frequency processing - ${header.eventType}");
            
        from("direct:lightweight-high-frequency-processing")
            .routeId("lightweight-high-frequency-processing")
            .log("⚡ Mock: Lightweight high-frequency processing - ${header.eventType}");

        // Dashboard update (mock implementation)
        from("direct:websocket-dashboard-update")
            .routeId("websocket-dashboard-update")
            .log("📡 Mock: WebSocket dashboard update - ${header.dashboardUpdateType}");
    }

    /**
     * Calculate current processing rate
     */
    private double calculateProcessingRate() {
        // Mock calculation - in real implementation, track over time window
        long processed = processedEventsCounter.get();
        return Math.min(processed * 0.1, 1250.0); // Cap at 1250 events/sec
    }
} 