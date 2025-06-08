package com.eipresso.orchestration.model;

/**
 * Workflow types supported by the orchestration service
 */
public enum WorkflowType {
    ORDER_PROCESSING("Complete order processing from payment to delivery"),
    REFUND_PROCESSING("Process customer refunds with inventory updates"),
    CATERING_ORDER("Large catering orders with multiple suppliers"),
    SUBSCRIPTION_MANAGEMENT("Recurring subscription processing"),
    INVENTORY_REPLENISHMENT("Automated inventory restocking workflow"),
    CUSTOMER_ONBOARDING("New customer registration and setup workflow");
    
    private final String description;
    
    WorkflowType(String description) {
        this.description = description;
    }
    
    public String getDescription() { return description; }
} 