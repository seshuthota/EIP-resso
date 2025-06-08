package com.eipresso.payment.model;

/**
 * Payment Method Enum
 * Defines all supported payment methods in the system
 */
public enum PaymentMethod {
    CREDIT_CARD("Credit Card", "credit-card"),
    DEBIT_CARD("Debit Card", "debit-card"),
    DIGITAL_WALLET("Digital Wallet", "digital-wallet"),
    BANK_TRANSFER("Bank Transfer", "bank-transfer"),
    CASH_ON_DELIVERY("Cash on Delivery", "cash"),
    GIFT_CARD("Gift Card", "gift-card"),
    STORE_CREDIT("Store Credit", "store-credit"),
    CRYPTOCURRENCY("Cryptocurrency", "crypto");
    
    private final String displayName;
    private final String code;
    
    PaymentMethod(String displayName, String code) {
        this.displayName = displayName;
        this.code = code;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getCode() {
        return code;
    }
    
    /**
     * Check if this payment method requires online processing
     */
    public boolean requiresOnlineProcessing() {
        return this != CASH_ON_DELIVERY;
    }
    
    /**
     * Check if this payment method supports instant settlement
     */
    public boolean supportsInstantSettlement() {
        return this == CREDIT_CARD || this == DEBIT_CARD || this == DIGITAL_WALLET;
    }
    
    /**
     * Check if this payment method requires fraud screening
     */
    public boolean requiresFraudScreening() {
        return this == CREDIT_CARD || this == DEBIT_CARD || this == DIGITAL_WALLET;
    }
    
    /**
     * Get processing fee percentage
     */
    public double getProcessingFeePercentage() {
        switch (this) {
            case CREDIT_CARD: return 2.9;
            case DEBIT_CARD: return 1.5;
            case DIGITAL_WALLET: return 2.5;
            case BANK_TRANSFER: return 0.5;
            case CRYPTOCURRENCY: return 1.0;
            default: return 0.0;
        }
    }
    
    /**
     * Get expected processing time in minutes
     */
    public int getExpectedProcessingTimeMinutes() {
        switch (this) {
            case CREDIT_CARD:
            case DEBIT_CARD:
            case DIGITAL_WALLET:
                return 2; // 2 minutes
            case BANK_TRANSFER:
                return 1440; // 24 hours
            case CRYPTOCURRENCY:
                return 60; // 1 hour
            case GIFT_CARD:
            case STORE_CREDIT:
                return 1; // 1 minute
            default:
                return 0;
        }
    }
} 