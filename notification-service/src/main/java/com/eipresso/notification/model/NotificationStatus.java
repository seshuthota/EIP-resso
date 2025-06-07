package com.eipresso.notification.model;

/**
 * Notification Status Enum
 * 
 * Defines the lifecycle states of a notification
 */
public enum NotificationStatus {
    PENDING("Pending", "Notification is waiting to be processed"),
    PROCESSING("Processing", "Notification is currently being processed"),
    FILTERED("Filtered", "Notification was filtered out based on user preferences"),
    THROTTLED("Throttled", "Notification is delayed due to rate limiting"),
    SENT("Sent", "Notification was successfully sent"),
    DELIVERED("Delivered", "Notification was delivered to the recipient"),
    FAILED("Failed", "Notification delivery failed"),
    RETRYING("Retrying", "Notification is being retried after failure"),
    CANCELLED("Cancelled", "Notification was cancelled before delivery"),
    EXPIRED("Expired", "Notification expired before delivery");

    private final String displayName;
    private final String description;

    NotificationStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Check if this status indicates the notification is complete (terminal state)
     */
    public boolean isTerminal() {
        return this == SENT || this == DELIVERED || this == FAILED || 
               this == CANCELLED || this == EXPIRED || this == FILTERED;
    }

    /**
     * Check if this status indicates the notification is still active
     */
    public boolean isActive() {
        return this == PENDING || this == PROCESSING || this == THROTTLED || this == RETRYING;
    }

    /**
     * Check if this status indicates a successful outcome
     */
    public boolean isSuccessful() {
        return this == SENT || this == DELIVERED;
    }

    /**
     * Check if this status indicates a failure
     */
    public boolean isFailure() {
        return this == FAILED || this == EXPIRED;
    }

    /**
     * Check if this status can transition to another status
     */
    public boolean canTransitionTo(NotificationStatus targetStatus) {
        switch (this) {
            case PENDING:
                return targetStatus == PROCESSING || targetStatus == FILTERED || 
                       targetStatus == THROTTLED || targetStatus == CANCELLED;
            case PROCESSING:
                return targetStatus == SENT || targetStatus == FAILED || 
                       targetStatus == CANCELLED || targetStatus == THROTTLED;
            case THROTTLED:
                return targetStatus == PROCESSING || targetStatus == CANCELLED || 
                       targetStatus == EXPIRED;
            case FAILED:
                return targetStatus == RETRYING || targetStatus == CANCELLED;
            case RETRYING:
                return targetStatus == PROCESSING || targetStatus == FAILED || 
                       targetStatus == CANCELLED;
            case SENT:
                return targetStatus == DELIVERED || targetStatus == FAILED;
            default:
                return false; // Terminal states cannot transition
        }
    }

    /**
     * Get the next possible statuses from this status
     */
    public NotificationStatus[] getNextPossibleStatuses() {
        switch (this) {
            case PENDING:
                return new NotificationStatus[]{PROCESSING, FILTERED, THROTTLED, CANCELLED};
            case PROCESSING:
                return new NotificationStatus[]{SENT, FAILED, CANCELLED, THROTTLED};
            case THROTTLED:
                return new NotificationStatus[]{PROCESSING, CANCELLED, EXPIRED};
            case FAILED:
                return new NotificationStatus[]{RETRYING, CANCELLED};
            case RETRYING:
                return new NotificationStatus[]{PROCESSING, FAILED, CANCELLED};
            case SENT:
                return new NotificationStatus[]{DELIVERED, FAILED};
            default:
                return new NotificationStatus[0]; // Terminal states
        }
    }
} 