package com.eipresso.order.service;

import com.eipresso.order.model.*;
import com.eipresso.order.repository.OrderRepository;
import com.eipresso.order.repository.OrderEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.CamelContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;

/**
 * Order Service - System of Record for Order States
 * Implements Event Sourcing, EIP patterns, and comprehensive order lifecycle management
 */
@Service
@Transactional
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final OrderEventRepository orderEventRepository;
    private final ProducerTemplate producerTemplate;
    private final CamelContext camelContext;
    
    @Autowired
    public OrderService(OrderRepository orderRepository, 
                       OrderEventRepository orderEventRepository,
                       ProducerTemplate producerTemplate,
                       CamelContext camelContext) {
        this.orderRepository = orderRepository;
        this.orderEventRepository = orderEventRepository;
        this.producerTemplate = producerTemplate;
        this.camelContext = camelContext;
    }
    
    // Core Order Operations
    
    /**
     * Create a new order with Event Sourcing
     */
    public Order createOrder(Long userId, BigDecimal totalAmount, 
                           String customerName, String customerEmail,
                           String deliveryAddress, String specialInstructions) {
        
        String correlationId = UUID.randomUUID().toString();
        
        // Create order entity
        Order order = new Order(userId, totalAmount, customerName, customerEmail);
        order.setDeliveryAddress(deliveryAddress);
        order.setSpecialInstructions(specialInstructions);
        
        // Save order
        Order savedOrder = orderRepository.save(order);
        
        // Create and save creation event
        OrderEvent creationEvent = OrderEvent.orderCreated(
            savedOrder.getId(), userId, correlationId);
        creationEvent.setSequenceNumber(1L);
        orderEventRepository.save(creationEvent);
        
        // Trigger Camel route for order processing
        producerTemplate.sendBodyAndHeader(
            "direct:order-created", 
            savedOrder,
            "correlationId", correlationId
        );
        
        return savedOrder;
    }
    
    /**
     * Transition order status with Event Sourcing
     */
    public Order transitionOrderStatus(Long orderId, OrderStatus newStatus, 
                                     String eventSource, String correlationId) {
        
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            throw new IllegalArgumentException("Order not found: " + orderId);
        }
        
        Order order = orderOpt.get();
        OrderStatus previousStatus = order.getStatus();
        
        // Validate state transition
        if (!order.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                String.format("Invalid transition from %s to %s for order %d", 
                             previousStatus, newStatus, orderId));
        }
        
        // Update order status
        order.transitionTo(newStatus);
        Order updatedOrder = orderRepository.save(order);
        
        // Create and save status change event
        Long nextSequenceNumber = getNextSequenceNumber(orderId);
        OrderEvent statusEvent = OrderEvent.statusChanged(
            orderId, previousStatus, newStatus, eventSource, correlationId);
        statusEvent.setSequenceNumber(nextSequenceNumber);
        orderEventRepository.save(statusEvent);
        
        // Trigger appropriate Camel routes based on new status
        triggerStatusChangeRoute(updatedOrder, previousStatus, newStatus, correlationId);
        
        return updatedOrder;
    }
    
    /**
     * Process payment confirmation
     */
    public Order processPayment(Long orderId, String paymentId, String correlationId) {
        return transitionOrderStatus(orderId, OrderStatus.PAID, "payment-service", correlationId);
    }
    
    /**
     * Cancel order with reason
     */
    public Order cancelOrder(Long orderId, String reason, String source, String correlationId) {
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            throw new IllegalArgumentException("Order not found: " + orderId);
        }
        
        Order order = orderOpt.get();
        if (!order.getStatus().isCancellable()) {
            throw new IllegalStateException(
                String.format("Order %d cannot be cancelled in status %s", 
                             orderId, order.getStatus()));
        }
        
        OrderStatus previousStatus = order.getStatus();
        order.transitionTo(OrderStatus.CANCELLED);
        Order cancelledOrder = orderRepository.save(order);
        
        // Create cancellation event with reason
        Long nextSequenceNumber = getNextSequenceNumber(orderId);
        OrderEvent cancellationEvent = OrderEvent.cancelled(
            orderId, previousStatus, reason, source, correlationId);
        cancellationEvent.setSequenceNumber(nextSequenceNumber);
        orderEventRepository.save(cancellationEvent);
        
        // Trigger cancellation processing
        producerTemplate.sendBodyAndHeaders(
            "direct:order-cancelled",
            cancelledOrder,
            java.util.Map.of(
                "correlationId", correlationId,
                "previousStatus", previousStatus.name(),
                "reason", reason
            )
        );
        
        return cancelledOrder;
    }
    
    // Query Methods
    
    public List<Order> getOrdersByUserId(Long userId) {
        return orderRepository.findByUserId(userId);
    }
    
    public List<Order> getActiveOrdersByUserId(Long userId) {
        return orderRepository.findActiveOrdersByUserId(userId);
    }
    
    public List<Order> getOrdersByStatus(OrderStatus status) {
        return orderRepository.findByStatus(status);
    }
    
    public Optional<Order> getOrderById(Long orderId) {
        return orderRepository.findById(orderId);
    }
    
    public Optional<Order> getOrderByIdAndUserId(Long orderId, Long userId) {
        return orderRepository.findByIdAndUserId(orderId, userId);
    }
    
    // Event Sourcing Methods
    
    public List<OrderEvent> getOrderEventHistory(Long orderId) {
        return orderEventRepository.findOrderEventHistory(orderId);
    }
    
    public List<OrderEvent> getEventsByCorrelationId(String correlationId) {
        return orderEventRepository.findEventsByCorrelationId(correlationId);
    }
    
    /**
     * Reconstruct order state from events (Event Sourcing replay)
     */
    public Order reconstructOrderFromEvents(Long orderId) {
        List<OrderEvent> events = getOrderEventHistory(orderId);
        if (events.isEmpty()) {
            throw new IllegalArgumentException("No events found for order: " + orderId);
        }
        
        // This is a simplified reconstruction - in a real system,
        // you'd have more sophisticated event replay logic
        Order reconstructedOrder = new Order();
        reconstructedOrder.setId(orderId);
        
        for (OrderEvent event : events) {
            applyEventToOrder(reconstructedOrder, event);
        }
        
        return reconstructedOrder;
    }
    
    // Analytics Methods
    
    public Long getOrderCountByStatus(OrderStatus status) {
        return orderRepository.countByStatus(status);
    }
    
    public Optional<BigDecimal> getTotalRevenueSince(LocalDateTime since) {
        return orderRepository.getTotalRevenueSince(since);
    }
    
    public Optional<Double> getAverageOrderValue() {
        return orderRepository.getAverageOrderValue();
    }
    
    public List<Order> getPendingOrdersOlderThan(LocalDateTime cutoffTime) {
        return orderRepository.findPendingOrdersOlderThan(cutoffTime);
    }
    
    public List<Order> getOrdersReadyForFulfillment() {
        return orderRepository.findOrdersReadyForFulfillment();
    }
    
    // Private Helper Methods
    
    private Long getNextSequenceNumber(Long orderId) {
        Optional<Long> maxSequence = orderEventRepository.findMaxSequenceNumberForOrder(orderId);
        return maxSequence.orElse(0L) + 1;
    }
    
    private void triggerStatusChangeRoute(Order order, OrderStatus previousStatus, 
                                        OrderStatus newStatus, String correlationId) {
        String routeEndpoint = switch (newStatus) {
            case PAID -> "direct:order-paid";
            case PREPARING -> "direct:order-preparing";
            case SHIPPED -> "direct:order-shipped";
            case DELIVERED -> "direct:order-delivered";
            case CANCELLED -> "direct:order-cancelled";
            default -> "direct:order-status-changed";
        };
        
        producerTemplate.sendBodyAndHeaders(
            routeEndpoint,
            order,
            java.util.Map.of(
                "correlationId", correlationId,
                "previousStatus", previousStatus.name(),
                "newStatus", newStatus.name()
            )
        );
    }
    
    private void applyEventToOrder(Order order, OrderEvent event) {
        // Simplified event application - in a real system,
        // this would be more comprehensive
        switch (event.getEventType()) {
            case CREATED:
                order.setStatus(OrderStatus.PENDING);
                order.setUserId(event.getUserId());
                order.setCreatedAt(event.getTimestamp());
                break;
            case STATUS_CHANGED:
                if (event.getNewStatus() != null) {
                    order.setStatus(event.getNewStatus());
                }
                order.setUpdatedAt(event.getTimestamp());
                break;
            // Add more event types as needed
        }
    }
} 