package com.eipresso.notification.routes;

import com.eipresso.notification.model.NotificationPriority;
import com.eipresso.notification.model.NotificationType;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

/**
 * Message Filter Pattern Implementation for Notification Service
 * 
 * EIP Pattern: Message Filter
 * Purpose: Intelligent filtering of notifications based on user preferences, 
 *          business rules, and Do-Not-Disturb settings
 * Clustering: Active-Active compatible (stateless filtering logic)
 * 
 * Routes:
 * 1. notification-filter-entry: Main filter entry point
 * 2. priority-exemption-filter: Allow high-priority notifications to bypass filters
 * 3. user-preference-filter: Filter based on user notification preferences
 * 4. time-based-filter: Filter based on time and Do-Not-Disturb rules
 * 5. notification-approved: Route approved notifications
 * 6. notification-filtered: Handle filtered notifications
 */
@Component
public class MessageFilterRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        /**
         * Route 1: Main Filter Entry Point
         */
        from("direct:notification-filter-entry")
            .routeId("notification-filter-entry")
            .description("Message Filter Pattern: Main filter entry point")
            .log("🔍 Starting notification filtering for user ${header.userId}")
            
            // Set initial filter context
            .process(exchange -> {
                exchange.getIn().setHeader("filterReason", "");
                exchange.getIn().setHeader("filtersPassed", "");
                exchange.getIn().setHeader("startFilterTime", System.currentTimeMillis());
            })
            
            // Apply filters in sequence
            .to("direct:priority-exemption-filter")
            .to("direct:user-preference-filter")
            .to("direct:time-based-filter")
            
            // Final decision point
            .choice()
                .when(header("filterBlocked").isEqualTo(true))
                    .to("direct:notification-filtered")
                .otherwise()
                    .to("direct:notification-approved")
            .end();

        /**
         * Route 2: Priority Exemption Filter
         */
        from("direct:priority-exemption-filter")
            .routeId("priority-exemption-filter")
            .description("Message Filter Pattern: Priority exemption filtering")
            .log("🚨 Checking priority exemption")
            
            .process(exchange -> {
                String priorityStr = exchange.getIn().getHeader("priority", String.class);
                String notificationTypeStr = exchange.getIn().getHeader("notificationType", String.class);
                
                boolean isHighPriority = false;
                if (priorityStr != null) {
                    NotificationPriority priority = NotificationPriority.valueOf(priorityStr);
                    isHighPriority = priority.requiresImmediateProcessing();
                }
                
                if (notificationTypeStr != null) {
                    NotificationType notificationType = NotificationType.valueOf(notificationTypeStr);
                    isHighPriority = isHighPriority || notificationType.requiresImmediateDelivery();
                }
                
                if (isHighPriority) {
                    exchange.getIn().setHeader("priorityExemption", true);
                    log.info("🚨 High priority notification - bypassing most filters");
                } else {
                    exchange.getIn().setHeader("priorityExemption", false);
                }
            })
            
            .log("✅ Priority exemption filter completed");

        /**
         * Route 3: User Preference Filter
         */
        from("direct:user-preference-filter")
            .routeId("user-preference-filter")
            .description("Message Filter Pattern: User preference filtering")
            .log("👤 Checking user preferences for user ${header.userId}")
            
            .process(exchange -> {
                if (Boolean.TRUE.equals(exchange.getIn().getHeader("priorityExemption"))) {
                    log.info("👤 Priority exemption - skipping user preference filter");
                    return;
                }
                
                String userId = exchange.getIn().getHeader("userId", String.class);
                String notificationTypeStr = exchange.getIn().getHeader("notificationType", String.class);
                
                boolean userAllowsType = checkUserNotificationPreference(userId, notificationTypeStr);
                
                if (!userAllowsType) {
                    exchange.getIn().setHeader("filterBlocked", true);
                    exchange.getIn().setHeader("filterReason", "User has disabled this notification type");
                    log.info("👤 Notification blocked by user preference: {}", notificationTypeStr);
                } else {
                    log.info("👤 User preference allows notification type: {}", notificationTypeStr);
                }
            })
            
            .log("✅ User preference filter completed");

        /**
         * Route 4: Time-Based Filter
         */
        from("direct:time-based-filter")
            .routeId("time-based-filter")
            .description("Message Filter Pattern: Time-based Do-Not-Disturb filtering")
            .log("🕐 Checking time-based filters")
            
            .process(exchange -> {
                if (Boolean.TRUE.equals(exchange.getIn().getHeader("priorityExemption")) ||
                    Boolean.TRUE.equals(exchange.getIn().getHeader("filterBlocked"))) {
                    return;
                }
                
                String channelStr = exchange.getIn().getHeader("channel", String.class);
                LocalTime now = LocalTime.now();
                
                // Check Do-Not-Disturb hours (22:00 - 07:00 for SMS/PUSH)
                if ("SMS".equals(channelStr) || "PUSH".equals(channelStr)) {
                    if (now.isAfter(LocalTime.of(22, 0)) || now.isBefore(LocalTime.of(7, 0))) {
                        exchange.getIn().setHeader("filterBlocked", true);
                        exchange.getIn().setHeader("filterReason", 
                            "Do-Not-Disturb hours (22:00-07:00) for " + channelStr);
                        log.info("🕐 Notification blocked by DND hours: {}", channelStr);
                        return;
                    }
                }
                
                log.info("🕐 Time-based filter passed");
            })
            
            .log("✅ Time-based filter completed");

        /**
         * Route 5: Notification Approved
         */
        from("direct:notification-approved")
            .routeId("notification-approved")
            .description("Message Filter Pattern: Approved notification handler")
            .log("✅ Notification approved for delivery")
            
            .process(exchange -> {
                long filterTime = System.currentTimeMillis() - 
                    exchange.getIn().getHeader("startFilterTime", Long.class);
                log.info("✅ Notification approved ({}ms)", filterTime);
                exchange.getIn().setHeader("filterApproved", true);
            })
            
            .to("direct:notification-publisher")
            .log("🚀 Approved notification sent to publisher");

        /**
         * Route 6: Notification Filtered
         */
        from("direct:notification-filtered")
            .routeId("notification-filtered")
            .description("Message Filter Pattern: Filtered notification handler")
            .log("🚫 Notification filtered out")
            
            .process(exchange -> {
                long filterTime = System.currentTimeMillis() - 
                    exchange.getIn().getHeader("startFilterTime", Long.class);
                String filterReason = exchange.getIn().getHeader("filterReason", String.class);
                
                log.info("🚫 Notification filtered - Reason: {} ({}ms)", filterReason, filterTime);
                exchange.getIn().setHeader("filterRejected", true);
            })
            
            .to("rabbitmq:notification.filtered.analytics?routingKey=filtered.notification")
            .log("📊 Filtered notification logged for analytics");
    }

    /**
     * Mock user notification preference check
     */
    private boolean checkUserNotificationPreference(String userId, String notificationType) {
        List<String> disabledTypes = Arrays.asList("PROMOTIONAL", "SURVEY_INVITATION");
        return !disabledTypes.contains(notificationType);
    }
} 