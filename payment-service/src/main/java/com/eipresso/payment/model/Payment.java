package com.eipresso.payment.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payment Entity - Financial Transaction Record
 * Implements comprehensive payment management with audit trail
 */
@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_payment_order_id", columnList = "orderId"),
    @Index(name = "idx_payment_user_id", columnList = "userId"),
    @Index(name = "idx_payment_status", columnList = "status"),
    @Index(name = "idx_payment_created_at", columnList = "createdAt"),
    @Index(name = "idx_payment_gateway", columnList = "paymentGateway")
})
@JsonIgnoreProperties(ignoreUnknown = true)
public class Payment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "payment_id", unique = true, nullable = false)
    @NotBlank(message = "Payment ID is required")
    private String paymentId;
    
    @Column(name = "order_id", nullable = false)
    @NotNull(message = "Order ID is required")
    private Long orderId;
    
    @Column(name = "user_id", nullable = false)
    @NotNull(message = "User ID is required")
    private Long userId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @NotNull(message = "Payment status is required")
    private PaymentStatus status;
    
    @Column(name = "amount", precision = 10, scale = 2, nullable = false)
    @NotNull(message = "Payment amount is required")
    @DecimalMin(value = "0.01", message = "Payment amount must be positive")
    private BigDecimal amount;
    
    @Column(name = "currency", length = 3, nullable = false)
    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    private String currency = "USD";
    
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_gateway", nullable = false)
    @NotNull(message = "Payment gateway is required")
    private PaymentGateway paymentGateway;
    
    @Column(name = "gateway_transaction_id")
    private String gatewayTransactionId;
    
    @Column(name = "customer_email", nullable = false)
    @NotBlank(message = "Customer email is required")
    @Email(message = "Invalid email format")
    private String customerEmail;
    
    @Column(name = "customer_name", nullable = false)
    @NotBlank(message = "Customer name is required")
    @Size(max = 100, message = "Customer name too long")
    private String customerName;
    
    @Column(name = "billing_address", columnDefinition = "TEXT")
    private String billingAddress;
    
    @Column(name = "payment_description")
    @Size(max = 500, message = "Payment description too long")
    private String description;
    
    @Column(name = "fraud_score")
    @DecimalMin(value = "0.0", message = "Fraud score must be non-negative")
    @DecimalMax(value = "100.0", message = "Fraud score must not exceed 100")
    private BigDecimal fraudScore = BigDecimal.ZERO;
    
    @Column(name = "fraud_reason")
    private String fraudReason;
    
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;
    
    @Column(name = "correlation_id")
    private String correlationId;
    
    @Column(name = "failure_reason")
    private String failureReason;
    
    @Column(name = "created_at", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "updated_at", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    @Column(name = "processed_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime processedAt;
    
    @Column(name = "expires_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expiresAt;
    
    // Constructors
    public Payment() {}
    
    public Payment(String paymentId, Long orderId, Long userId, BigDecimal amount, 
                   PaymentMethod paymentMethod, PaymentGateway paymentGateway,
                   String customerEmail, String customerName) {
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.userId = userId;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.paymentGateway = paymentGateway;
        this.customerEmail = customerEmail;
        this.customerName = customerName;
        this.status = PaymentStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.expiresAt = LocalDateTime.now().plusMinutes(30); // 30-minute expiry
    }
    
    // Business Logic Methods
    
    /**
     * Check if payment can be processed
     */
    public boolean canBeProcessed() {
        return status == PaymentStatus.PENDING && !isExpired() && !isHighFraudRisk();
    }
    
    /**
     * Check if payment has expired
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
    
    /**
     * Check if payment has high fraud risk
     */
    public boolean isHighFraudRisk() {
        return fraudScore != null && fraudScore.compareTo(new BigDecimal("75.0")) > 0;
    }
    
    /**
     * Check if payment is successful
     */
    public boolean isSuccessful() {
        return status == PaymentStatus.COMPLETED;
    }
    
    /**
     * Check if payment failed
     */
    public boolean isFailed() {
        return status == PaymentStatus.FAILED || status == PaymentStatus.REJECTED;
    }
    
    /**
     * Check if payment can be retried
     */
    public boolean canBeRetried() {
        return (status == PaymentStatus.FAILED || status == PaymentStatus.TIMEOUT) 
               && retryCount < 3 && !isExpired();
    }
    
    /**
     * Transition payment to new status
     */
    public void transitionTo(PaymentStatus newStatus) {
        if (!canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                String.format("Invalid transition from %s to %s for payment %s", 
                             this.status, newStatus, this.paymentId));
        }
        
        this.status = newStatus;
        this.updatedAt = LocalDateTime.now();
        
        if (newStatus == PaymentStatus.COMPLETED || newStatus == PaymentStatus.FAILED) {
            this.processedAt = LocalDateTime.now();
        }
    }
    
    /**
     * Check if transition to new status is valid
     */
    public boolean canTransitionTo(PaymentStatus newStatus) {
        switch (this.status) {
            case PENDING:
                return newStatus == PaymentStatus.PROCESSING || 
                       newStatus == PaymentStatus.FAILED ||
                       newStatus == PaymentStatus.REJECTED ||
                       newStatus == PaymentStatus.CANCELLED;
            case PROCESSING:
                return newStatus == PaymentStatus.COMPLETED || 
                       newStatus == PaymentStatus.FAILED ||
                       newStatus == PaymentStatus.TIMEOUT;
            case COMPLETED:
                return newStatus == PaymentStatus.REFUNDED;
            case FAILED:
            case TIMEOUT:
                return newStatus == PaymentStatus.PENDING; // For retry
            case REJECTED:
            case CANCELLED:
            case REFUNDED:
                return false; // Terminal states
            default:
                return false;
        }
    }
    
    /**
     * Increment retry count
     */
    public void incrementRetryCount() {
        this.retryCount++;
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * Update fraud score
     */
    public void updateFraudScore(BigDecimal score, String reason) {
        this.fraudScore = score;
        this.fraudReason = reason;
        this.updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
    
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }
    
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }
    
    public PaymentGateway getPaymentGateway() { return paymentGateway; }
    public void setPaymentGateway(PaymentGateway paymentGateway) { this.paymentGateway = paymentGateway; }
    
    public String getGatewayTransactionId() { return gatewayTransactionId; }
    public void setGatewayTransactionId(String gatewayTransactionId) { this.gatewayTransactionId = gatewayTransactionId; }
    
    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
    
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    
    public String getBillingAddress() { return billingAddress; }
    public void setBillingAddress(String billingAddress) { this.billingAddress = billingAddress; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public BigDecimal getFraudScore() { return fraudScore; }
    public void setFraudScore(BigDecimal fraudScore) { this.fraudScore = fraudScore; }
    
    public String getFraudReason() { return fraudReason; }
    public void setFraudReason(String fraudReason) { this.fraudReason = fraudReason; }
    
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }
    
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
} 