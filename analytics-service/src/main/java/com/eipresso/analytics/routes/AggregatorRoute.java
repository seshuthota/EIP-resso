package com.eipresso.analytics.routes;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Aggregator Pattern Implementation for Analytics Service
 * 
 * EIP Pattern: Aggregator
 * Purpose: Time-window based metrics aggregation for analytical processing
 * Clustering: Active-Active compatible (distributed aggregation)
 * 
 * Key Features:
 * - Time-window based aggregations (1min, 5min, 15min, 1hour, 1day)
 * - Metric type aggregations (count, sum, average, min, max)
 * - Business domain aggregations (orders, users, revenue, notifications)
 * - Correlation-based aggregations for saga tracking
 * - Real-time aggregation updates
 * - Aggregation completion strategies
 * 
 * Routes:
 * 1. aggregator-entry: Main aggregation entry point
 * 2. time-window-aggregator: Time-based aggregations
 * 3. business-metric-aggregator: Business metrics aggregation
 * 4. correlation-aggregator: Correlation-based aggregations
 * 5. completion-checker: Check aggregation completion
 * 6. aggregation-publisher: Publish completed aggregations
 * 7. order-metrics-aggregator: Order-specific aggregations
 * 8. user-metrics-aggregator: User behavior aggregations
 * 9. revenue-aggregator: Revenue calculations
 * 10. notification-metrics-aggregator: Notification statistics
 */
@Component
public class AggregatorRoute extends RouteBuilder {

    // Aggregation counters and state
    private final Map<String, AtomicLong> aggregationCounters = new ConcurrentHashMap<>();
    private final Map<String, Double> aggregationSums = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> aggregationStartTimes = new ConcurrentHashMap<>();

