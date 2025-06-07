package com.eipresso.order.model;

/**
 * Order Event Types for Event Sourcing
 * Defines all possible events that can occur in order lifecycle
 */
public enum OrderEventType {
    CREATED("Order was created"),
    STATUS_CHANGED("Order status changed"),
    PAYMENT_REQUESTED("Payment processing requested"),
    PAYMENT_PROCESSED("Payment successfully processed"),
    PAYMENT_FAILED("Payment processing failed"),
    INVENTORY_RESERVED("Inventory items reserved"),
    INVENTORY_RELEASED("Inventory items released"),
    PREPARATION_STARTED("Order preparation started"),
    PREPARATION_COMPLETED("Order preparation completed"),
    SHIPPED("Order shipped to customer"),
    DELIVERED("Order delivered to customer"),
    CANCELLED("Order cancelled"),
    REFUNDED("Order refunded"),
    MODIFIED("Order details modified"),
    NOTIFICATION_SENT("Notification sent to customer");
    
    private final String description;
    
    OrderEventType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * Check if this event type represents a state transition
     */
    public boolean isStateTransition() {
        return this == STATUS_CHANGED || 
               this == PAYMENT_PROCESSED || 
               this == CANCELLED || 
               this == DELIVERED;
    }
    
    /**
     * Check if this event type is related to payment processing
     */
    public boolean isPaymentRelated() {
        return this == PAYMENT_REQUESTED || 
               this == PAYMENT_PROCESSED || 
               this == PAYMENT_FAILED ||
               this == REFUNDED;
    }
    
    /**
     * Check if this event type is related to fulfillment
     */
    public boolean isFulfillmentRelated() {
        return this == INVENTORY_RESERVED || 
               this == INVENTORY_RELEASED ||
               this == PREPARATION_STARTED || 
               this == PREPARATION_COMPLETED ||
               this == SHIPPED || 
               this == DELIVERED;
    }
    
    /**
     * Check if this event type requires external notification
     */
    public boolean requiresNotification() {
        return this == CREATED || 
               this == PAYMENT_PROCESSED || 
               this == PAYMENT_FAILED ||
               this == SHIPPED || 
               this == DELIVERED || 
               this == CANCELLED;
    }
} 