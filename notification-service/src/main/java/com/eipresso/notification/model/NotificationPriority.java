package com.eipresso.notification.model;

/**
 * Notification Priority Enum
 * 
 * Defines priority levels for notification processing and delivery
 */
public enum NotificationPriority {
    URGENT(1, "Urgent", "Immediate delivery required", 0),
    HIGH(2, "High", "High priority delivery", 1000),
    MEDIUM(3, "Medium", "Normal priority delivery", 5000),
    LOW(4, "Low", "Low priority delivery", 15000),
    BULK(5, "Bulk", "Batch processing suitable", 60000);

    private final int level;
    private final String displayName;
    private final String description;
    private final long throttleDelayMs;

    NotificationPriority(int level, String displayName, String description, long throttleDelayMs) {
        this.level = level;
        this.displayName = displayName;
        this.description = description;
        this.throttleDelayMs = throttleDelayMs;
    }

    public int getLevel() {
        return level;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public long getThrottleDelayMs() {
        return throttleDelayMs;
    }

    /**
     * Check if this priority is higher than another priority
     */
    public boolean isHigherThan(NotificationPriority other) {
        return this.level < other.level;
    }

    /**
     * Check if this priority requires immediate processing
     */
    public boolean requiresImmediateProcessing() {
        return this == URGENT || this == HIGH;
    }

    /**
     * Check if this priority can be batched
     */
    public boolean canBeBatched() {
        return this == LOW || this == BULK;
    }

    /**
     * Get the maximum retry count for this priority
     */
    public int getMaxRetryCount() {
        switch (this) {
            case URGENT:
                return 5;
            case HIGH:
                return 3;
            case MEDIUM:
                return 2;
            case LOW:
                return 1;
            case BULK:
                return 1;
            default:
                return 2;
        }
    }

    /**
     * Get the retry delay in milliseconds for this priority
     */
    public long getRetryDelayMs() {
        switch (this) {
            case URGENT:
                return 1000; // 1 second
            case HIGH:
                return 5000; // 5 seconds
            case MEDIUM:
                return 30000; // 30 seconds
            case LOW:
                return 300000; // 5 minutes
            case BULK:
                return 600000; // 10 minutes
            default:
                return 30000;
        }
    }

    /**
     * Get the processing timeout in milliseconds for this priority
     */
    public long getProcessingTimeoutMs() {
        switch (this) {
            case URGENT:
                return 10000; // 10 seconds
            case HIGH:
                return 30000; // 30 seconds
            case MEDIUM:
                return 60000; // 1 minute
            case LOW:
                return 300000; // 5 minutes
            case BULK:
                return 600000; // 10 minutes
            default:
                return 60000;
        }
    }
} 