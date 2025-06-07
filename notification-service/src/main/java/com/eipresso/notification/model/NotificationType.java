package com.eipresso.notification.model;

/**
 * Notification Type Enum
 * 
 * Comprehensive list of notification types for EIP-resso coffee shop
 */
public enum NotificationType {
    // Order-related notifications
    ORDER_CONFIRMATION("Order confirmation", "Your order has been confirmed"),
    ORDER_PAID("Payment received", "Your payment has been processed"),
    ORDER_PREPARING("Order in preparation", "Your order is being prepared"),
    ORDER_READY("Order ready", "Your order is ready for pickup"),
    ORDER_SHIPPED("Order shipped", "Your order has been shipped"),
    ORDER_DELIVERED("Order delivered", "Your order has been delivered"),
    ORDER_CANCELLED("Order cancelled", "Your order has been cancelled"),
    
    // Payment-related notifications
    PAYMENT_CONFIRMATION("Payment confirmation", "Your payment was successful"),
    PAYMENT_FAILED("Payment failed", "Your payment could not be processed"),
    REFUND_PROCESSED("Refund processed", "Your refund has been processed"),
    
    // Account-related notifications
    WELCOME("Welcome", "Welcome to EIP-resso"),
    ACCOUNT_CREATED("Account created", "Your account has been created"),
    PASSWORD_RESET("Password reset", "Your password has been reset"),
    PROFILE_UPDATED("Profile updated", "Your profile has been updated"),
    
    // Marketing and promotional notifications
    PROMOTIONAL("Promotional offer", "Special offer just for you"),
    LOYALTY_REWARD("Loyalty reward", "You've earned a reward"),
    NEW_PRODUCT("New product", "Check out our new products"),
    SEASONAL_OFFER("Seasonal offer", "Limited time seasonal offer"),
    
    // Inventory and product notifications
    LOW_STOCK_ALERT("Low stock alert", "Your favorite product is running low"),
    BACK_IN_STOCK("Back in stock", "Your favorite product is back"),
    PRICE_CHANGE("Price change", "Price update for your favorite products"),
    
    // Service notifications
    MAINTENANCE_NOTICE("Maintenance notice", "Scheduled maintenance notification"),
    SERVICE_UPDATE("Service update", "Important service update"),
    SYSTEM_ALERT("System alert", "System notification"),
    
    // Customer service notifications
    FEEDBACK_REQUEST("Feedback request", "We'd love your feedback"),
    SURVEY_INVITATION("Survey invitation", "Help us improve our service"),
    SUPPORT_RESPONSE("Support response", "Response to your support request"),
    
    // Administrative notifications
    ADMIN_ALERT("Admin alert", "Administrative notification"),
    COMPLIANCE_NOTICE("Compliance notice", "Important compliance information"),
    SECURITY_ALERT("Security alert", "Important security notification");

    private final String displayName;
    private final String defaultSubject;

    NotificationType(String displayName, String defaultSubject) {
        this.displayName = displayName;
        this.defaultSubject = defaultSubject;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDefaultSubject() {
        return defaultSubject;
    }

    /**
     * Check if this notification type is order-related
     */
    public boolean isOrderRelated() {
        return this.name().startsWith("ORDER_");
    }

    /**
     * Check if this notification type is payment-related
     */
    public boolean isPaymentRelated() {
        return this.name().startsWith("PAYMENT_") || this.name().startsWith("REFUND_");
    }

    /**
     * Check if this notification type is marketing-related
     */
    public boolean isMarketingRelated() {
        return this == PROMOTIONAL || this == LOYALTY_REWARD || 
               this == NEW_PRODUCT || this == SEASONAL_OFFER;
    }

    /**
     * Check if this notification type is high priority
     */
    public boolean isHighPriority() {
        return this == PAYMENT_FAILED || this == ORDER_CANCELLED || 
               this == SECURITY_ALERT || this == SYSTEM_ALERT || 
               this == ADMIN_ALERT;
    }

    /**
     * Check if this notification type requires immediate delivery
     */
    public boolean requiresImmediateDelivery() {
        return this == SECURITY_ALERT || this == PAYMENT_FAILED || 
               this == ORDER_CANCELLED || this == SYSTEM_ALERT;
    }
} 