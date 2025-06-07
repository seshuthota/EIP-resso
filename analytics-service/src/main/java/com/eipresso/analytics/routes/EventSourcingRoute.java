package com.eipresso.analytics.routes;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event Sourcing Pattern Implementation for Analytics Service
 * 
 * EIP Pattern: Event Sourcing
 * Purpose: Capture and process all business events in real-time for analytics
 * Clustering: Active-Active compatible (distributed event processing)
 * 
 * Routes:
 * 1. event-source-entry: Main event sourcing entry point
 * 2. business-event-processor: Process business events from all services
 * 3. order-event-processor: Specialized order event processing
 * 4. user-event-processor: User activity event processing
 * 5. event-store-persister: Persist events to event store
 * 6. event-analytics-aggregator: Real-time event aggregation
 */
@Component
public class EventSourcingRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        
        // Global error handling for Event Sourcing pattern
        errorHandler(deadLetterChannel("direct:event-sourcing-dead-letter")
            .maximumRedeliveries(5)
            .redeliveryDelay(1000)
            .retryAttemptedLogLevel(LoggingLevel.WARN)
            .useExponentialBackOff()
            .backOffMultiplier(2)
            .maximumRedeliveryDelay(60000));

        /**
         * Route 1: Event Source Entry Point
         */
        from("direct:event-source-entry")
            .routeId("event-source-entry")
            .description("Event Sourcing Pattern: Main event entry point")
            .log("📊 Processing business event: ${header.eventType}")
            
            .process(exchange -> {
                String eventId = UUID.randomUUID().toString();
                exchange.getIn().setHeader("eventId", eventId);
                exchange.getIn().setHeader("eventTimestamp", LocalDateTime.now());
                exchange.getIn().setHeader("sourcingVersion", "1.0");
                
                String correlationId = exchange.getIn().getHeader("correlationId", String.class);
                if (correlationId == null) {
                    correlationId = eventId;
                    exchange.getIn().setHeader("correlationId", correlationId);
                }
                
                log.info("📊 Event sourced: {} [{}]", eventId, correlationId);
            })
            
            .to("direct:business-event-processor")
            .log("✅ Event sourcing completed for ${header.eventId}");

        /**
         * Route 2: Business Event Processor
         */
        from("direct:business-event-processor")
            .routeId("business-event-processor")
            .description("Event Sourcing Pattern: Business event processing")
            .log("🏢 Processing business event from ${header.sourceService}")
            
            .choice()
                .when(header("sourceService").isEqualTo("order-management"))
                    .to("direct:order-event-processor")
                .when(header("sourceService").isEqualTo("user-service"))
                    .to("direct:user-event-processor")
                .otherwise()
                    .to("direct:generic-event-processor")
            .end()
            
            .to("direct:event-store-persister")
            .to("direct:event-analytics-aggregator")
            .log("✅ Business event processing completed");

        /**
         * Route 3: Order Event Processor
         */
        from("direct:order-event-processor")
            .routeId("order-event-processor")
            .description("Event Sourcing Pattern: Order event processing")
            .log("🛒 Processing order event: ${header.eventType}")
            
            .process(exchange -> {
                String eventType = exchange.getIn().getHeader("eventType", String.class);
                String orderId = exchange.getIn().getHeader("orderId", String.class);
                
                exchange.getIn().setHeader("analyticsCategory", "order");
                exchange.getIn().setHeader("businessDomain", "ecommerce");
                
                if ("ORDER_CREATED".equals(eventType)) {
                    exchange.getIn().setHeader("metricsType", "conversion");
                } else if ("ORDER_PAID".equals(eventType)) {
                    exchange.getIn().setHeader("metricsType", "revenue");
                } else if ("ORDER_DELIVERED".equals(eventType)) {
                    exchange.getIn().setHeader("metricsType", "fulfillment");
                }
                
                log.info("🛒 Order event enriched: {} for order {}", eventType, orderId);
            })
            
            .multicast()
                .parallelProcessing(true)
                .to("direct:order-metrics-aggregator",
                   "direct:revenue-calculator")
            .end()
            
            .log("✅ Order event processing completed");

        /**
         * Route 4: User Event Processor
         */
        from("direct:user-event-processor")
            .routeId("user-event-processor")
            .description("Event Sourcing Pattern: User event processing")
            .log("👤 Processing user event: ${header.eventType}")
            
            .process(exchange -> {
                String eventType = exchange.getIn().getHeader("eventType", String.class);
                String userId = exchange.getIn().getHeader("userId", String.class);
                
                exchange.getIn().setHeader("analyticsCategory", "user");
                exchange.getIn().setHeader("businessDomain", "customer");
                
                if ("USER_REGISTERED".equals(eventType)) {
                    exchange.getIn().setHeader("metricsType", "acquisition");
                } else if ("USER_LOGIN".equals(eventType)) {
                    exchange.getIn().setHeader("metricsType", "engagement");
                }
                
                log.info("👤 User event enriched: {} for user {}", eventType, userId);
            })
            
            .to("direct:user-behavior-tracker")
            .log("✅ User event processing completed");

        /**
         * Route 5: Event Store Persister
         */
        from("direct:event-store-persister")
            .routeId("event-store-persister")
            .description("Event Sourcing Pattern: Event store persistence")
            .log("💾 Persisting event to event store")
            
            .process(exchange -> {
                String eventId = exchange.getIn().getHeader("eventId", String.class);
                LocalDateTime timestamp = exchange.getIn().getHeader("eventTimestamp", LocalDateTime.class);
                
                exchange.getIn().setHeader("storageTimestamp", LocalDateTime.now());
                exchange.getIn().setHeader("immutable", true);
                
                log.info("💾 Event {} prepared for storage at {}", eventId, timestamp);
            })
            
            .to("elasticsearch:events?operation=INDEX&indexName=business-events")
            .log("✅ Event persisted to event store");

        /**
         * Route 6: Event Analytics Aggregator
         */
        from("direct:event-analytics-aggregator")
            .routeId("event-analytics-aggregator")
            .description("Event Sourcing Pattern: Real-time analytics aggregation")
            .log("📈 Aggregating event for real-time analytics")
            
            .process(exchange -> {
                String analyticsCategory = exchange.getIn().getHeader("analyticsCategory", String.class);
                String metricsType = exchange.getIn().getHeader("metricsType", String.class);
                
                exchange.getIn().setHeader("aggregationKey", 
                    String.format("%s:%s", analyticsCategory, metricsType));
                exchange.getIn().setHeader("aggregationTimestamp", System.currentTimeMillis());
                
                log.info("📈 Aggregating: {} - {}", analyticsCategory, metricsType);
            })
            
            .to("direct:real-time-dashboard-aggregator")
            .log("✅ Event analytics aggregation completed");

        /**
         * Dead Letter Channel
         */
        from("direct:event-sourcing-dead-letter")
            .routeId("event-sourcing-dead-letter")
            .description("Event Sourcing Pattern: Dead letter channel")
            .log(LoggingLevel.ERROR, "💀 Event sourcing failed: ${exception.message}")
            
            .process(exchange -> {
                Exception exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                String eventId = exchange.getIn().getHeader("eventId", String.class);
                
                log.error("💀 Event sourcing failed - ID: {}, Error: {}", 
                         eventId, exception != null ? exception.getMessage() : "Unknown error");
                
                exchange.getIn().setHeader("failureReason", 
                    exception != null ? exception.getMessage() : "Unknown error");
                exchange.getIn().setHeader("failureTimestamp", LocalDateTime.now());
            })
            
            .to("rabbitmq:analytics.failed.events?routingKey=failed.event")
            .log("💾 Failed event stored for investigation");

        // Mock processors
        from("direct:generic-event-processor")
            .routeId("generic-event-processor")
            .log("🔧 Mock: Generic event processor - ${body}");
            
        from("direct:order-metrics-aggregator")
            .routeId("order-metrics-aggregator")
            .log("🛒 Mock: Order metrics aggregator - ${body}");
            
        from("direct:revenue-calculator")
            .routeId("revenue-calculator")
            .log("💰 Mock: Revenue calculator - ${body}");
            
        from("direct:user-behavior-tracker")
            .routeId("user-behavior-tracker")
            .log("👤 Mock: User behavior tracker - ${body}");
            
        from("direct:real-time-dashboard-aggregator")
            .routeId("dashboard-aggregator")
            .log("📊 Mock: Dashboard aggregator - ${body}");
    }
} 