package com.eipresso.notification.model;

/**
 * Notification Channel Enum
 * 
 * Defines the various channels through which notifications can be sent
 */
public enum NotificationChannel {
    EMAIL("Email", "email", true),
    SMS("SMS", "sms", true),
    PUSH("Push Notification", "push", true),
    IN_APP("In-App Notification", "in_app", false),
    WEBHOOK("Webhook", "webhook", false),
    SLACK("Slack", "slack", false);

    private final String displayName;
    private final String channelCode;
    private final boolean requiresUserContact;

    NotificationChannel(String displayName, String channelCode, boolean requiresUserContact) {
        this.displayName = displayName;
        this.channelCode = channelCode;
        this.requiresUserContact = requiresUserContact;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getChannelCode() {
        return channelCode;
    }

    public boolean isRequiresUserContact() {
        return requiresUserContact;
    }

    /**
     * Check if this channel supports real-time delivery
     */
    public boolean isRealTime() {
        return this == PUSH || this == IN_APP || this == WEBHOOK || this == SLACK;
    }

    /**
     * Check if this channel supports batch processing
     */
    public boolean supportsBatchProcessing() {
        return this == EMAIL || this == SMS;
    }

    /**
     * Check if this channel is suitable for marketing notifications
     */
    public boolean isMarketingSuitable() {
        return this == EMAIL || this == PUSH || this == IN_APP;
    }

    /**
     * Check if this channel is suitable for urgent notifications
     */
    public boolean isUrgentSuitable() {
        return this == SMS || this == PUSH || this == IN_APP;
    }

    /**
     * Get the default priority for this channel
     */
    public NotificationPriority getDefaultPriority() {
        switch (this) {
            case SMS:
                return NotificationPriority.HIGH;
            case PUSH:
            case IN_APP:
                return NotificationPriority.MEDIUM;
            case EMAIL:
                return NotificationPriority.LOW;
            case WEBHOOK:
            case SLACK:
                return NotificationPriority.MEDIUM;
            default:
                return NotificationPriority.MEDIUM;
        }
    }
} 