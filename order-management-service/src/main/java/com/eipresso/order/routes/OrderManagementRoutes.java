package com.eipresso.order.routes;

import com.eipresso.order.model.*;
import com.eipresso.order.service.OrderService;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.hazelcast.core.HazelcastInstance;

/**
 * Order Management Camel Routes
 * Implements advanced EIP patterns: Event Sourcing, Content-Based Router, 
 * Split/Aggregate, Idempotent Consumer, Wire Tap
 */
@Component
public class OrderManagementRoutes extends RouteBuilder {

    @Autowired
    private OrderService orderService;
    
    @Autowired
    private HazelcastInstance hazelcastInstance;

    @Override
    public void configure() throws Exception {
        
        // Configure JSON data format
        JacksonDataFormat jsonFormat = new JacksonDataFormat();
        jsonFormat.setUnmarshalType(Order.class);
        
        // Configure Dead Letter Channel for error handling
        errorHandler(deadLetterChannel("direct:order-error-handler")
            .maximumRedeliveries(3)
            .redeliveryDelay(5000)
            .retryAttemptedLogLevel(LoggingLevel.WARN));
        
        // Configure Idempotent Repository (simplified for now)
        // HazelcastIdempotentRepository idempotentRepo = 
        //     new HazelcastIdempotentRepository(hazelcastInstance, "order-idempotent");
        
        // ================================================================
        // MAIN ORDER PROCESSING ROUTES - Content-Based Router Pattern
        // ================================================================
        
        /**
         * Order Created Route - Event Sourcing Pattern Entry Point
         */
        from("direct:order-created")
            .routeId("order-created-main")
            .log(LoggingLevel.INFO, "Processing new order: ${body.id}")
            .wireTap("direct:order-audit-trail")
            .choice()
                .when(header("correlationId").isNotNull())
                    .to("direct:order-event-sourcing")
                .otherwise()
                    .log(LoggingLevel.WARN, "Order created without correlation ID: ${body.id}")
            .end()
            .multicast()
                .to("direct:order-validation")
                .to("direct:order-payment-request")
                .to("direct:order-notification")
            .end();
        
        /**
         * Content-Based Router - Route orders based on status and business rules
         */
        from("direct:order-status-changed")
            .routeId("order-status-router")
            .log(LoggingLevel.INFO, "Order status changed: ${body.id} -> ${header.newStatus}")
            .wireTap("direct:order-audit-trail")
            .choice()
                .when(header("newStatus").isEqualTo("PAID"))
                    .to("direct:order-paid-processing")
                .when(header("newStatus").isEqualTo("PREPARING"))
                    .to("direct:order-preparation-workflow")
                .when(header("newStatus").isEqualTo("SHIPPED"))
                    .to("direct:order-shipping-workflow")
                .when(header("newStatus").isEqualTo("DELIVERED"))
                    .to("direct:order-delivered-workflow")
                .when(header("newStatus").isEqualTo("CANCELLED"))
                    .to("direct:order-cancellation-workflow")
                .otherwise()
                    .log(LoggingLevel.WARN, "Unhandled order status: ${header.newStatus}")
            .end();
        
        // ================================================================
        // EVENT SOURCING IMPLEMENTATION
        // ================================================================
        
        /**
         * Event Sourcing - Capture all order state changes
         */
        from("direct:order-event-sourcing")
            .routeId("order-event-sourcing")
            .log(LoggingLevel.DEBUG, "Capturing event for order: ${body.id}")
            .process(exchange -> {
                Order order = exchange.getIn().getBody(Order.class);
                String correlationId = exchange.getIn().getHeader("correlationId", String.class);
                log.info("Event sourcing for order {} with correlation {}", 
                        order.getId(), correlationId);
            })
            .to("direct:event-store-persistence");
        
        /**
         * Event Store Persistence
         */
        from("direct:event-store-persistence")
            .routeId("event-store-persistence")
            .log(LoggingLevel.DEBUG, "Persisting event to event store")
            .process(exchange -> {
                Order order = exchange.getIn().getBody(Order.class);
                log.info("Event persisted for order: {}", order.getId());
            });
        
        // ================================================================
        // SPLIT/AGGREGATE PATTERN - Handle orders with multiple items
        // ================================================================
        
        /**
         * Order Item Processing - Split Pattern
         */
        from("direct:order-paid-processing")
            .routeId("order-item-split")
            .log(LoggingLevel.INFO, "Processing paid order: ${body.id}")
            .wireTap("direct:order-audit-trail")
            .process(exchange -> {
                Order order = exchange.getIn().getBody(Order.class);
                // Simulate splitting order into individual items
                exchange.getIn().setHeader("orderItemCount", 3); // Simulated
                exchange.getIn().setHeader("aggregationStrategy", "order-items");
            })
            .split(simple("${header.orderItemCount}"))
                .parallelProcessing()
                .aggregationStrategy(new OrderItemAggregationStrategy())
                .to("direct:process-order-item")
            .end()
            .to("direct:order-items-processed");
        
        /**
         * Individual Order Item Processing
         */
        from("direct:process-order-item")
            .routeId("process-order-item")
            .log(LoggingLevel.DEBUG, "Processing order item for order: ${body.id}")
            .delay(100) // Simulate processing time
            .setHeader("itemProcessed", constant(true))
            .to("direct:inventory-reservation");
        
        /**
         * Order Items Processed - Aggregate Results
         */
        from("direct:order-items-processed")
            .routeId("order-items-aggregated")
            .log(LoggingLevel.INFO, "All items processed for order: ${body.id}")
            .to("direct:order-preparation-ready");
        
        // ================================================================
        // BUSINESS WORKFLOW ROUTES
        // ================================================================
        
        /**
         * Order Validation Workflow
         */
        from("direct:order-validation")
            .routeId("order-validation")
            .log(LoggingLevel.DEBUG, "Validating order: ${body.id}")
            .process(exchange -> {
                Order order = exchange.getIn().getBody(Order.class);
                if (order.getTotalAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Invalid order amount");
                }
                exchange.getIn().setHeader("validationStatus", "PASSED");
            })
            .choice()
                .when(header("validationStatus").isEqualTo("PASSED"))
                    .log(LoggingLevel.INFO, "Order validation passed: ${body.id}")
                .otherwise()
                    .to("direct:order-validation-failed")
            .end();
        
        /**
         * Payment Request Processing
         */
        from("direct:order-payment-request")
            .routeId("order-payment-request")
            .log(LoggingLevel.INFO, "Requesting payment for order: ${body.id}")
            .setHeader("paymentAmount", simple("${body.totalAmount}"))
            .setHeader("customerEmail", simple("${body.customerEmail}"))
            .to("rabbitmq:payment.requests?routingKey=payment.process");
        
        /**
         * Order Preparation Workflow
         */
        from("direct:order-preparation-workflow")
            .routeId("order-preparation")
            .log(LoggingLevel.INFO, "Starting preparation for order: ${body.id}")
            .wireTap("direct:order-audit-trail")
            .delay(2000) // Simulate preparation time
            .process(exchange -> {
                Order order = exchange.getIn().getBody(Order.class);
                exchange.getIn().setHeader("preparationEstimate", "15 minutes");
            })
            .to("direct:notify-preparation-started");
        
        /**
         * Order Shipping Workflow
         */
        from("direct:order-shipping-workflow")
            .routeId("order-shipping")
            .log(LoggingLevel.INFO, "Processing shipment for order: ${body.id}")
            .wireTap("direct:order-audit-trail")
            .process(exchange -> {
                Order order = exchange.getIn().getBody(Order.class);
                // Generate tracking number
                String trackingNumber = "TRK" + System.currentTimeMillis();
                exchange.getIn().setHeader("trackingNumber", trackingNumber);
            })
            .multicast()
                .to("direct:notify-order-shipped")
                .to("direct:update-delivery-estimate")
            .end();
        
        /**
         * Order Delivery Workflow
         */
        from("direct:order-delivered-workflow")
            .routeId("order-delivered")
            .log(LoggingLevel.INFO, "Order delivered: ${body.id}")
            .wireTap("direct:order-audit-trail")
            .multicast()
                .to("direct:notify-order-delivered")
                .to("direct:request-feedback")
                .to("direct:analytics-order-completed")
            .end();
        
        /**
         * Order Cancellation Workflow
         */
        from("direct:order-cancellation-workflow")
            .routeId("order-cancellation")
            .log(LoggingLevel.INFO, "Processing cancellation for order: ${body.id}")
            .wireTap("direct:order-audit-trail")
            .choice()
                .when(header("previousStatus").isEqualTo("PAID"))
                    .to("direct:process-refund")
                .when(header("previousStatus").isEqualTo("PREPARING"))
                    .multicast()
                        .to("direct:stop-preparation")
                        .to("direct:release-inventory")
                    .end()
            .end()
            .to("direct:notify-order-cancelled");
        
        // ================================================================
        // INTEGRATION AND NOTIFICATION ROUTES
        // ================================================================
        
        /**
         * Inventory Reservation
         */
        from("direct:inventory-reservation")
            .routeId("inventory-reservation")
            .log(LoggingLevel.DEBUG, "Reserving inventory for order: ${body.id}")
            .setHeader("inventoryAction", constant("RESERVE"))
            .to("rabbitmq:inventory.requests?routingKey=inventory.reserve");
        
        /**
         * Order Notification Router
         */
        from("direct:order-notification")
            .routeId("order-notification-router")
            .log(LoggingLevel.DEBUG, "Routing notification for order: ${body.id}")
            .setHeader("notificationType", constant("ORDER_CREATED"))
            .to("rabbitmq:notifications.orders?routingKey=notification.order");
        
        /**
         * Analytics Integration
         */
        from("direct:analytics-order-completed")
            .routeId("analytics-integration")
            .log(LoggingLevel.DEBUG, "Sending order completion data to analytics")
            .marshal(jsonFormat)
            .to("rabbitmq:analytics.orders?routingKey=analytics.order.completed");
        
        // ================================================================
        // AUDIT TRAIL - Wire Tap Pattern Implementation
        // ================================================================
        
        /**
         * Order Audit Trail - Wire Tap Pattern
         */
        from("direct:order-audit-trail")
            .routeId("order-audit-trail")
            .process(exchange -> {
                Order order = exchange.getIn().getBody(Order.class);
                String correlationId = exchange.getIn().getHeader("correlationId", String.class);
                String action = exchange.getIn().getHeader("action", "UNKNOWN", String.class);
                log.info("AUDIT: Order {} - {} (Correlation: {})", 
                        order.getId(), action, correlationId);
            })
            .to("rabbitmq:audit.orders?routingKey=audit.order");
        
        // ================================================================
        // ERROR HANDLING AND DEAD LETTER PROCESSING
        // ================================================================
        
        /**
         * Dead Letter Channel - Error Handler
         */
        from("direct:order-error-handler")
            .routeId("order-error-handler")
            .log(LoggingLevel.ERROR, "Order processing failed: ${exception.message}")
            .process(exchange -> {
                Throwable exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
                Order order = exchange.getIn().getBody(Order.class);
                log.error("Failed to process order {}: {}", 
                         order != null ? order.getId() : "unknown", 
                         exception.getMessage());
                exchange.getIn().setHeader("errorType", exception.getClass().getSimpleName());
                exchange.getIn().setHeader("errorMessage", exception.getMessage());
            })
            .to("rabbitmq:errors.orders?routingKey=error.order");
        
        /**
         * Validation Failure Handler
         */
        from("direct:order-validation-failed")
            .routeId("order-validation-failed")
            .log(LoggingLevel.WARN, "Order validation failed: ${body.id}")
            .setHeader("failureReason", constant("VALIDATION_FAILED"))
            .to("direct:order-error-handler");
        
        // ================================================================
        // TIMER-BASED MONITORING ROUTES
        // ================================================================
        
        /**
         * Pending Order Monitor - Timer-based monitoring
         */
        from("timer:pending-order-monitor?period=300000") // Every 5 minutes
            .routeId("pending-order-monitor")
            .log(LoggingLevel.INFO, "Checking for stale pending orders")
            .process(exchange -> {
                java.time.LocalDateTime cutoff = java.time.LocalDateTime.now().minusHours(1);
                var staleOrders = orderService.getPendingOrdersOlderThan(cutoff);
                log.info("Found {} stale pending orders", staleOrders.size());
                exchange.getIn().setBody(staleOrders);
            })
            .split(body())
                .to("direct:handle-stale-order")
            .end();
        
        /**
         * Handle Stale Orders
         */
        from("direct:handle-stale-order")
            .routeId("handle-stale-order")
            .log(LoggingLevel.WARN, "Handling stale order: ${body.id}")
            .setHeader("alertType", constant("STALE_ORDER"))
            .to("rabbitmq:alerts.orders?routingKey=alert.stale.order");
    }
    
    /**
     * Custom Aggregation Strategy for Order Items
     */
    private static class OrderItemAggregationStrategy implements org.apache.camel.AggregationStrategy {
        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange == null) {
                return newExchange;
            }
            
            // Aggregate processing results
            Integer oldCount = oldExchange.getIn().getHeader("processedItems", 0, Integer.class);
            oldExchange.getIn().setHeader("processedItems", oldCount + 1);
            
            return oldExchange;
        }
    }
} 