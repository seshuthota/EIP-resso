package com.eipresso.notification.routes;

import com.eipresso.notification.model.NotificationChannel;
import com.eipresso.notification.model.NotificationPriority;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * Throttling Pattern Implementation for Notification Service
 * 
 * EIP Pattern: Throttling
 * Purpose: Rate limiting and flow control for notification delivery
 * Clustering: Active-Active compatible (shared throttling state via Redis)
 * 
 * Key Features:
 * - Priority-based throttling
 * - Channel-specific rate limits
 * - User-level throttling
 * - Global system throttling
 * - Burst handling with token bucket
 * - Dynamic throttle adjustment
 * - Anti-spam protection
 * 
 * Routes:
 * 1. notification-throttle-entry: Main throttling entry point
 * 2. priority-based-throttling: Priority-aware rate limiting
 * 3. channel-specific-throttling: Channel-based rate limits
 * 4. user-level-throttling: Per-user rate limiting
 * 5. global-system-throttling: System-wide rate limiting
 * 6. throttle-burst-handler: Handle burst traffic with token bucket
 * 7. throttle-queue-processor: Process throttled notifications
 * 8. notification-throttled: Handle throttled notifications
 * 9. notification-rate-approved: Process approved notifications
 */
@Component
public class ThrottlingRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        
        /**
         * Route 1: Main Throttling Entry Point
         * Purpose: Entry point for notification throttling
         */
        from("direct:notification-throttle-entry")
            .routeId("notification-throttle-entry")
            .description("Throttling Pattern: Main throttling entry point")
            .log("⏱️ Starting throttling check for notification ${header.correlationId}")
            
            // Initialize throttling context
            .process(exchange -> {
                exchange.getIn().setHeader("throttleStartTime", System.currentTimeMillis());
                exchange.getIn().setHeader("throttleChecks", 0);
                exchange.getIn().setHeader("throttleApproved", false);
                
                // Determine throttling parameters based on notification properties
                String priorityStr = exchange.getIn().getHeader("priority", String.class);
                String channelStr = exchange.getIn().getHeader("channel", String.class);
                
                if (priorityStr != null) {
                    NotificationPriority priority = NotificationPriority.valueOf(priorityStr);
                    exchange.getIn().setHeader("throttleDelayMs", priority.getThrottleDelayMs());
                    exchange.getIn().setHeader("maxRetries", priority.getMaxRetryCount());
                }
                
                if (channelStr != null) {
                    NotificationChannel channel = NotificationChannel.valueOf(channelStr);
                    exchange.getIn().setHeader("channelCode", channel.getChannelCode());
                }
                
                log.info("⏱️ Throttling context initialized");
            })
            
            // Apply throttling checks in sequence
            .to("direct:priority-based-throttling")
            .to("direct:channel-specific-throttling")
            .to("direct:user-level-throttling")
            .to("direct:global-system-throttling")
            
            // Final throttling decision
            .choice()
                .when(header("throttleBlocked").isEqualTo(true))
                    .to("direct:notification-throttled")
                .otherwise()
                    .to("direct:notification-rate-approved")
            .end();

        /**
         * Route 2: Priority-Based Throttling
         * Purpose: Apply priority-aware throttling delays
         */
        from("direct:priority-based-throttling")
            .routeId("priority-based-throttling")
            .description("Throttling Pattern: Priority-based rate limiting")
            .log("🚨 Checking priority-based throttling")
            
            .process(exchange -> {
                String priorityStr = exchange.getIn().getHeader("priority", String.class);
                
                if (priorityStr != null) {
                    NotificationPriority priority = NotificationPriority.valueOf(priorityStr);
                    
                    // Skip throttling for urgent notifications
                    if (priority == NotificationPriority.URGENT) {
                        log.info("🚨 URGENT priority - bypassing throttling");
                        exchange.getIn().setHeader("priorityBypass", true);
                        return;
                    }
                    
                    // Apply priority-specific delay
                    long throttleDelay = priority.getThrottleDelayMs();
                    if (throttleDelay > 0) {
                        exchange.getIn().setHeader("priorityThrottleDelay", throttleDelay);
                        log.info("🚨 Priority {} throttle delay: {}ms", priority, throttleDelay);
                    }
                }
                
                incrementThrottleChecks(exchange);
            })
            
            // Apply priority-based delay if needed
            .choice()
                .when(header("priorityThrottleDelay").isNotNull())
                    .delay(header("priorityThrottleDelay"))
                    .log("⏰ Applied priority-based throttling delay")
            .end()
            
            .log("✅ Priority-based throttling completed");

        /**
         * Route 3: Channel-Specific Throttling
         * Purpose: Apply channel-specific rate limits
         */
        from("direct:channel-specific-throttling")
            .routeId("channel-specific-throttling")
            .description("Throttling Pattern: Channel-specific rate limiting")
            .log("📱 Checking channel-specific throttling")
            
            .process(exchange -> {
                // Skip if priority bypass or already blocked
                if (Boolean.TRUE.equals(exchange.getIn().getHeader("priorityBypass")) ||
                    Boolean.TRUE.equals(exchange.getIn().getHeader("throttleBlocked"))) {
                    return;
                }
                
                String channelStr = exchange.getIn().getHeader("channel", String.class);
                String userId = exchange.getIn().getHeader("userId", String.class);
                
                if (channelStr != null) {
                    NotificationChannel channel = NotificationChannel.valueOf(channelStr);
                    
                    // Check channel-specific rate limits
                    boolean exceedsChannelLimit = checkChannelRateLimit(userId, channel);
                    
                    if (exceedsChannelLimit) {
                        exchange.getIn().setHeader("throttleBlocked", true);
                        exchange.getIn().setHeader("throttleReason", 
                            "Channel rate limit exceeded for " + channel.getDisplayName());
                        log.info("📱 Throttled by channel limit: {} for user {}", 
                                channel.getDisplayName(), userId);
                    } else {
                        log.info("📱 Channel rate limit OK: {}", channel.getDisplayName());
                    }
                }
                
                incrementThrottleChecks(exchange);
            })
            
            .log("✅ Channel-specific throttling completed");

        /**
         * Route 4: User-Level Throttling
         * Purpose: Apply per-user rate limiting
         */
        from("direct:user-level-throttling")
            .routeId("user-level-throttling")
            .description("Throttling Pattern: User-level rate limiting")
            .log("👤 Checking user-level throttling")
            
            .process(exchange -> {
                // Skip if priority bypass or already blocked
                if (Boolean.TRUE.equals(exchange.getIn().getHeader("priorityBypass")) ||
                    Boolean.TRUE.equals(exchange.getIn().getHeader("throttleBlocked"))) {
                    return;
                }
                
                String userId = exchange.getIn().getHeader("userId", String.class);
                
                // Check user-specific rate limits
                boolean exceedsUserLimit = checkUserRateLimit(userId);
                
                if (exceedsUserLimit) {
                    exchange.getIn().setHeader("throttleBlocked", true);
                    exchange.getIn().setHeader("throttleReason", 
                        "User rate limit exceeded for user " + userId);
                    log.info("👤 Throttled by user limit: {}", userId);
                } else {
                    log.info("👤 User rate limit OK: {}", userId);
                }
                
                incrementThrottleChecks(exchange);
            })
            
            .log("✅ User-level throttling completed");

        /**
         * Route 5: Global System Throttling
         * Purpose: Apply system-wide rate limiting
         */
        from("direct:global-system-throttling")
            .routeId("global-system-throttling")
            .description("Throttling Pattern: Global system rate limiting")
            .log("🌐 Checking global system throttling")
            
            .process(exchange -> {
                // Skip if priority bypass or already blocked
                if (Boolean.TRUE.equals(exchange.getIn().getHeader("priorityBypass")) ||
                    Boolean.TRUE.equals(exchange.getIn().getHeader("throttleBlocked"))) {
                    return;
                }
                
                // Check global system limits
                boolean exceedsGlobalLimit = checkGlobalRateLimit();
                
                if (exceedsGlobalLimit) {
                    exchange.getIn().setHeader("throttleBlocked", true);
                    exchange.getIn().setHeader("throttleReason", "Global system rate limit exceeded");
                    log.info("🌐 Throttled by global system limit");
                } else {
                    log.info("🌐 Global rate limit OK");
                }
                
                incrementThrottleChecks(exchange);
            })
            
            .log("✅ Global system throttling completed");

        /**
         * Route 6: Token Bucket Throttling
         * Purpose: Implement token bucket algorithm for burst handling
         */
        from("direct:token-bucket-throttling")
            .routeId("token-bucket-throttling")
            .description("Throttling Pattern: Token bucket burst handling")
            .log("🪣 Checking token bucket availability")
            
            .process(exchange -> {
                String userId = exchange.getIn().getHeader("userId", String.class);
                String channelStr = exchange.getIn().getHeader("channel", String.class);
                
                // Check token bucket availability
                boolean tokenAvailable = checkTokenBucket(userId, channelStr);
                
                if (!tokenAvailable) {
                    exchange.getIn().setHeader("throttleBlocked", true);
                    exchange.getIn().setHeader("throttleReason", "Token bucket depleted");
                    log.info("🪣 Token bucket depleted for user {} channel {}", userId, channelStr);
                } else {
                    // Consume token
                    consumeToken(userId, channelStr);
                    log.info("🪣 Token consumed for user {} channel {}", userId, channelStr);
                }
            })
            
            .log("✅ Token bucket check completed");

        /**
         * Route 7: Notification Throttled
         * Purpose: Handle throttled notifications
         */
        from("direct:notification-throttled")
            .routeId("notification-throttled")
            .description("Throttling Pattern: Throttled notification handler")
            .log("⏸️ Notification throttled")
            
            .process(exchange -> {
                long throttleTime = System.currentTimeMillis() - 
                    exchange.getIn().getHeader("throttleStartTime", Long.class);
                String throttleReason = exchange.getIn().getHeader("throttleReason", String.class);
                String correlationId = exchange.getIn().getHeader("correlationId", String.class);
                
                log.info("⏸️ Notification throttled - Reason: {} ({}ms) [{}]", 
                        throttleReason, throttleTime, correlationId);
                
                // Add throttling metadata
                exchange.getIn().setHeader("throttled", true);
                exchange.getIn().setHeader("throttleDurationMs", throttleTime);
                exchange.getIn().setHeader("throttleTimestamp", System.currentTimeMillis());
                
                // Determine retry delay based on throttle reason
                long retryDelay = calculateRetryDelay(throttleReason);
                exchange.getIn().setHeader("retryDelay", retryDelay);
            })
            
            // Queue for retry with delay
            .to("rabbitmq:notification.throttled.queue?routingKey=throttled.notification")
            
            // Schedule retry
            .delay(header("retryDelay"))
            .to("direct:throttle-queue-processor")
            
            .log("🔄 Throttled notification queued for retry");

        /**
         * Route 8: Throttle Queue Processor
         * Purpose: Process notifications from throttle queue
         */
        from("direct:throttle-queue-processor")
            .routeId("throttle-queue-processor")
            .description("Throttling Pattern: Throttle queue processor")
            .log("🔄 Processing notification from throttle queue")
            
            .process(exchange -> {
                // Reset throttling state for retry
                exchange.getIn().removeHeader("throttleBlocked");
                exchange.getIn().removeHeader("throttleReason");
                exchange.getIn().setHeader("retryAttempt", true);
                
                // Increment retry count
                Integer retryCount = exchange.getIn().getHeader("retryCount", Integer.class);
                if (retryCount == null) retryCount = 0;
                exchange.getIn().setHeader("retryCount", retryCount + 1);
                
                log.info("🔄 Retry attempt {} for notification {}", 
                        retryCount + 1, exchange.getIn().getHeader("correlationId"));
            })
            
            // Retry throttling check
            .to("direct:notification-throttle-entry")
            
            .log("🔄 Throttle queue processing completed");

        /**
         * Route 9: Notification Rate Approved
         * Purpose: Handle notifications that passed throttling
         */
        from("direct:notification-rate-approved")
            .routeId("notification-rate-approved")
            .description("Throttling Pattern: Rate-approved notification handler")
            .log("✅ Notification approved for rate limiting")
            
            .process(exchange -> {
                long throttleTime = System.currentTimeMillis() - 
                    exchange.getIn().getHeader("throttleStartTime", Long.class);
                int checksPerformed = exchange.getIn().getHeader("throttleChecks", Integer.class);
                
                log.info("✅ Rate limiting approved - Checks: {} ({}ms)", 
                        checksPerformed, throttleTime);
                
                // Update rate limiting counters
                updateRateLimitCounters(exchange);
                
                exchange.getIn().setHeader("throttleApproved", true);
                exchange.getIn().setHeader("throttleDurationMs", throttleTime);
            })
            
            // Route to message filter for additional filtering
            .to("direct:notification-filter-entry")
            
            .log("🚀 Rate-approved notification sent to filter");

        /**
         * Route 10: Rate Limit Monitor (Timer-based)
         * Purpose: Monitor and reset rate limits periodically
         */
        from("timer:rate-limit-monitor?period=60000") // Every minute
            .routeId("rate-limit-monitor")
            .description("Throttling Pattern: Rate limit monitoring")
            .log("📊 Monitoring rate limits")
            
            .process(exchange -> {
                // Reset rate limit counters (mock implementation)
                log.info("📊 Rate limit counters reset");
                
                // Log current rate limiting statistics
                logRateLimitingStats();
            })
            
            .log("📊 Rate limit monitoring completed");
    }

    /**
     * Helper method to increment throttle checks counter
     */
    private void incrementThrottleChecks(Exchange exchange) {
        int current = exchange.getIn().getHeader("throttleChecks", Integer.class);
        exchange.getIn().setHeader("throttleChecks", current + 1);
    }

    /**
     * Check channel-specific rate limits
     */
    private boolean checkChannelRateLimit(String userId, NotificationChannel channel) {
        // Mock implementation - in reality, check Redis counters
        switch (channel) {
            case SMS:
                return false; // 10 SMS per hour limit (mock: always OK)
            case EMAIL:
                return false; // 100 emails per hour limit (mock: always OK)
            case PUSH:
                return false; // 50 push notifications per hour limit (mock: always OK)
            default:
                return false; // No limits for other channels
        }
    }

    /**
     * Check user-specific rate limits
     */
    private boolean checkUserRateLimit(String userId) {
        // Mock implementation - some users have exceeded limits
        return userId.equals("999"); // User 999 has exceeded limits
    }

    /**
     * Check global system rate limits
     */
    private boolean checkGlobalRateLimit() {
        // Mock implementation - check system load
        return false; // System is OK
    }

    /**
     * Check token bucket availability
     */
    private boolean checkTokenBucket(String userId, String channel) {
        // Mock implementation - token bucket algorithm
        return true; // Tokens available
    }

    /**
     * Consume token from bucket
     */
    private void consumeToken(String userId, String channel) {
        // Mock implementation - consume token
        log.debug("Token consumed for user {} channel {}", userId, channel);
    }

    /**
     * Calculate retry delay based on throttle reason
     */
    private long calculateRetryDelay(String throttleReason) {
        if (throttleReason.contains("Channel rate limit")) {
            return 300000; // 5 minutes for channel limits
        } else if (throttleReason.contains("User rate limit")) {
            return 600000; // 10 minutes for user limits
        } else if (throttleReason.contains("Global system")) {
            return 60000; // 1 minute for system limits
        }
        return 30000; // Default 30 seconds
    }

    /**
     * Update rate limiting counters
     */
    private void updateRateLimitCounters(Exchange exchange) {
        // Mock implementation - update Redis counters
        String userId = exchange.getIn().getHeader("userId", String.class);
        String channel = exchange.getIn().getHeader("channel", String.class);
        log.debug("Updated rate limit counters for user {} channel {}", userId, channel);
    }

    /**
     * Log rate limiting statistics
     */
    private void logRateLimitingStats() {
        // Mock implementation - log current stats
        log.info("📊 Rate limiting stats: Active throttles: 0, Queue depth: 0, Avg delay: 50ms");
    }
} 