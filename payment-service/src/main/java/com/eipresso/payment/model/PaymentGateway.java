package com.eipresso.payment.model;

/**
 * Payment Gateway Enum
 * Defines all supported payment gateway providers
 */
public enum PaymentGateway {
    STRIPE("Stripe", "stripe", true, 99.9),
    PAYPAL("PayPal", "paypal", true, 99.5),
    SQUARE("Square", "square", true, 99.8),
    ADYEN("Adyen", "adyen", true, 99.7),
    MOCK("Mock Gateway", "mock", false, 100.0);
    
    private final String displayName;
    private final String code;
    private final boolean isProduction;
    private final double uptime;
    
    PaymentGateway(String displayName, String code, boolean isProduction, double uptime) {
        this.displayName = displayName;
        this.code = code;
        this.isProduction = isProduction;
        this.uptime = uptime;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getCode() {
        return code;
    }
    
    public boolean isProduction() {
        return isProduction;
    }
    
    public double getUptime() {
        return uptime;
    }
    
    /**
     * Check if gateway supports the given payment method
     */
    public boolean supports(PaymentMethod paymentMethod) {
        switch (this) {
            case STRIPE:
                return paymentMethod == PaymentMethod.CREDIT_CARD || 
                       paymentMethod == PaymentMethod.DEBIT_CARD ||
                       paymentMethod == PaymentMethod.DIGITAL_WALLET;
            case PAYPAL:
                return paymentMethod == PaymentMethod.DIGITAL_WALLET ||
                       paymentMethod == PaymentMethod.CREDIT_CARD ||
                       paymentMethod == PaymentMethod.BANK_TRANSFER;
            case SQUARE:
                return paymentMethod == PaymentMethod.CREDIT_CARD || 
                       paymentMethod == PaymentMethod.DEBIT_CARD;
            case ADYEN:
                return true; // Supports all methods
            case MOCK:
                return true; // Mock supports all for testing
            default:
                return false;
        }
    }
    
    /**
     * Get gateway-specific timeout in seconds
     */
    public int getTimeoutSeconds() {
        switch (this) {
            case STRIPE: return 30;
            case PAYPAL: return 45;
            case SQUARE: return 25;
            case ADYEN: return 35;
            case MOCK: return 5;
            default: return 30;
        }
    }
    
    /**
     * Get gateway priority for selection
     */
    public int getPriority() {
        switch (this) {
            case STRIPE: return 1; // Highest priority
            case ADYEN: return 2;
            case SQUARE: return 3;
            case PAYPAL: return 4;
            case MOCK: return 99; // Lowest priority
            default: return 50;
        }
    }
    
    /**
     * Check if gateway supports webhooks
     */
    public boolean supportsWebhooks() {
        return this != MOCK;
    }
    
    /**
     * Check if gateway supports batch processing
     */
    public boolean supportsBatchProcessing() {
        return this == STRIPE || this == ADYEN || this == PAYPAL;
    }
    
    /**
     * Get base API URL for the gateway
     */
    public String getApiUrl() {
        switch (this) {
            case STRIPE: return "https://api.stripe.com/v1";
            case PAYPAL: return "https://api.paypal.com/v1";
            case SQUARE: return "https://connect.squareup.com/v2";
            case ADYEN: return "https://checkout-test.adyen.com/v69";
            case MOCK: return "http://localhost:9999/mock";
            default: return "";
        }
    }
} 