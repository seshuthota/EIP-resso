package com.eipresso.order.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

/**
 * Order Entity - System of Record for Order States
 * Implements Event Sourcing pattern with immutable state transitions
 */
@Entity
@Table(name = "orders", indexes = {
    @Index(name = "idx_order_user_id", columnList = "userId"),
    @Index(name = "idx_order_status", columnList = "status"),
    @Index(name = "idx_order_created_at", columnList = "createdAt")
})
@JsonIgnoreProperties(ignoreUnknown = true)
public class Order {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false)
    @NotNull(message = "User ID is required")
    private Long userId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @NotNull(message = "Order status is required")
    private OrderStatus status;
    
    @Column(name = "total_amount", precision = 10, scale = 2, nullable = false)
    @NotNull(message = "Total amount is required")
    @DecimalMin(value = "0.01", message = "Total amount must be positive")
    private BigDecimal totalAmount;
    
    @Column(name = "currency", length = 3, nullable = false)
    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    private String currency = "USD";
    
    @Column(name = "customer_name", nullable = false)
    @NotBlank(message = "Customer name is required")
    @Size(max = 100, message = "Customer name too long")
    private String customerName;
    
    @Column(name = "customer_email", nullable = false)
    @NotBlank(message = "Customer email is required")
    @Email(message = "Invalid email format")
    private String customerEmail;
    
    @Column(name = "delivery_address", length = 500)
    @Size(max = 500, message = "Delivery address too long")
    private String deliveryAddress;
    
    @Column(name = "special_instructions", length = 1000)
    @Size(max = 1000, message = "Special instructions too long")
    private String specialInstructions;
    
    @Column(name = "estimated_delivery_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime estimatedDeliveryTime;
    
    @Column(name = "actual_delivery_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime actualDeliveryTime;
    
    @Column(name = "created_at", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
    
    @Version
    private Long version;
    
    // Constructors
    public Order() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.status = OrderStatus.PENDING;
    }
    
    public Order(Long userId, BigDecimal totalAmount, String customerName, String customerEmail) {
        this();
        this.userId = userId;
        this.totalAmount = totalAmount;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
    }
    
    // Business Logic Methods
    public boolean canTransitionTo(OrderStatus newStatus) {
        return status.canTransitionTo(newStatus);
    }
    
    public void transitionTo(OrderStatus newStatus) {
        if (!canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                String.format("Cannot transition from %s to %s", status, newStatus)
            );
        }
        this.status = newStatus;
        this.updatedAt = LocalDateTime.now();
    }
    
    public boolean isPaid() {
        return status == OrderStatus.PAID || 
               status == OrderStatus.PREPARING || 
               status == OrderStatus.SHIPPED || 
               status == OrderStatus.DELIVERED;
    }
    
    public boolean isActive() {
        return status != OrderStatus.CANCELLED && 
               status != OrderStatus.DELIVERED;
    }
    
    public boolean isDeliverable() {
        return status == OrderStatus.PREPARING || status == OrderStatus.SHIPPED;
    }
    
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    
    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
    
    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }
    
    public String getSpecialInstructions() { return specialInstructions; }
    public void setSpecialInstructions(String specialInstructions) { this.specialInstructions = specialInstructions; }
    
    public LocalDateTime getEstimatedDeliveryTime() { return estimatedDeliveryTime; }
    public void setEstimatedDeliveryTime(LocalDateTime estimatedDeliveryTime) { this.estimatedDeliveryTime = estimatedDeliveryTime; }
    
    public LocalDateTime getActualDeliveryTime() { return actualDeliveryTime; }
    public void setActualDeliveryTime(LocalDateTime actualDeliveryTime) { this.actualDeliveryTime = actualDeliveryTime; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    
    @Override
    public String toString() {
        return String.format("Order{id=%d, userId=%d, status=%s, totalAmount=%s, customerName='%s'}", 
                           id, userId, status, totalAmount, customerName);
    }
} 