package com.eipresso.order;

import com.eipresso.order.model.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple tests for Order Management domain models
 */
class OrderModelTest {

    @Test
    void testOrderStatusTransitions() {
        // Test valid transitions
        assertTrue(OrderStatus.PENDING.canTransitionTo(OrderStatus.PAID));
        assertTrue(OrderStatus.PAID.canTransitionTo(OrderStatus.PREPARING));
        assertTrue(OrderStatus.PREPARING.canTransitionTo(OrderStatus.SHIPPED));
        assertTrue(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.DELIVERED));
        
        // Test invalid transitions
        assertFalse(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.PENDING));
        assertFalse(OrderStatus.CANCELLED.canTransitionTo(OrderStatus.PAID));
        
        // Test terminal states
        assertTrue(OrderStatus.DELIVERED.isTerminal());
        assertTrue(OrderStatus.CANCELLED.isTerminal());
        assertFalse(OrderStatus.PENDING.isTerminal());
    }
    
    @Test
    void testOrderCreation() {
        Order order = new Order(1L, new BigDecimal("25.99"), "John Doe", "john@example.com");
        
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertEquals(new BigDecimal("25.99"), order.getTotalAmount());
        assertEquals("John Doe", order.getCustomerName());
        assertEquals("john@example.com", order.getCustomerEmail());
        assertTrue(order.isActive());
        assertFalse(order.isPaid());
        assertNotNull(order.getCreatedAt());
        assertNotNull(order.getUpdatedAt());
    }
    
    @Test
    void testOrderStateTransition() {
        Order order = new Order(1L, new BigDecimal("25.99"), "John Doe", "john@example.com");
        
        // Valid transition
        assertTrue(order.canTransitionTo(OrderStatus.PAID));
        order.transitionTo(OrderStatus.PAID);
        assertEquals(OrderStatus.PAID, order.getStatus());
        assertTrue(order.isPaid());
        
        // Invalid transition should throw exception
        assertThrows(IllegalStateException.class, () -> {
            order.transitionTo(OrderStatus.PENDING);
        });
    }
    
    @Test
    void testOrderEventCreation() {
        OrderEvent event = OrderEvent.orderCreated(1L, 100L, "correlation-123");
        
        assertEquals(1L, event.getOrderId());
        assertEquals(100L, event.getUserId());
        assertEquals("correlation-123", event.getCorrelationId());
        assertEquals(OrderEventType.CREATED, event.getEventType());
        assertEquals(OrderStatus.PENDING, event.getNewStatus());
        assertNotNull(event.getTimestamp());
    }
    
    @Test
    void testOrderEventTypes() {
        assertTrue(OrderEventType.PAYMENT_PROCESSED.isPaymentRelated());
        assertTrue(OrderEventType.SHIPPED.isFulfillmentRelated());
        assertTrue(OrderEventType.CREATED.requiresNotification());
        assertTrue(OrderEventType.STATUS_CHANGED.isStateTransition());
    }
    
    @Test
    void testOrderBusinessLogic() {
        Order order = new Order(1L, new BigDecimal("25.99"), "John Doe", "john@example.com");
        
        // Test cancellable states
        assertTrue(order.getStatus().isCancellable());
        
        order.transitionTo(OrderStatus.PAID);
        assertTrue(order.getStatus().isCancellable());
        
        order.transitionTo(OrderStatus.PREPARING);
        assertTrue(order.getStatus().isCancellable());
        
        order.transitionTo(OrderStatus.SHIPPED);
        assertFalse(order.getStatus().isCancellable());
        
        // Test deliverable states
        assertTrue(order.isDeliverable());
        
        order.transitionTo(OrderStatus.DELIVERED);
        assertFalse(order.isDeliverable());
        assertFalse(order.isActive());
    }
} 