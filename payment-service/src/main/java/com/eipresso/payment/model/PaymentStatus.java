package com.eipresso.payment.model;

import java.util.Set;
import java.util.EnumSet;

/**
 * Payment Status Enum with State Transition Rules
 * Implements finite state machine for payment lifecycle management
 */
public enum PaymentStatus {
    PENDING("Payment created, awaiting processing"),
    PROCESSING("Payment is being processed"),
    COMPLETED("Payment successfully completed"),
    FAILED("Payment processing failed"),
    TIMEOUT("Payment processing timed out"),
    REJECTED("Payment rejected due to fraud or policy"),
    CANCELLED("Payment cancelled by user or system"),
    REFUNDED("Payment refunded to customer");
    
    private final String description;
    
    PaymentStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * Defines valid state transitions for payment lifecycle
     */
    public boolean canTransitionTo(PaymentStatus newStatus) {
        return getValidTransitions().contains(newStatus);
    }
    
    /**
     * Get all valid next states from current state
     */
    public Set<PaymentStatus> getValidTransitions() {
        switch (this) {
            case PENDING:
                return EnumSet.of(PROCESSING, FAILED, REJECTED, CANCELLED);
            case PROCESSING:
                return EnumSet.of(COMPLETED, FAILED, TIMEOUT);
            case COMPLETED:
                return EnumSet.of(REFUNDED);
            case FAILED:
            case TIMEOUT:
                return EnumSet.of(PENDING); // For retry
            case REJECTED:
            case CANCELLED:
            case REFUNDED:
                return EnumSet.noneOf(PaymentStatus.class); // Terminal states
            default:
                return EnumSet.noneOf(PaymentStatus.class);
        }
    }
    
    /**
     * Check if this is a terminal state (no further transitions allowed)
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == REJECTED || this == CANCELLED || this == REFUNDED;
    }
    
    /**
     * Check if payment is successful
     */
    public boolean isSuccessful() {
        return this == COMPLETED;
    }
    
    /**
     * Check if payment failed
     */
    public boolean isFailed() {
        return this == FAILED || this == TIMEOUT || this == REJECTED;
    }
    
    /**
     * Check if payment can be retried
     */
    public boolean isRetryable() {
        return this == FAILED || this == TIMEOUT;
    }
    
    /**
     * Check if payment is in progress
     */
    public boolean isInProgress() {
        return this == PENDING || this == PROCESSING;
    }
    
    /**
     * Check if payment requires immediate attention
     */
    public boolean requiresAttention() {
        return this == FAILED || this == TIMEOUT || this == REJECTED;
    }
    
    /**
     * Get user-friendly status name
     */
    public String getDisplayName() {
        switch (this) {
            case PENDING: return "Pending";
            case PROCESSING: return "Processing";
            case COMPLETED: return "Completed";
            case FAILED: return "Failed";
            case TIMEOUT: return "Timed Out";
            case REJECTED: return "Rejected";
            case CANCELLED: return "Cancelled";
            case REFUNDED: return "Refunded";
            default: return name();
        }
    }
    
    /**
     * Get priority level for processing
     */
    public int getPriority() {
        switch (this) {
            case TIMEOUT: return 1; // Highest priority
            case FAILED: return 2;
            case PENDING: return 3;
            case PROCESSING: return 4;
            default: return 5; // Lowest priority
        }
    }
} 