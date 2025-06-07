package com.eipresso.order.controller;

import com.eipresso.order.model.*;
import com.eipresso.order.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.math.BigDecimal;

/**
 * Order Management REST Controller
 * Provides comprehensive order management endpoints with Event Sourcing integration
 */
@RestController
@RequestMapping("/api/orders")
@Validated
@CrossOrigin(origins = "*")
public class OrderController {
    
    private final OrderService orderService;
    
    @Autowired
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }
    
    // ================================================================
    // CORE ORDER OPERATIONS
    // ================================================================
    
    /**
     * Create a new order
     */
    @PostMapping
    public ResponseEntity<Order> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        try {
            Order order = orderService.createOrder(
                request.getUserId(),
                request.getTotalAmount(),
                request.getCustomerName(),
                request.getCustomerEmail(),
                request.getDeliveryAddress(),
                request.getSpecialInstructions()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(order);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Get order by ID
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<Order> getOrder(@PathVariable Long orderId) {
        Optional<Order> order = orderService.getOrderById(orderId);
        return order.map(o -> ResponseEntity.ok(o))
                   .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Get orders by user ID
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Order>> getOrdersByUserId(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        
        List<Order> orders = activeOnly 
            ? orderService.getActiveOrdersByUserId(userId)
            : orderService.getOrdersByUserId(userId);
        
        return ResponseEntity.ok(orders);
    }
    
    /**
     * Get orders by status
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<Order>> getOrdersByStatus(@PathVariable OrderStatus status) {
        List<Order> orders = orderService.getOrdersByStatus(status);
        return ResponseEntity.ok(orders);
    }
    
    // ================================================================
    // ORDER STATE MANAGEMENT
    // ================================================================
    
    /**
     * Transition order status
     */
    @PutMapping("/{orderId}/status")
    public ResponseEntity<Order> updateOrderStatus(
            @PathVariable Long orderId,
            @Valid @RequestBody StatusUpdateRequest request) {
        
        try {
            Order order = orderService.transitionOrderStatus(
                orderId,
                request.getNewStatus(),
                request.getEventSource(),
                request.getCorrelationId()
            );
            return ResponseEntity.ok(order);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Process payment for order
     */
    @PostMapping("/{orderId}/payment")
    public ResponseEntity<Order> processPayment(
            @PathVariable Long orderId,
            @Valid @RequestBody PaymentRequest request) {
        
        try {
            Order order = orderService.processPayment(
                orderId,
                request.getPaymentId(),
                request.getCorrelationId()
            );
            return ResponseEntity.ok(order);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Cancel order
     */
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<Order> cancelOrder(
            @PathVariable Long orderId,
            @Valid @RequestBody CancelOrderRequest request) {
        
        try {
            Order order = orderService.cancelOrder(
                orderId,
                request.getReason(),
                request.getSource(),
                request.getCorrelationId()
            );
            return ResponseEntity.ok(order);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    // ================================================================
    // EVENT SOURCING ENDPOINTS
    // ================================================================
    
    /**
     * Get order event history
     */
    @GetMapping("/{orderId}/events")
    public ResponseEntity<List<OrderEvent>> getOrderEventHistory(@PathVariable Long orderId) {
        List<OrderEvent> events = orderService.getOrderEventHistory(orderId);
        return ResponseEntity.ok(events);
    }
    
    /**
     * Get events by correlation ID
     */
    @GetMapping("/events/correlation/{correlationId}")
    public ResponseEntity<List<OrderEvent>> getEventsByCorrelationId(@PathVariable String correlationId) {
        List<OrderEvent> events = orderService.getEventsByCorrelationId(correlationId);
        return ResponseEntity.ok(events);
    }
    
    /**
     * Reconstruct order from events (Event Sourcing replay)
     */
    @GetMapping("/{orderId}/reconstruct")
    public ResponseEntity<Order> reconstructOrderFromEvents(@PathVariable Long orderId) {
        try {
            Order reconstructedOrder = orderService.reconstructOrderFromEvents(orderId);
            return ResponseEntity.ok(reconstructedOrder);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    // ================================================================
    // ANALYTICS AND REPORTING
    // ================================================================
    
    /**
     * Get order statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getOrderStatistics() {
        Map<String, Object> stats = Map.of(
            "pendingCount", orderService.getOrderCountByStatus(OrderStatus.PENDING),
            "paidCount", orderService.getOrderCountByStatus(OrderStatus.PAID),
            "preparingCount", orderService.getOrderCountByStatus(OrderStatus.PREPARING),
            "shippedCount", orderService.getOrderCountByStatus(OrderStatus.SHIPPED),
            "deliveredCount", orderService.getOrderCountByStatus(OrderStatus.DELIVERED),
            "cancelledCount", orderService.getOrderCountByStatus(OrderStatus.CANCELLED),
            "averageOrderValue", orderService.getAverageOrderValue().orElse(0.0)
        );
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Get revenue since date
     */
    @GetMapping("/revenue")
    public ResponseEntity<Map<String, Object>> getRevenue(
            @RequestParam(required = false) String since) {
        
        LocalDateTime sinceDate = since != null 
            ? LocalDateTime.parse(since)
            : LocalDateTime.now().minusDays(30);
        
        Optional<BigDecimal> revenue = orderService.getTotalRevenueSince(sinceDate);
        
        Map<String, Object> result = Map.of(
            "since", sinceDate,
            "totalRevenue", revenue.orElse(BigDecimal.ZERO)
        );
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Get orders ready for fulfillment
     */
    @GetMapping("/fulfillment/ready")
    public ResponseEntity<List<Order>> getOrdersReadyForFulfillment() {
        List<Order> orders = orderService.getOrdersReadyForFulfillment();
        return ResponseEntity.ok(orders);
    }
    
    /**
     * Get stale pending orders
     */
    @GetMapping("/pending/stale")
    public ResponseEntity<List<Order>> getStalePendingOrders(
            @RequestParam(defaultValue = "1") int hoursOld) {
        
        LocalDateTime cutoff = LocalDateTime.now().minusHours(hoursOld);
        List<Order> staleOrders = orderService.getPendingOrdersOlderThan(cutoff);
        return ResponseEntity.ok(staleOrders);
    }
    
    // ================================================================
    // HEALTH AND MONITORING
    // ================================================================
    
    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = Map.of(
            "status", "UP",
            "service", "order-management-service",
            "timestamp", LocalDateTime.now(),
            "version", "1.0.0",
            "activeOrders", orderService.getOrderCountByStatus(OrderStatus.PENDING) +
                           orderService.getOrderCountByStatus(OrderStatus.PAID) +
                           orderService.getOrderCountByStatus(OrderStatus.PREPARING) +
                           orderService.getOrderCountByStatus(OrderStatus.SHIPPED)
        );
        return ResponseEntity.ok(health);
    }
    
    // ================================================================
    // EXCEPTION HANDLERS
    // ================================================================
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        Map<String, String> error = Map.of(
            "error", "Invalid request",
            "message", e.getMessage()
        );
        return ResponseEntity.badRequest().body(error);
    }
    
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException e) {
        Map<String, String> error = Map.of(
            "error", "Invalid state transition",
            "message", e.getMessage()
        );
        return ResponseEntity.badRequest().body(error);
    }
    
    // ================================================================
    // REQUEST/RESPONSE DTOs
    // ================================================================
    
    public static class CreateOrderRequest {
        @NotNull(message = "User ID is required")
        private Long userId;
        
        @NotNull(message = "Total amount is required")
        @DecimalMin(value = "0.01", message = "Total amount must be positive")
        private BigDecimal totalAmount;
        
        @NotBlank(message = "Customer name is required")
        @Size(max = 100, message = "Customer name too long")
        private String customerName;
        
        @NotBlank(message = "Customer email is required")
        @Email(message = "Invalid email format")
        private String customerEmail;
        
        @Size(max = 500, message = "Delivery address too long")
        private String deliveryAddress;
        
        @Size(max = 1000, message = "Special instructions too long")
        private String specialInstructions;
        
        // Getters and setters
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        
        public BigDecimal getTotalAmount() { return totalAmount; }
        public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
        
        public String getCustomerName() { return customerName; }
        public void setCustomerName(String customerName) { this.customerName = customerName; }
        
        public String getCustomerEmail() { return customerEmail; }
        public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
        
        public String getDeliveryAddress() { return deliveryAddress; }
        public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }
        
        public String getSpecialInstructions() { return specialInstructions; }
        public void setSpecialInstructions(String specialInstructions) { this.specialInstructions = specialInstructions; }
    }
    
    public static class StatusUpdateRequest {
        @NotNull(message = "New status is required")
        private OrderStatus newStatus;
        
        @NotBlank(message = "Event source is required")
        private String eventSource;
        
        private String correlationId;
        
        // Getters and setters
        public OrderStatus getNewStatus() { return newStatus; }
        public void setNewStatus(OrderStatus newStatus) { this.newStatus = newStatus; }
        
        public String getEventSource() { return eventSource; }
        public void setEventSource(String eventSource) { this.eventSource = eventSource; }
        
        public String getCorrelationId() { return correlationId; }
        public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    }
    
    public static class PaymentRequest {
        @NotBlank(message = "Payment ID is required")
        private String paymentId;
        
        private String correlationId;
        
        // Getters and setters
        public String getPaymentId() { return paymentId; }
        public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
        
        public String getCorrelationId() { return correlationId; }
        public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    }
    
    public static class CancelOrderRequest {
        @NotBlank(message = "Cancellation reason is required")
        private String reason;
        
        @NotBlank(message = "Source is required")
        private String source;
        
        private String correlationId;
        
        // Getters and setters
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        
        public String getCorrelationId() { return correlationId; }
        public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    }
} 