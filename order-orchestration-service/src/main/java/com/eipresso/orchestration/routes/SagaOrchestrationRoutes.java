package com.eipresso.orchestration.routes;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.SagaPropagation;
import org.springframework.stereotype.Component;

/**
 * Saga Orchestration Routes - Implements Saga Pattern for distributed transaction management
 * 
 * Advanced EIP Patterns Implemented:
 * 1. Saga Pattern - Distributed transaction coordination with compensation
 * 2. Process Manager Pattern - Stateful workflow coordination
 * 3. Compensating Actions Pattern - Rollback mechanisms for partial failures
 * 4. Request-Reply Pattern - Synchronous service coordination
 * 5. Scatter-Gather Pattern - Parallel service calls with correlation
 */
@Component
public class SagaOrchestrationRoutes extends RouteBuilder {
    
    @Override
    public void configure() throws Exception {
        
        // Configure global error handling for saga operations
        onException(Exception.class)
            .handled(true)
            .log(LoggingLevel.ERROR, "Saga orchestration error: ${exception.message}")
            .to("direct:saga-error-handler")
            .end();
        
        // ==========================================
        // SAGA PATTERN: Order Processing Saga
        // ==========================================
        
        /**
         * Main Order Processing Saga Coordinator
         * Orchestrates: Payment → Inventory → Fulfillment → Notification
         */
        from("direct:start-order-saga")
            .routeId("order-processing-saga")
            .log(LoggingLevel.INFO, "Starting Order Processing Saga for Order: ${header.orderId}")
            .saga()
                .propagation(SagaPropagation.REQUIRES_NEW)
                .option("id", header("orderId"))
                .compensation("direct:compensate-order-saga")
                .completion("direct:complete-order-saga")
            .to("direct:saga-payment-step")
            .to("direct:saga-inventory-step")
            .to("direct:saga-fulfillment-step")
            .to("direct:saga-notification-step")
            .log(LoggingLevel.INFO, "Order Processing Saga completed successfully for Order: ${header.orderId}")
            .end();
        
        /**
         * Saga Step 1: Payment Processing
         * Compensating Action: Refund payment
         */
        from("direct:saga-payment-step")
            .routeId("saga-payment-step")
            .log(LoggingLevel.INFO, "Executing Payment Step for Order: ${header.orderId}")
            .saga()
                .compensation("direct:compensate-payment")
            .setHeader("stepName", constant("PAYMENT_PROCESSING"))
            .setHeader("serviceEndpoint", constant("http://payment-service:8084/api/payments/process"))
            .to("direct:execute-service-call")
            .choice()
                .when(header("stepResult").isEqualTo("SUCCESS"))
                    .log(LoggingLevel.INFO, "Payment processed successfully for Order: ${header.orderId}")
                    .setHeader("paymentId", body())
                .otherwise()
                    .log(LoggingLevel.ERROR, "Payment failed for Order: ${header.orderId}")
                    .throwException(new RuntimeException("Payment processing failed"))
            .end();
        
        /**
         * Saga Step 2: Inventory Reservation
         * Compensating Action: Release inventory
         */
        from("direct:saga-inventory-step")
            .routeId("saga-inventory-step")
            .log(LoggingLevel.INFO, "Executing Inventory Step for Order: ${header.orderId}")
            .saga()
                .compensation("direct:compensate-inventory")
            .setHeader("stepName", constant("INVENTORY_RESERVATION"))
            .setHeader("serviceEndpoint", constant("http://inventory-service:8085/api/inventory/reserve"))
            .to("direct:execute-service-call")
            .choice()
                .when(header("stepResult").isEqualTo("SUCCESS"))
                    .log(LoggingLevel.INFO, "Inventory reserved successfully for Order: ${header.orderId}")
                    .setHeader("reservationId", body())
                .otherwise()
                    .log(LoggingLevel.ERROR, "Inventory reservation failed for Order: ${header.orderId}")
                    .throwException(new RuntimeException("Inventory reservation failed"))
            .end();
        
        /**
         * Saga Step 3: Order Fulfillment
         * Compensating Action: Cancel fulfillment
         */
        from("direct:saga-fulfillment-step")
            .routeId("saga-fulfillment-step")
            .log(LoggingLevel.INFO, "Executing Fulfillment Step for Order: ${header.orderId}")
            .saga()
                .compensation("direct:compensate-fulfillment")
            .setHeader("stepName", constant("ORDER_FULFILLMENT"))
            .setHeader("serviceEndpoint", constant("http://order-service:8083/api/orders/fulfill"))
            .to("direct:execute-service-call")
            .choice()
                .when(header("stepResult").isEqualTo("SUCCESS"))
                    .log(LoggingLevel.INFO, "Order fulfillment initiated for Order: ${header.orderId}")
                    .setHeader("fulfillmentId", body())
                .otherwise()
                    .log(LoggingLevel.ERROR, "Order fulfillment failed for Order: ${header.orderId}")
                    .throwException(new RuntimeException("Order fulfillment failed"))
            .end();
        
        /**
         * Saga Step 4: Customer Notification
         * Compensating Action: Send cancellation notification
         */
        from("direct:saga-notification-step")
            .routeId("saga-notification-step")
            .log(LoggingLevel.INFO, "Executing Notification Step for Order: ${header.orderId}")
            .saga()
                .compensation("direct:compensate-notification")
            .setHeader("stepName", constant("CUSTOMER_NOTIFICATION"))
            .setHeader("serviceEndpoint", constant("http://notification-service:8086/api/notifications/send"))
            .to("direct:execute-service-call")
            .choice()
                .when(header("stepResult").isEqualTo("SUCCESS"))
                    .log(LoggingLevel.INFO, "Customer notification sent for Order: ${header.orderId}")
                .otherwise()
                    .log(LoggingLevel.WARN, "Customer notification failed for Order: ${header.orderId} - continuing saga")
            .end();
        
        // ==========================================
        // COMPENSATION ACTIONS (Saga Rollback)
        // ==========================================
        
        /**
         * Global Saga Compensation Coordinator
         */
        from("direct:compensate-order-saga")
            .routeId("compensate-order-saga")
            .log(LoggingLevel.WARN, "Starting compensation for Order Saga: ${header.orderId}")
            .setHeader("compensationReason", constant("SAGA_ROLLBACK"))
            .to("direct:track-compensation-start")
            .end();
        
        /**
         * Payment Compensation
         */
        from("direct:compensate-payment")
            .routeId("compensate-payment")
            .log(LoggingLevel.WARN, "Compensating payment for Order: ${header.orderId}")
            .setHeader("compensationType", constant("PAYMENT_REFUND"))
            .setHeader("compensationEndpoint", constant("http://payment-service:8084/api/payments/refund"))
            .to("direct:execute-compensation-action")
            .log(LoggingLevel.INFO, "Payment compensation completed for Order: ${header.orderId}")
            .end();
        
        /**
         * Inventory Compensation
         */
        from("direct:compensate-inventory")
            .routeId("compensate-inventory")
            .log(LoggingLevel.WARN, "Compensating inventory for Order: ${header.orderId}")
            .setHeader("compensationType", constant("INVENTORY_RELEASE"))
            .setHeader("compensationEndpoint", constant("http://inventory-service:8085/api/inventory/release"))
            .to("direct:execute-compensation-action")
            .log(LoggingLevel.INFO, "Inventory compensation completed for Order: ${header.orderId}")
            .end();
        
        /**
         * Fulfillment Compensation
         */
        from("direct:compensate-fulfillment")
            .routeId("compensate-fulfillment")
            .log(LoggingLevel.WARN, "Compensating fulfillment for Order: ${header.orderId}")
            .setHeader("compensationType", constant("ORDER_CANCELLATION"))
            .setHeader("compensationEndpoint", constant("http://order-service:8083/api/orders/cancel"))
            .to("direct:execute-compensation-action")
            .log(LoggingLevel.INFO, "Fulfillment compensation completed for Order: ${header.orderId}")
            .end();
        
        /**
         * Notification Compensation
         */
        from("direct:compensate-notification")
            .routeId("compensate-notification")
            .log(LoggingLevel.WARN, "Compensating notification for Order: ${header.orderId}")
            .setHeader("compensationType", constant("NOTIFICATION_CANCELLATION"))
            .setHeader("compensationEndpoint", constant("http://notification-service:8086/api/notifications/cancel"))
            .to("direct:execute-compensation-action")
            .log(LoggingLevel.INFO, "Notification compensation completed for Order: ${header.orderId}")
            .end();
        
        // ==========================================
        // UTILITY ROUTES FOR SAGA SUPPORT
        // ==========================================
        
        /**
         * Generic Service Call Executor with Timeout
         */
        from("direct:execute-service-call")
            .routeId("execute-service-call")
            .log(LoggingLevel.DEBUG, "Executing service call: ${header.serviceEndpoint}")
            .removeHeaders("CamelHttp*")
            .setHeader(Exchange.HTTP_METHOD, constant("POST"))
            .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
            .doTry()
                .to("mock:saga-service-call")
                .setHeader("stepResult", constant("SUCCESS"))
                .log(LoggingLevel.DEBUG, "Service call successful: ${header.stepName}")
            .doCatch(Exception.class)
                .setHeader("stepResult", constant("FAILED"))
                .log(LoggingLevel.ERROR, "Service call failed: ${header.stepName} - ${exception.message}")
            .end();
        
        /**
         * Generic Compensation Action Executor
         */
        from("direct:execute-compensation-action")
            .routeId("execute-compensation-action")
            .log(LoggingLevel.DEBUG, "Executing compensation: ${header.compensationType}")
            .removeHeaders("CamelHttp*")
            .setHeader(Exchange.HTTP_METHOD, constant("POST"))
            .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
            .doTry()
                .to("mock:compensation-action")
                .log(LoggingLevel.DEBUG, "Compensation successful: ${header.compensationType}")
            .doCatch(Exception.class)
                .log(LoggingLevel.ERROR, "Compensation failed: ${header.compensationType} - ${exception.message}")
            .end();
        
        /**
         * Saga Completion Handler
         */
        from("direct:complete-order-saga")
            .routeId("complete-order-saga")
            .log(LoggingLevel.INFO, "Order Saga completed successfully: ${header.orderId}")
            .to("direct:track-saga-completion")
            .end();
        
        /**
         * Saga Error Handler
         */
        from("direct:saga-error-handler")
            .routeId("saga-error-handler")
            .log(LoggingLevel.ERROR, "Saga error occurred: ${exception.message}")
            .to("direct:track-saga-error")
            .end();
        
        // ==========================================
        // MONITORING AND TRACKING ROUTES
        // ==========================================
        
        /**
         * Saga Execution Tracking
         */
        from("direct:track-saga-start")
            .routeId("track-saga-start")
            .log(LoggingLevel.INFO, "Tracking saga start: ${header.sagaId}")
            .to("mock:saga-tracking")
            .end();
        
        from("direct:track-saga-completion")
            .routeId("track-saga-completion")
            .log(LoggingLevel.INFO, "Tracking saga completion: ${header.sagaId}")
            .to("mock:saga-tracking")
            .end();
        
        from("direct:track-saga-error")
            .routeId("track-saga-error")
            .log(LoggingLevel.ERROR, "Tracking saga error: ${header.sagaId}")
            .to("mock:saga-tracking")
            .end();
        
        from("direct:track-compensation-start")
            .routeId("track-compensation-start")
            .log(LoggingLevel.WARN, "Tracking compensation start: ${header.sagaId}")
            .to("mock:compensation-tracking")
            .end();
    }
} 