    @Override
    public void configure() throws Exception {
        
        // Global error handling for Aggregator pattern
        errorHandler(deadLetterChannel("direct:aggregator-dead-letter")
            .maximumRedeliveries(3)
            .redeliveryDelay(1000)
            .retryAttemptedLogLevel(LoggingLevel.WARN)
            .useExponentialBackOff()
            .backOffMultiplier(2)
            .maximumRedeliveryDelay(30000));

        /**
         * Route 1: Aggregator Entry Point
         * Purpose: Main entry point for all aggregation operations
         */
        from("direct:aggregator-entry")
            .routeId("aggregator-entry")
            .description("Aggregator Pattern: Main aggregation entry point")
            .log("📊 Starting aggregation for event: ${header.eventType}")
            
            // Set aggregation metadata
            .process(exchange -> {
                String eventType = exchange.getIn().getHeader("eventType", String.class);
                String correlationId = exchange.getIn().getHeader("correlationId", String.class);
                LocalDateTime timestamp = LocalDateTime.now();
                
                exchange.getIn().setHeader("aggregationId", 
                    java.util.UUID.randomUUID().toString());
                exchange.getIn().setHeader("aggregationTimestamp", timestamp);
                exchange.getIn().setHeader("aggregationSource", "analytics-service");
                
                // Initialize aggregation tracking
                String aggregationKey = buildAggregationKey(eventType, timestamp);
                exchange.getIn().setHeader("aggregationKey", aggregationKey);
                
                log.info("📊 Aggregation initiated: {} for event {}", aggregationKey, eventType);
            })
            
            // Route to different aggregation strategies
            .multicast()
                .parallelProcessing(true)
                .to("direct:time-window-aggregator",
                   "direct:business-metric-aggregator",
                   "direct:correlation-aggregator")
            .end()
            
            .log("✅ Aggregation processing completed");

        /**
         * Route 2: Time Window Aggregator
         * Purpose: Aggregate events by time windows
         */
        from("direct:time-window-aggregator")
            .routeId("time-window-aggregator")
            .description("Aggregator Pattern: Time-window aggregations")
            .log("⏰ Processing time-window aggregations")
            
            // Aggregate by 1-minute windows
            .aggregate(header("minute-window"), new TimeWindowAggregationStrategy())
                .completionSize(10)
                .completionTimeout(60000) // 1 minute
                .to("direct:minute-aggregation-completed")
            
            // Aggregate by 5-minute windows  
            .aggregate(header("5minute-window"), new TimeWindowAggregationStrategy())
                .completionSize(50)
                .completionTimeout(300000) // 5 minutes
                .to("direct:5minute-aggregation-completed")
            
            // Aggregate by 15-minute windows
            .aggregate(header("15minute-window"), new TimeWindowAggregationStrategy())
                .completionSize(150)
                .completionTimeout(900000) // 15 minutes
                .to("direct:15minute-aggregation-completed")
            
            // Aggregate by 1-hour windows
            .aggregate(header("hour-window"), new TimeWindowAggregationStrategy())
                .completionSize(600)
                .completionTimeout(3600000) // 1 hour
                .to("direct:hour-aggregation-completed")
            
            .log("✅ Time-window aggregations processed");

        /**
         * Route 3: Business Metric Aggregator
         * Purpose: Aggregate business-specific metrics
         */
        from("direct:business-metric-aggregator")
            .routeId("business-metric-aggregator")
            .description("Aggregator Pattern: Business metrics aggregation")
            .log("💼 Processing business metric aggregations")
            
            // Route to business domain aggregators
            .choice()
                .when(header("eventType").startsWith("ORDER_"))
                    .to("direct:order-metrics-aggregator")
                .when(header("eventType").startsWith("USER_"))
                    .to("direct:user-metrics-aggregator")
                .when(header("eventType").contains("REVENUE") or header("eventType").contains("PAYMENT"))
                    .to("direct:revenue-aggregator")
                .when(header("eventType").startsWith("NOTIFICATION_"))
                    .to("direct:notification-metrics-aggregator")
                .otherwise()
                    .to("direct:generic-metrics-aggregator")
            .end()
            
            .log("✅ Business metric aggregations processed");

        /**
         * Route 4: Correlation Aggregator
         * Purpose: Aggregate events by correlation ID for saga tracking
         */
        from("direct:correlation-aggregator")
            .routeId("correlation-aggregator")
            .description("Aggregator Pattern: Correlation-based aggregations")
            .log("🔗 Processing correlation aggregations")
            
            // Aggregate by correlation ID for saga completion tracking
            .aggregate(header("correlationId"), new CorrelationAggregationStrategy())
                .completionPredicate(method(this, "isCorrelationComplete"))
                .completionTimeout(300000) // 5 minutes timeout for saga completion
                .to("direct:correlation-aggregation-completed")
            
            // Aggregate by user ID for user journey tracking
            .aggregate(header("userId"), new UserJourneyAggregationStrategy())
                .completionSize(20)
                .completionTimeout(1800000) // 30 minutes for user session
                .to("direct:user-journey-completed")
            
            .log("✅ Correlation aggregations processed");

        /**
         * Route 5: Order Metrics Aggregator
         * Purpose: Aggregate order-related metrics
         */
        from("direct:order-metrics-aggregator")
            .routeId("order-metrics-aggregator")
            .description("Aggregator Pattern: Order metrics aggregation")
            .log("🛒 Processing order metrics aggregation")
            
            .process(exchange -> {
                String eventType = exchange.getIn().getHeader("eventType", String.class);
                String orderId = exchange.getIn().getHeader("orderId", String.class);
                Double orderAmount = exchange.getIn().getHeader("orderAmount", Double.class);
                
                // Update order metrics
                updateAggregationCounter("orders.total");
                
                if ("ORDER_CREATED".equals(eventType)) {
                    updateAggregationCounter("orders.created");
                } else if ("ORDER_PAID".equals(eventType)) {
                    updateAggregationCounter("orders.paid");
                    if (orderAmount != null) {
                        updateAggregationSum("revenue.total", orderAmount);
                    }
                } else if ("ORDER_DELIVERED".equals(eventType)) {
                    updateAggregationCounter("orders.delivered");
                } else if ("ORDER_CANCELLED".equals(eventType)) {
                    updateAggregationCounter("orders.cancelled");
                }
                
                // Build aggregation result
                Map<String, Object> orderMetrics = new HashMap<>();
                orderMetrics.put("eventType", eventType);
                orderMetrics.put("orderId", orderId);
                orderMetrics.put("amount", orderAmount);
                orderMetrics.put("totalOrders", getAggregationCount("orders.total"));
                orderMetrics.put("totalRevenue", getAggregationSum("revenue.total"));
                orderMetrics.put("aggregationTimestamp", LocalDateTime.now());
                
                exchange.getIn().setBody(orderMetrics);
                
                log.info("🛒 Order aggregation: {} - Total Orders: {}, Total Revenue: {}", 
                        eventType, getAggregationCount("orders.total"), getAggregationSum("revenue.total"));
            })
            
            .to("direct:aggregation-publisher")
            .log("✅ Order metrics aggregation completed");

        /**
         * Route 6: User Metrics Aggregator
         * Purpose: Aggregate user behavior metrics
         */
        from("direct:user-metrics-aggregator")
            .routeId("user-metrics-aggregator")
            .description("Aggregator Pattern: User metrics aggregation")
            .log("👤 Processing user metrics aggregation")
            
            .process(exchange -> {
                String eventType = exchange.getIn().getHeader("eventType", String.class);
                String userId = exchange.getIn().getHeader("userId", String.class);
                
                // Update user metrics
                updateAggregationCounter("users.total");
                
                if ("USER_REGISTERED".equals(eventType)) {
                    updateAggregationCounter("users.registered");
                } else if ("USER_LOGIN".equals(eventType)) {
                    updateAggregationCounter("users.logins");
                } else if ("PROFILE_UPDATED".equals(eventType)) {
                    updateAggregationCounter("users.profile_updates");
                }
                
                // Build aggregation result
                Map<String, Object> userMetrics = new HashMap<>();
                userMetrics.put("eventType", eventType);
                userMetrics.put("userId", userId);
                userMetrics.put("totalUsers", getAggregationCount("users.total"));
                userMetrics.put("totalRegistrations", getAggregationCount("users.registered"));
                userMetrics.put("totalLogins", getAggregationCount("users.logins"));
                userMetrics.put("aggregationTimestamp", LocalDateTime.now());
                
                exchange.getIn().setBody(userMetrics);
                
                log.info("👤 User aggregation: {} - Total Users: {}, Logins: {}", 
                        eventType, getAggregationCount("users.total"), getAggregationCount("users.logins"));
            })
            
            .to("direct:aggregation-publisher")
            .log("✅ User metrics aggregation completed");

        /**
         * Route 7: Revenue Aggregator
         * Purpose: Aggregate revenue and financial metrics
         */
        from("direct:revenue-aggregator")
            .routeId("revenue-aggregator")
            .description("Aggregator Pattern: Revenue aggregation")
            .log("💰 Processing revenue aggregation")
            
            .process(exchange -> {
                String eventType = exchange.getIn().getHeader("eventType", String.class);
                Double amount = exchange.getIn().getHeader("amount", Double.class);
                String currency = exchange.getIn().getHeader("currency", "USD");
                
                if (amount != null) {
                    updateAggregationSum("revenue.total", amount);
                    updateAggregationCounter("revenue.transactions");
                    
                    // Calculate average transaction value
                    double totalRevenue = getAggregationSum("revenue.total");
                    long totalTransactions = getAggregationCount("revenue.transactions");
                    double averageTransaction = totalTransactions > 0 ? totalRevenue / totalTransactions : 0;
                    
                    // Build aggregation result
                    Map<String, Object> revenueMetrics = new HashMap<>();
                    revenueMetrics.put("eventType", eventType);
                    revenueMetrics.put("amount", amount);
                    revenueMetrics.put("currency", currency);
                    revenueMetrics.put("totalRevenue", totalRevenue);
                    revenueMetrics.put("totalTransactions", totalTransactions);
                    revenueMetrics.put("averageTransaction", averageTransaction);
                    revenueMetrics.put("aggregationTimestamp", LocalDateTime.now());
                    
                    exchange.getIn().setBody(revenueMetrics);
                    
                    log.info("💰 Revenue aggregation: {} - Total: {}, Avg: {}", 
                            eventType, totalRevenue, averageTransaction);
                } else {
                    log.warn("💰 Revenue aggregation skipped: No amount for {}", eventType);
                }
            })
            
            .to("direct:aggregation-publisher")
            .log("✅ Revenue aggregation completed");

        /**
         * Route 8: Notification Metrics Aggregator
         * Purpose: Aggregate notification delivery and engagement metrics
         */
        from("direct:notification-metrics-aggregator")
            .routeId("notification-metrics-aggregator")
            .description("Aggregator Pattern: Notification metrics aggregation")
            .log("🔔 Processing notification metrics aggregation")
            
            .process(exchange -> {
                String eventType = exchange.getIn().getHeader("eventType", String.class);
                String channel = exchange.getIn().getHeader("channel", String.class);
                String notificationId = exchange.getIn().getHeader("notificationId", String.class);
                
                // Update notification metrics
                updateAggregationCounter("notifications.total");
                
                if ("NOTIFICATION_SENT".equals(eventType)) {
                    updateAggregationCounter("notifications.sent");
                    if (channel != null) {
                        updateAggregationCounter("notifications.sent." + channel.toLowerCase());
                    }
                } else if ("NOTIFICATION_DELIVERED".equals(eventType)) {
                    updateAggregationCounter("notifications.delivered");
                } else if ("NOTIFICATION_OPENED".equals(eventType)) {
                    updateAggregationCounter("notifications.opened");
                } else if ("NOTIFICATION_CLICKED".equals(eventType)) {
                    updateAggregationCounter("notifications.clicked");
                }
                
                // Calculate engagement rates
                long sent = getAggregationCount("notifications.sent");
                long opened = getAggregationCount("notifications.opened");
                long clicked = getAggregationCount("notifications.clicked");
                
                double openRate = sent > 0 ? (double) opened / sent * 100 : 0;
                double clickRate = sent > 0 ? (double) clicked / sent * 100 : 0;
                
                // Build aggregation result
                Map<String, Object> notificationMetrics = new HashMap<>();
                notificationMetrics.put("eventType", eventType);
                notificationMetrics.put("channel", channel);
                notificationMetrics.put("notificationId", notificationId);
                notificationMetrics.put("totalSent", sent);
                notificationMetrics.put("totalOpened", opened);
                notificationMetrics.put("totalClicked", clicked);
                notificationMetrics.put("openRate", openRate);
                notificationMetrics.put("clickRate", clickRate);
                notificationMetrics.put("aggregationTimestamp", LocalDateTime.now());
                
                exchange.getIn().setBody(notificationMetrics);
                
                log.info("🔔 Notification aggregation: {} - Sent: {}, Open Rate: {}%, Click Rate: {}%", 
                        eventType, sent, String.format("%.1f", openRate), String.format("%.1f", clickRate));
            })
            
            .to("direct:aggregation-publisher")
            .log("✅ Notification metrics aggregation completed");

        /**
         * Route 9: Aggregation Publisher
         * Purpose: Publish completed aggregations
         */
        from("direct:aggregation-publisher")
            .routeId("aggregation-publisher")
            .description("Aggregator Pattern: Aggregation results publisher")
            .log("📤 Publishing aggregation results")
            
            .process(exchange -> {
                Map<String, Object> aggregationResult = (Map<String, Object>) exchange.getIn().getBody();
                String eventType = (String) aggregationResult.get("eventType");
                
                // Add publishing metadata
                aggregationResult.put("publishedAt", LocalDateTime.now());
                aggregationResult.put("publishedBy", "analytics-service");
                aggregationResult.put("aggregationVersion", "1.0");
                
                exchange.getIn().setHeader("aggregationResultType", eventType);
                
                log.info("📤 Publishing aggregation result for: {}", eventType);
            })
            
            // Send to real-time dashboard
            .to("direct:dashboard-aggregation-update")
            
            // Store in analytics database
            .to("elasticsearch:aggregations?operation=INDEX&indexName=business-aggregations")
            
            // Publish to other services that need aggregated data
            .to("rabbitmq:analytics.aggregations.topic?routingKey=aggregation.completed")
            
            .log("✅ Aggregation results published");

        /**
         * Aggregation completion handlers
         */
        from("direct:minute-aggregation-completed")
            .routeId("minute-aggregation-completed")
            .log("⏰ 1-minute aggregation completed: ${body}");
            
        from("direct:5minute-aggregation-completed")
            .routeId("5minute-aggregation-completed")
            .log("⏰ 5-minute aggregation completed: ${body}");
            
        from("direct:15minute-aggregation-completed")
            .routeId("15minute-aggregation-completed")
            .log("⏰ 15-minute aggregation completed: ${body}");
            
        from("direct:hour-aggregation-completed")
            .routeId("hour-aggregation-completed")
            .log("⏰ 1-hour aggregation completed: ${body}");
            
        from("direct:correlation-aggregation-completed")
            .routeId("correlation-aggregation-completed")
            .log("🔗 Correlation aggregation completed: ${body}");
            
        from("direct:user-journey-completed")
            .routeId("user-journey-completed")
            .log("👤 User journey aggregation completed: ${body}");

        /**
         * Aggregator Dead Letter Channel
         */
        from("direct:aggregator-dead-letter")
            .routeId("aggregator-dead-letter")
            .description("Aggregator Pattern: Dead letter channel")
            .log(LoggingLevel.ERROR, "💀 Aggregation failed: ${exception.message}")
            
            .process(exchange -> {
                Exception exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                String aggregationKey = exchange.getIn().getHeader("aggregationKey", String.class);
                
                log.error("💀 Aggregation failed - Key: {}, Error: {}", 
                         aggregationKey, exception != null ? exception.getMessage() : "Unknown error");
                
                exchange.getIn().setHeader("failureReason", 
                    exception != null ? exception.getMessage() : "Unknown error");
                exchange.getIn().setHeader("failureTimestamp", LocalDateTime.now());
            })
            
            .to("rabbitmq:analytics.aggregation.failed?routingKey=aggregation.failed")
            .log("💾 Failed aggregation stored for investigation");

        // Mock processors
        from("direct:generic-metrics-aggregator")
            .routeId("generic-metrics-aggregator")
            .log("🔧 Mock: Generic metrics aggregator - ${body}");
            
        from("direct:dashboard-aggregation-update")
            .routeId("dashboard-aggregation-update")
            .log("📊 Mock: Dashboard aggregation update - ${header.aggregationResultType}");
    }

