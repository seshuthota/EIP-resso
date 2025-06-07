package com.eipresso.order.model;

import java.util.Set;
import java.util.EnumSet;

/**
 * Order Status Enum with State Transition Rules
 * Implements finite state machine for order lifecycle management
 */
public enum OrderStatus {
    PENDING("Order created, awaiting payment"),
    PAID("Payment confirmed, ready for preparation"),
    PREPARING("Order is being prepared"),
    SHIPPED("Order has been shipped"),
    DELIVERED("Order delivered to customer"),
    CANCELLED("Order cancelled");
    
    private final String description;
    
    OrderStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * Defines valid state transitions for order lifecycle
     */
    public boolean canTransitionTo(OrderStatus newStatus) {
        return getValidTransitions().contains(newStatus);
    }
    
    /**
     * Get all valid next states from current state
     */
    public Set<OrderStatus> getValidTransitions() {
        switch (this) {
            case PENDING:
                return EnumSet.of(PAID, CANCELLED);
            case PAID:
                return EnumSet.of(PREPARING, CANCELLED);
            case PREPARING:
                return EnumSet.of(SHIPPED, CANCELLED);
            case SHIPPED:
                return EnumSet.of(DELIVERED);
            case DELIVERED:
                return EnumSet.noneOf(OrderStatus.class); // Terminal state
            case CANCELLED:
                return EnumSet.noneOf(OrderStatus.class); // Terminal state
            default:
                return EnumSet.noneOf(OrderStatus.class);
        }
    }
    
    /**
     * Check if this is a terminal state (no further transitions allowed)
     */
    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED;
    }
    
    /**
     * Check if order requires payment processing
     */
    public boolean requiresPayment() {
        return this == PENDING;
    }
    
    /**
     * Check if order is in progress (not terminal)
     */
    public boolean isInProgress() {
        return !isTerminal();
    }
    
    /**
     * Check if order can be cancelled
     */
    public boolean isCancellable() {
        return this == PENDING || this == PAID || this == PREPARING;
    }
    
    /**
     * Check if order is ready for fulfillment
     */
    public boolean isReadyForFulfillment() {
        return this == PAID;
    }
    
    /**
     * Get user-friendly status name
     */
    public String getDisplayName() {
        switch (this) {
            case PENDING: return "Pending Payment";
            case PAID: return "Payment Confirmed";
            case PREPARING: return "Being Prepared";
            case SHIPPED: return "Shipped";
            case DELIVERED: return "Delivered";
            case CANCELLED: return "Cancelled";
            default: return name();
        }
    }
} 