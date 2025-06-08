package com.eipresso.orchestration.routes;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * Process Manager Routes - Implements Process Manager Pattern for complex workflow coordination
 * 
 * Advanced EIP Patterns Implemented:
 * 1. Process Manager Pattern - Stateful workflow coordination with state machines
 * 2. Scatter-Gather Pattern - Parallel service calls with correlation and aggregation
 * 3. Content-Based Router - Dynamic routing based on workflow state and business rules
 * 4. Aggregator Pattern - Collect and correlate responses from multiple services
 * 5. Timer-Based Events - Timeout handling and scheduled workflow monitoring
 */
@Component
public class ProcessManagerRoutes extends RouteBuilder {
    
    @Override
    public void configure() throws Exception {
        
        // Configure error handling for process manager
        onException(Exception.class)
            .handled(true)
            .log(LoggingLevel.ERROR, "Process manager error: ${exception.message}")
            .to("direct:process-manager-error-handler")
            .end();
        
        // ==========================================
        // PROCESS MANAGER PATTERN: Workflow Coordination
        // ==========================================
        
        /**
         * Workflow Instance Manager - Main coordination hub
         */
        from("direct:start-workflow")
            .routeId("workflow-instance-manager")
            .log(LoggingLevel.INFO, "Starting workflow: ${header.workflowType} for correlation: ${header.correlationId}")
            .to("direct:create-workflow-instance")
            .to("direct:route-by-workflow-type")
            .end();
        
        /**
         * Workflow Type Router - Content-Based Router Pattern
         */
        from("direct:route-by-workflow-type")
            .routeId("workflow-type-router")
            .choice()
                .when(header("workflowType").isEqualTo("ORDER_PROCESSING"))
                    .to("direct:order-processing-workflow")
                .when(header("workflowType").isEqualTo("CATERING_ORDER"))
                    .to("direct:catering-order-workflow")
                .when(header("workflowType").isEqualTo("REFUND_PROCESSING"))
                    .to("direct:refund-processing-workflow")
                .when(header("workflowType").isEqualTo("SUBSCRIPTION_MANAGEMENT"))
                    .to("direct:subscription-workflow")
                .otherwise()
                    .log(LoggingLevel.WARN, "Unknown workflow type: ${header.workflowType}")
                    .to("direct:unsupported-workflow-handler")
            .end();
        
        // ==========================================
        // SCATTER-GATHER PATTERN: Parallel Processing
        // ==========================================
        
        /**
         * Catering Order Workflow - Complex multi-supplier coordination
         */
        from("direct:catering-order-workflow")
            .routeId("catering-order-workflow")
            .log(LoggingLevel.INFO, "Starting catering order workflow: ${header.correlationId}")
            .setHeader("workflowStep", constant("CATERING_COORDINATION"))
            .to("direct:update-workflow-status")
            .multicast()
                .parallelProcessing()
                .to("direct:supplier-coordination")
                .to("direct:equipment-reservation")
                .to("direct:staff-scheduling")
                .to("direct:delivery-planning")
            .end()
            .to("direct:finalize-catering-order")
            .end();
        
        /**
         * Order Processing Workflow with Conditional Logic
         */
        from("direct:order-processing-workflow")
            .routeId("order-processing-workflow")
            .log(LoggingLevel.INFO, "Starting order processing workflow: ${header.correlationId}")
            .setHeader("workflowStep", constant("ORDER_VALIDATION"))
            .to("direct:validate-order-details")
            .choice()
                .when(header("orderValid").isEqualTo(true))
                    .to("direct:process-valid-order")
                .otherwise()
                    .to("direct:handle-invalid-order")
            .end();
        
        /**
         * Valid Order Processing Path
         */
        from("direct:process-valid-order")
            .routeId("process-valid-order")
            .setHeader("workflowStep", constant("ORDER_PROCESSING"))
            .to("direct:update-workflow-status")
            .choice()
                .when(header("orderAmount").convertTo(Double.class).isGreaterThan(1000.0))
                    .to("direct:high-value-order-processing")
                .when(header("customerType").isEqualTo("VIP"))
                    .to("direct:vip-order-processing")
                .when(header("rushOrder").isEqualTo(true))
                    .to("direct:expedited-order-processing")
                .otherwise()
                    .to("direct:standard-order-processing")
            .end();
        
        // ==========================================
        // TIMER-BASED PATTERNS: Workflow Monitoring
        // ==========================================
        
        /**
         * Workflow Timeout Monitor - Runs every 30 seconds
         */
        from("timer:workflow-timeout-monitor?period=30000")
            .routeId("workflow-timeout-monitor")
            .log(LoggingLevel.DEBUG, "Checking for workflow timeouts")
            .to("direct:check-workflow-timeouts")
            .split(body())
                .to("direct:handle-workflow-timeout")
            .end();
        
        /**
         * Workflow Instance Creator
         */
        from("direct:create-workflow-instance")
            .routeId("create-workflow-instance")
            .log(LoggingLevel.INFO, "Creating workflow instance: ${header.workflowType}")
            .setHeader("workflowId", simple("WF-${date:now:yyyyMMddHHmmss}-${random(1000,9999)}"))
            .setHeader("workflowStatus", constant("STARTED"))
            .to("mock:workflow-persistence")
            .end();
        
        /**
         * Workflow Status Updater
         */
        from("direct:update-workflow-status")
            .routeId("update-workflow-status")
            .log(LoggingLevel.DEBUG, "Updating workflow status: ${header.workflowStep} for ${header.workflowId}")
            .to("mock:workflow-status-update")
            .end();
        
        // Mock routes for testing
        from("direct:supplier-coordination")
            .routeId("supplier-coordination")
            .log("Supplier coordination for ${header.correlationId}")
            .to("mock:supplier-coordination")
            .end();
            
        from("direct:equipment-reservation")
            .routeId("equipment-reservation")
            .log("Equipment reservation for ${header.correlationId}")
            .to("mock:equipment-reservation")
            .end();
            
        from("direct:staff-scheduling")
            .routeId("staff-scheduling")
            .log("Staff scheduling for ${header.correlationId}")
            .to("mock:staff-scheduling")
            .end();
            
        from("direct:delivery-planning")
            .routeId("delivery-planning")
            .log("Delivery planning for ${header.correlationId}")
            .to("mock:delivery-planning")
            .end();
            
        from("direct:finalize-catering-order")
            .routeId("finalize-catering-order")
            .log("Finalizing catering order ${header.correlationId}")
            .to("mock:catering-finalization")
            .end();
            
        from("direct:validate-order-details")
            .routeId("validate-order-details")
            .log("Validating order details")
            .setHeader("orderValid", constant(true))
            .to("mock:order-validation")
            .end();
            
        from("direct:handle-invalid-order")
            .routeId("handle-invalid-order")
            .log("Handling invalid order")
            .to("mock:invalid-order-handler")
            .end();
            
        from("direct:high-value-order-processing")
            .routeId("high-value-order-processing")
            .log("Processing high-value order")
            .to("mock:high-value-processing")
            .end();
            
        from("direct:vip-order-processing")
            .routeId("vip-order-processing")
            .log("Processing VIP order")
            .to("mock:vip-processing")
            .end();
            
        from("direct:expedited-order-processing")
            .routeId("expedited-order-processing")
            .log("Processing expedited order")
            .to("mock:expedited-processing")
            .end();
            
        from("direct:standard-order-processing")
            .routeId("standard-order-processing")
            .log("Processing standard order")
            .to("mock:standard-processing")
            .end();
            
        from("direct:unsupported-workflow-handler")
            .routeId("unsupported-workflow-handler")
            .log("Handling unsupported workflow")
            .to("mock:unsupported-workflow")
            .end();
            
        from("direct:check-workflow-timeouts")
            .routeId("check-workflow-timeouts")
            .to("mock:timeout-check")
            .setBody(simple("[]"))
            .end();
            
        from("direct:handle-workflow-timeout")
            .routeId("handle-workflow-timeout")
            .log("Handling workflow timeout")
            .to("mock:timeout-handler")
            .end();
            
        from("direct:process-manager-error-handler")
            .routeId("process-manager-error-handler")
            .log(LoggingLevel.ERROR, "Process manager error: ${exception.message}")
            .to("mock:error-handler")
            .end();
    }
} 