    // Aggregation strategy implementations
    private class TimeWindowAggregationStrategy implements AggregationStrategy {
        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange == null) {
                return newExchange;
            }
            
            // Simple aggregation - in real implementation, would aggregate metrics
            Map<String, Object> aggregatedData = new HashMap<>();
            aggregatedData.put("count", 1);
            aggregatedData.put("timestamp", LocalDateTime.now());
            
            newExchange.getIn().setBody(aggregatedData);
            return newExchange;
        }
    }

    private class CorrelationAggregationStrategy implements AggregationStrategy {
        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange == null) {
                return newExchange;
            }
            
            // Aggregate correlation events
            Map<String, Object> correlationData = new HashMap<>();
            correlationData.put("events", 1);
            correlationData.put("correlationId", newExchange.getIn().getHeader("correlationId"));
            correlationData.put("timestamp", LocalDateTime.now());
            
            newExchange.getIn().setBody(correlationData);
            return newExchange;
        }
    }

    private class UserJourneyAggregationStrategy implements AggregationStrategy {
        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange == null) {
                return newExchange;
            }
            
            // Aggregate user journey events
            Map<String, Object> journeyData = new HashMap<>();
            journeyData.put("journeySteps", 1);
            journeyData.put("userId", newExchange.getIn().getHeader("userId"));
            journeyData.put("timestamp", LocalDateTime.now());
            
            newExchange.getIn().setBody(journeyData);
            return newExchange;
        }
    }

    // Helper methods
    private String buildAggregationKey(String eventType, LocalDateTime timestamp) {
        return String.format("%s:%d:%d", eventType, timestamp.getHour(), timestamp.getMinute());
    }

    private void updateAggregationCounter(String key) {
        aggregationCounters.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
    }

    private void updateAggregationSum(String key, double value) {
        aggregationSums.merge(key, value, Double::sum);
    }

    private long getAggregationCount(String key) {
        return aggregationCounters.getOrDefault(key, new AtomicLong(0)).get();
    }

    private double getAggregationSum(String key) {
        return aggregationSums.getOrDefault(key, 0.0);
    }

    public boolean isCorrelationComplete(Exchange exchange) {
        // Simple completion check - in real implementation, would check business rules
        return Math.random() > 0.8; // 20% completion rate for demo
    }
} 