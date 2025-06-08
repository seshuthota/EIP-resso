package com.eipresso.orchestration.controller;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Order Orchestration Controller - REST API for Saga and Process Manager patterns
 * 
 * Demonstrates:
 * - Saga Pattern initiation and coordination
 * - Process Manager workflow execution
 * - Distributed transaction management
 * - Compensation and rollback mechanisms
 */
@RestController
@RequestMapping("/api/orchestration")
@CrossOrigin(origins = "*")
public class OrchestrationController {
    
    @Autowired
    private ProducerTemplate producerTemplate;
    
    @Autowired
    private CamelContext camelContext;
    
    // ==========================================
    // SAGA PATTERN ENDPOINTS
    // ==========================================
    
    /**
     * Start Order Processing Saga
     * Demonstrates complete distributed transaction with compensation
     */
    @PostMapping("/saga/order")
    public ResponseEntity<Map<String, Object>> startOrderSaga(@RequestBody Map<String, Object> orderRequest) {
        String orderId = "ORD-" + System.currentTimeMillis();
        String correlationId = "SAGA-" + UUID.randomUUID().toString();
        
        Map<String, Object> headers = new HashMap<>();
        headers.put("orderId", orderId);
        headers.put("correlationId", correlationId);
        headers.put("customerId", orderRequest.get("customerId"));
        headers.put("orderAmount", orderRequest.get("orderAmount"));
        headers.put("customerType", orderRequest.getOrDefault("customerType", "REGULAR"));
        
        try {
            // Start the order processing saga
            Object result = producerTemplate.requestBodyAndHeaders("direct:start-order-saga", orderRequest, headers);
            
            Map<String, Object> response = new HashMap<>();
            response.put("sagaId", correlationId);
            response.put("orderId", orderId);
            response.put("status", "SAGA_STARTED");
            response.put("message", "Order processing saga initiated successfully");
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("sagaId", correlationId);
            errorResponse.put("orderId", orderId);
            errorResponse.put("status", "SAGA_FAILED");
            errorResponse.put("error", e.getMessage());
            errorResponse.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * Simulate Saga Failure to test compensation
     */
    @PostMapping("/saga/order/fail")
    public ResponseEntity<Map<String, Object>> startFailingSaga(@RequestBody Map<String, Object> orderRequest) {
        String orderId = "ORD-FAIL-" + System.currentTimeMillis();
        String correlationId = "SAGA-FAIL-" + UUID.randomUUID().toString();
        
        Map<String, Object> headers = new HashMap<>();
        headers.put("orderId", orderId);
        headers.put("correlationId", correlationId);
        headers.put("simulateFailure", true);
        headers.put("failureStep", orderRequest.getOrDefault("failureStep", "INVENTORY"));
        
        try {
            Object result = producerTemplate.requestBodyAndHeaders("direct:start-order-saga", orderRequest, headers);
            
            Map<String, Object> response = new HashMap<>();
            response.put("sagaId", correlationId);
            response.put("orderId", orderId);
            response.put("status", "SAGA_COMPENSATION_TRIGGERED");
            response.put("message", "Saga failure simulation triggered compensation");
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("sagaId", correlationId);
            errorResponse.put("status", "COMPENSATION_EXECUTED");
            errorResponse.put("error", e.getMessage());
            errorResponse.put("message", "Saga failed and compensation was executed");
            errorResponse.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(errorResponse); // Return 200 as this is expected behavior
        }
    }
    
    // ==========================================
    // PROCESS MANAGER PATTERN ENDPOINTS
    // ==========================================
    
    /**
     * Start Generic Workflow using Process Manager
     */
    @PostMapping("/workflow/start")
    public ResponseEntity<Map<String, Object>> startWorkflow(@RequestBody Map<String, Object> workflowRequest) {
        String workflowType = (String) workflowRequest.get("workflowType");
        String correlationId = "WF-" + UUID.randomUUID().toString();
        
        Map<String, Object> headers = new HashMap<>();
        headers.put("workflowType", workflowType);
        headers.put("correlationId", correlationId);
        headers.putAll(workflowRequest);
        
        try {
            Object result = producerTemplate.requestBodyAndHeaders("direct:start-workflow", workflowRequest, headers);
            
            Map<String, Object> response = new HashMap<>();
            response.put("workflowId", correlationId);
            response.put("workflowType", workflowType);
            response.put("status", "WORKFLOW_STARTED");
            response.put("message", "Workflow initiated successfully");
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("workflowId", correlationId);
            errorResponse.put("workflowType", workflowType);
            errorResponse.put("status", "WORKFLOW_FAILED");
            errorResponse.put("error", e.getMessage());
            errorResponse.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * Start Catering Order Workflow (Scatter-Gather Pattern)
     */
    @PostMapping("/workflow/catering")
    public ResponseEntity<Map<String, Object>> startCateringWorkflow(@RequestBody Map<String, Object> cateringRequest) {
        String correlationId = "CATERING-" + UUID.randomUUID().toString();
        
        Map<String, Object> headers = new HashMap<>();
        headers.put("workflowType", "CATERING_ORDER");
        headers.put("correlationId", correlationId);
        headers.put("orderSize", cateringRequest.get("orderSize"));
        headers.put("eventDate", cateringRequest.get("eventDate"));
        headers.put("guestCount", cateringRequest.get("guestCount"));
        headers.put("supplierList", cateringRequest.getOrDefault("suppliers", "supplier1,supplier2,supplier3"));
        
        try {
            Object result = producerTemplate.requestBodyAndHeaders("direct:catering-order-workflow", cateringRequest, headers);
            
            Map<String, Object> response = new HashMap<>();
            response.put("workflowId", correlationId);
            response.put("workflowType", "CATERING_ORDER");
            response.put("status", "CATERING_WORKFLOW_STARTED");
            response.put("message", "Catering order workflow with parallel processing initiated");
            response.put("parallelBranches", new String[]{"SUPPLIER_COORDINATION", "EQUIPMENT_RESERVATION", "STAFF_SCHEDULING", "DELIVERY_PLANNING"});
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("workflowId", correlationId);
            errorResponse.put("status", "CATERING_WORKFLOW_FAILED");
            errorResponse.put("error", e.getMessage());
            errorResponse.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * Start Order Processing Workflow with Business Rules
     */
    @PostMapping("/workflow/order")
    public ResponseEntity<Map<String, Object>> startOrderWorkflow(@RequestBody Map<String, Object> orderRequest) {
        String correlationId = "ORDER-WF-" + UUID.randomUUID().toString();
        
        Map<String, Object> headers = new HashMap<>();
        headers.put("workflowType", "ORDER_PROCESSING");
        headers.put("correlationId", correlationId);
        headers.put("orderAmount", orderRequest.get("orderAmount"));
        headers.put("customerType", orderRequest.getOrDefault("customerType", "REGULAR"));
        headers.put("rushOrder", orderRequest.getOrDefault("rushOrder", false));
        
        try {
            Object result = producerTemplate.requestBodyAndHeaders("direct:order-processing-workflow", orderRequest, headers);
            
            Map<String, Object> response = new HashMap<>();
            response.put("workflowId", correlationId);
            response.put("workflowType", "ORDER_PROCESSING");
            response.put("status", "ORDER_WORKFLOW_STARTED");
            response.put("message", "Order processing workflow with business rules initiated");
            response.put("businessRules", determineBusinessRules(orderRequest));
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("workflowId", correlationId);
            errorResponse.put("status", "ORDER_WORKFLOW_FAILED");
            errorResponse.put("error", e.getMessage());
            errorResponse.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    // ==========================================
    // MONITORING AND STATUS ENDPOINTS
    // ==========================================
    
    /**
     * Get service health and active routes
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("service", "Order Orchestration Service");
        health.put("port", 8089);
        health.put("status", "UP");
        health.put("patterns", new String[]{"Saga Pattern", "Process Manager Pattern", "Scatter-Gather Pattern", "Compensating Actions Pattern"});
        health.put("activeRoutes", camelContext.getRoutes().size());
        health.put("camelVersion", camelContext.getVersion());
        health.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(health);
    }
    
    /**
     * Get active Camel routes information
     */
    @GetMapping("/routes")
    public ResponseEntity<Map<String, Object>> getRoutes() {
        Map<String, Object> routeInfo = new HashMap<>();
        routeInfo.put("totalRoutes", camelContext.getRoutes().size());
        
        Map<String, String> routes = new HashMap<>();
        camelContext.getRoutes().forEach(route -> {
            routes.put(route.getId(), route.getEndpoint().getEndpointUri());
        });
        
        routeInfo.put("routes", routes);
        routeInfo.put("sagaRoutes", routes.keySet().stream().filter(id -> id.contains("saga")).count());
        routeInfo.put("workflowRoutes", routes.keySet().stream().filter(id -> id.contains("workflow")).count());
        routeInfo.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(routeInfo);
    }
    
    /**
     * Get EIP patterns demonstration endpoints
     */
    @GetMapping("/patterns")
    public ResponseEntity<Map<String, Object>> getPatterns() {
        Map<String, Object> patterns = new HashMap<>();
        
        Map<String, Object> sagaPattern = new HashMap<>();
        sagaPattern.put("name", "Saga Pattern");
        sagaPattern.put("description", "Distributed transaction management with compensation");
        sagaPattern.put("endpoints", new String[]{"/saga/order", "/saga/order/fail"});
        sagaPattern.put("features", new String[]{"Compensation", "Rollback", "Transaction Coordination"});
        
        Map<String, Object> processManagerPattern = new HashMap<>();
        processManagerPattern.put("name", "Process Manager Pattern");
        processManagerPattern.put("description", "Stateful workflow coordination with business rules");
        processManagerPattern.put("endpoints", new String[]{"/workflow/start", "/workflow/catering", "/workflow/order"});
        processManagerPattern.put("features", new String[]{"State Management", "Business Rules", "Workflow Coordination"});
        
        Map<String, Object> scatterGatherPattern = new HashMap<>();
        scatterGatherPattern.put("name", "Scatter-Gather Pattern");
        scatterGatherPattern.put("description", "Parallel service calls with correlation and aggregation");
        scatterGatherPattern.put("endpoints", new String[]{"/workflow/catering"});
        scatterGatherPattern.put("features", new String[]{"Parallel Processing", "Response Aggregation", "Correlation"});
        
        Map<String, Object> compensatingActionsPattern = new HashMap<>();
        compensatingActionsPattern.put("name", "Compensating Actions Pattern");
        compensatingActionsPattern.put("description", "Rollback mechanisms for partial failures");
        compensatingActionsPattern.put("endpoints", new String[]{"/saga/order/fail"});
        compensatingActionsPattern.put("features", new String[]{"Rollback", "Compensation", "Error Recovery"});
        
        patterns.put("sagaPattern", sagaPattern);
        patterns.put("processManagerPattern", processManagerPattern);
        patterns.put("scatterGatherPattern", scatterGatherPattern);
        patterns.put("compensatingActionsPattern", compensatingActionsPattern);
        patterns.put("totalPatterns", 4);
        patterns.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(patterns);
    }
    
    // ==========================================
    // UTILITY METHODS
    // ==========================================
    
    private String[] determineBusinessRules(Map<String, Object> orderRequest) {
        Double orderAmount = orderRequest.get("orderAmount") != null ? 
            Double.valueOf(orderRequest.get("orderAmount").toString()) : 0.0;
        String customerType = (String) orderRequest.getOrDefault("customerType", "REGULAR");
        Boolean rushOrder = (Boolean) orderRequest.getOrDefault("rushOrder", false);
        
        if (orderAmount > 1000.0) {
            return new String[]{"HIGH_VALUE_ORDER", "MANAGER_APPROVAL_REQUIRED", "ENHANCED_FRAUD_CHECK"};
        } else if ("VIP".equals(customerType)) {
            return new String[]{"VIP_PROCESSING", "PRIORITY_HANDLING", "PERSONAL_SERVICE"};
        } else if (rushOrder) {
            return new String[]{"EXPEDITED_PROCESSING", "RUSH_FULFILLMENT", "PRIORITY_QUEUE"};
        } else {
            return new String[]{"STANDARD_PROCESSING", "REGULAR_FULFILLMENT"};
        }
    }
} 