package com.eipresso.notification.routes;

import com.eipresso.notification.model.NotificationChannel;
import com.eipresso.notification.model.NotificationPriority;
import com.eipresso.notification.model.NotificationType;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * Publish-Subscribe Pattern Implementation for Notification Service
 * 
 * EIP Pattern: Publish-Subscribe
 * Purpose: Multi-channel notification distribution with topic-based routing
 * Clustering: Active-Active compatible (stateless message processing)
 * 
 * Key Features:
 * - Topic-based message distribution
 * - Channel-specific routing
 * - Priority-based processing
 * - User preference filtering
 * - Comprehensive error handling
 * 
 * Routes:
 * 1. notification-publisher: Main publish entry point
 * 2. notification-topic-router: Route to channel-specific topics
 * 3. email-topic-subscriber: Email notification processing
 * 4. sms-topic-subscriber: SMS notification processing
 * 5. push-topic-subscriber: Push notification processing
 * 6. in-app-topic-subscriber: In-app notification processing
 * 7. webhook-topic-subscriber: Webhook notification processing
 * 8. slack-topic-subscriber: Slack notification processing
 * 9. notification-broadcast: Broadcast to all applicable channels
 * 10. notification-dead-letter: Handle failed notifications
 */
@Component
public class PublishSubscribeRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        
        // Global error handling for Publish-Subscribe pattern
        errorHandler(deadLetterChannel("direct:notification-dead-letter")
            .maximumRedeliveries(3)
            .redeliveryDelay(2000)
            .retryAttemptedLogLevel(LoggingLevel.WARN)
            .useExponentialBackOff()
            .backOffMultiplier(2)
            .maximumRedeliveryDelay(30000));

        /**
         * Route 1: Notification Publisher (Main Entry Point)
         * Purpose: Accept notification requests and publish to appropriate topics
         */
        from("direct:notification-publisher")
            .routeId("notification-publisher")
            .description("Publish-Subscribe Pattern: Main notification publisher")
            .log("📡 Publishing notification: ${header.notificationType} for user ${header.userId}")
            
            // Validate notification request
            .process(exchange -> {
                validateNotificationRequest(exchange);
                
                // Set correlation ID for tracking
                String correlationId = exchange.getIn().getHeader("correlationId", String.class);
                if (correlationId == null) {
                    correlationId = java.util.UUID.randomUUID().toString();
                    exchange.getIn().setHeader("correlationId", correlationId);
                }
                
                log.info("🆔 Correlation ID: {}", correlationId);
            })
            
            // Route to topic-based distribution
            .to("direct:notification-topic-router")
            
            .log("✅ Notification published successfully");

        /**
         * Route 2: Topic Router
         * Purpose: Route notifications to channel-specific topics
         */
        from("direct:notification-topic-router")
            .routeId("notification-topic-router")
            .description("Publish-Subscribe Pattern: Topic-based routing")
            .log("🎯 Routing notification to topics")
            
            // Determine applicable channels based on user preferences and notification type
            .process(exchange -> {
                String notificationTypeStr = exchange.getIn().getHeader("notificationType", String.class);
                String channelStr = exchange.getIn().getHeader("channel", String.class);
                
                NotificationType notificationType = NotificationType.valueOf(notificationTypeStr);
                
                // If specific channel requested, use it; otherwise determine best channels
                if (channelStr != null && !channelStr.isEmpty()) {
                    NotificationChannel channel = NotificationChannel.valueOf(channelStr);
                    exchange.getIn().setHeader("targetChannels", channel.name());
                } else {
                    // Determine best channels based on notification type and urgency
                    String channels = determineChannelsForNotification(notificationType, exchange);
                    exchange.getIn().setHeader("targetChannels", channels);
                }
                
                log.info("📋 Target channels: {}", exchange.getIn().getHeader("targetChannels"));
            })
            
            // Check if broadcast to all channels is requested
            .choice()
                .when(header("broadcast").isEqualTo(true))
                    .to("direct:notification-broadcast")
                .otherwise()
                    .to("direct:channel-specific-routing")
            .end();

        /**
         * Route 3: Channel-Specific Routing
         * Purpose: Route to specific channel subscribers
         */
        from("direct:channel-specific-routing")
            .routeId("channel-specific-routing")
            .description("Publish-Subscribe Pattern: Channel-specific routing")
            .log("📮 Routing to specific channels: ${header.targetChannels}")
            
            // Split target channels and route to each
            .split(header("targetChannels").tokenize(","))
                .parallelProcessing(true)
                .stopOnException()
                .choice()
                    .when(body().isEqualTo("EMAIL"))
                        .to("rabbitmq:notification.email.topic?routingKey=email.notification")
                        .to("direct:email-topic-subscriber")
                    .when(body().isEqualTo("SMS"))
                        .to("rabbitmq:notification.sms.topic?routingKey=sms.notification")
                        .to("direct:sms-topic-subscriber")
                    .when(body().isEqualTo("PUSH"))
                        .to("rabbitmq:notification.push.topic?routingKey=push.notification")
                        .to("direct:push-topic-subscriber")
                    .when(body().isEqualTo("IN_APP"))
                        .to("rabbitmq:notification.inapp.topic?routingKey=inapp.notification")
                        .to("direct:in-app-topic-subscriber")
                    .when(body().isEqualTo("WEBHOOK"))
                        .to("rabbitmq:notification.webhook.topic?routingKey=webhook.notification")
                        .to("direct:webhook-topic-subscriber")
                    .when(body().isEqualTo("SLACK"))
                        .to("rabbitmq:notification.slack.topic?routingKey=slack.notification")
                        .to("direct:slack-topic-subscriber")
                    .otherwise()
                        .log(LoggingLevel.WARN, "⚠️ Unknown channel: ${body}")
                .end()
            .end()
            
            .log("✅ Channel-specific routing completed");

        /**
         * Route 4: Notification Broadcast
         * Purpose: Broadcast to all applicable channels
         */
        from("direct:notification-broadcast")
            .routeId("notification-broadcast")
            .description("Publish-Subscribe Pattern: Broadcast to all channels")
            .log("📡 Broadcasting notification to all applicable channels")
            
            // Publish to all channel topics simultaneously
            .multicast()
                .parallelProcessing(true)
                .stopOnException()
                .to("rabbitmq:notification.email.topic?routingKey=email.broadcast",
                   "rabbitmq:notification.sms.topic?routingKey=sms.broadcast",
                   "rabbitmq:notification.push.topic?routingKey=push.broadcast",
                   "rabbitmq:notification.inapp.topic?routingKey=inapp.broadcast",
                   "rabbitmq:notification.webhook.topic?routingKey=webhook.broadcast",
                   "rabbitmq:notification.slack.topic?routingKey=slack.broadcast")
            .end()
            
            // Also route to channel subscribers for immediate processing
            .multicast()
                .parallelProcessing(true)
                .to("direct:email-topic-subscriber",
                   "direct:sms-topic-subscriber",
                   "direct:push-topic-subscriber",
                   "direct:in-app-topic-subscriber",
                   "direct:webhook-topic-subscriber",
                   "direct:slack-topic-subscriber")
            .end()
            
            .log("✅ Broadcast completed to all channels");

        /**
         * Routes 5-10: Channel-Specific Subscribers
         * Purpose: Process notifications for specific channels
         */
        
        // Email Topic Subscriber
        from("direct:email-topic-subscriber")
            .routeId("email-topic-subscriber")
            .description("Publish-Subscribe Pattern: Email channel subscriber")
            .log("📧 Processing email notification for user ${header.userId}")
            .setHeader("processingChannel", constant("EMAIL"))
            .to("direct:email-notification-processor")
            .log("✅ Email notification processed");

        // SMS Topic Subscriber
        from("direct:sms-topic-subscriber")
            .routeId("sms-topic-subscriber")
            .description("Publish-Subscribe Pattern: SMS channel subscriber")
            .log("📱 Processing SMS notification for user ${header.userId}")
            .setHeader("processingChannel", constant("SMS"))
            .to("direct:sms-notification-processor")
            .log("✅ SMS notification processed");

        // Push Topic Subscriber
        from("direct:push-topic-subscriber")
            .routeId("push-topic-subscriber")
            .description("Publish-Subscribe Pattern: Push channel subscriber")
            .log("🔔 Processing push notification for user ${header.userId}")
            .setHeader("processingChannel", constant("PUSH"))
            .to("direct:push-notification-processor")
            .log("✅ Push notification processed");

        // In-App Topic Subscriber
        from("direct:in-app-topic-subscriber")
            .routeId("in-app-topic-subscriber")
            .description("Publish-Subscribe Pattern: In-app channel subscriber")
            .log("📱 Processing in-app notification for user ${header.userId}")
            .setHeader("processingChannel", constant("IN_APP"))
            .to("direct:in-app-notification-processor")
            .log("✅ In-app notification processed");

        // Webhook Topic Subscriber
        from("direct:webhook-topic-subscriber")
            .routeId("webhook-topic-subscriber")
            .description("Publish-Subscribe Pattern: Webhook channel subscriber")
            .log("🔗 Processing webhook notification for user ${header.userId}")
            .setHeader("processingChannel", constant("WEBHOOK"))
            .to("direct:webhook-notification-processor")
            .log("✅ Webhook notification processed");

        // Slack Topic Subscriber
        from("direct:slack-topic-subscriber")
            .routeId("slack-topic-subscriber")
            .description("Publish-Subscribe Pattern: Slack channel subscriber")
            .log("💬 Processing Slack notification for user ${header.userId}")
            .setHeader("processingChannel", constant("SLACK"))
            .to("direct:slack-notification-processor")
            .log("✅ Slack notification processed");

        /**
         * Route 11: Dead Letter Channel
         * Purpose: Handle failed notification processing
         */
        from("direct:notification-dead-letter")
            .routeId("notification-dead-letter")
            .description("Publish-Subscribe Pattern: Dead letter channel")
            .log(LoggingLevel.ERROR, "💀 Notification failed after retries: ${exception.message}")
            
            .process(exchange -> {
                // Log failure details
                Exception exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                String correlationId = exchange.getIn().getHeader("correlationId", String.class);
                String notificationType = exchange.getIn().getHeader("notificationType", String.class);
                String userId = exchange.getIn().getHeader("userId", String.class);
                
                log.error("💀 Dead letter - Correlation: {}, Type: {}, User: {}, Error: {}", 
                         correlationId, notificationType, userId, 
                         exception != null ? exception.getMessage() : "Unknown error");
                
                // Store for analysis
                exchange.getIn().setHeader("failureTimestamp", System.currentTimeMillis());
                exchange.getIn().setHeader("failureReason", 
                    exception != null ? exception.getMessage() : "Unknown error");
            })
            
            .to("rabbitmq:notification.failed.dlq?routingKey=failed.notification")
            .log("💾 Failed notification stored in dead letter queue");

        // Mock channel processors (to be implemented with actual integrations)
        from("direct:email-notification-processor")
            .routeId("email-processor-mock")
            .log("📧 Mock: Email processor - ${body}");
            
        from("direct:sms-notification-processor")
            .routeId("sms-processor-mock")
            .log("📱 Mock: SMS processor - ${body}");
            
        from("direct:push-notification-processor")
            .routeId("push-processor-mock")
            .log("🔔 Mock: Push processor - ${body}");
            
        from("direct:in-app-notification-processor")
            .routeId("in-app-processor-mock")
            .log("📱 Mock: In-app processor - ${body}");
            
        from("direct:webhook-notification-processor")
            .routeId("webhook-processor-mock")
            .log("🔗 Mock: Webhook processor - ${body}");
            
        from("direct:slack-notification-processor")
            .routeId("slack-processor-mock")
            .log("💬 Mock: Slack processor - ${body}");
    }

    /**
     * Validate notification request
     */
    private void validateNotificationRequest(Exchange exchange) throws Exception {
        String userId = exchange.getIn().getHeader("userId", String.class);
        String notificationType = exchange.getIn().getHeader("notificationType", String.class);
        String userEmail = exchange.getIn().getHeader("userEmail", String.class);
        
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("User ID is required");
        }
        
        if (notificationType == null || notificationType.isEmpty()) {
            throw new IllegalArgumentException("Notification type is required");
        }
        
        if (userEmail == null || userEmail.isEmpty()) {
            throw new IllegalArgumentException("User email is required");
        }
        
        // Validate notification type
        try {
            NotificationType.valueOf(notificationType);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid notification type: " + notificationType);
        }
    }

    /**
     * Determine best channels for notification based on type and context
     */
    private String determineChannelsForNotification(NotificationType notificationType, Exchange exchange) {
        StringBuilder channels = new StringBuilder();
        
        // Default channel selection based on notification type
        if (notificationType.requiresImmediateDelivery()) {
            channels.append("SMS,PUSH,IN_APP");
        } else if (notificationType.isOrderRelated()) {
            channels.append("EMAIL,PUSH");
        } else if (notificationType.isPaymentRelated()) {
            channels.append("EMAIL,SMS");
        } else if (notificationType.isMarketingRelated()) {
            channels.append("EMAIL,PUSH");
        } else {
            channels.append("EMAIL");
        }
        
        return channels.toString();
    }
}