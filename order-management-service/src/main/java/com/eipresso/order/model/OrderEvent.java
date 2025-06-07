package com.eipresso.order.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;

/**
 * Order Event Entity - Event Sourcing Implementation
 * Captures all order state changes as immutable events for audit and replay
 */
@Entity
@Table(name = "order_events", indexes = {
    @Index(name = "idx_order_event_order_id", columnList = "orderId"),
    @Index(name = "idx_order_event_type", columnList = "eventType"),
    @Index(name = "idx_order_event_timestamp", columnList = "timestamp"),
    @Index(name = "idx_order_event_source", columnList = "eventSource")
})
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "order_id", nullable = false)
    @NotNull(message = "Order ID is required")
    private Long orderId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    @NotNull(message = "Event type is required")
    private OrderEventType eventType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status")
    private OrderStatus previousStatus;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "new_status")
    private OrderStatus newStatus;
    
    @Column(name = "event_source", nullable = false)
    @NotBlank(message = "Event source is required")
    private String eventSource;
    
    @Column(name = "user_id")
    private Long userId;
    
    @Column(name = "correlation_id")
    private String correlationId;
    
    @Column(name = "event_data", columnDefinition = "TEXT")
    private String eventData; // JSON string for flexible event data
    
    @Column(name = "timestamp", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;
    
    @Column(name = "sequence_number", nullable = false)
    private Long sequenceNumber;
    
    @Version
    private Long version;
    
    // Constructors
    public OrderEvent() {
        this.timestamp = LocalDateTime.now();
    }
    
    public OrderEvent(Long orderId, OrderEventType eventType, String eventSource) {
        this();
        this.orderId = orderId;
        this.eventType = eventType;
        this.eventSource = eventSource;
    }
    
    public OrderEvent(Long orderId, OrderEventType eventType, String eventSource, 
                     OrderStatus previousStatus, OrderStatus newStatus) {
        this(orderId, eventType, eventSource);
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
    }
    
    // Factory Methods for Common Events
    public static OrderEvent orderCreated(Long orderId, Long userId, String correlationId) {
        OrderEvent event = new OrderEvent(orderId, OrderEventType.CREATED, "order-service");
        event.setUserId(userId);
        event.setNewStatus(OrderStatus.PENDING);
        event.setCorrelationId(correlationId);
        return event;
    }
    
    public static OrderEvent statusChanged(Long orderId, OrderStatus from, OrderStatus to, 
                                         String source, String correlationId) {
        OrderEvent event = new OrderEvent(orderId, OrderEventType.STATUS_CHANGED, source, from, to);
        event.setCorrelationId(correlationId);
        return event;
    }
    
    public static OrderEvent paymentProcessed(Long orderId, String paymentId, String correlationId) {
        OrderEvent event = new OrderEvent(orderId, OrderEventType.PAYMENT_PROCESSED, "payment-service");
        event.setPreviousStatus(OrderStatus.PENDING);
        event.setNewStatus(OrderStatus.PAID);
        event.setCorrelationId(correlationId);
        event.setEventData(String.format("{\"paymentId\":\"%s\"}", paymentId));
        return event;
    }
    
    public static OrderEvent cancelled(Long orderId, OrderStatus previousStatus, String reason, 
                                     String source, String correlationId) {
        OrderEvent event = new OrderEvent(orderId, OrderEventType.CANCELLED, source, previousStatus, OrderStatus.CANCELLED);
        event.setCorrelationId(correlationId);
        event.setEventData(String.format("{\"reason\":\"%s\"}", reason));
        return event;
    }
    
    public static OrderEvent delivered(Long orderId, String deliveryConfirmation, String correlationId) {
        OrderEvent event = new OrderEvent(orderId, OrderEventType.DELIVERED, "fulfillment-service");
        event.setPreviousStatus(OrderStatus.SHIPPED);
        event.setNewStatus(OrderStatus.DELIVERED);
        event.setCorrelationId(correlationId);
        event.setEventData(String.format("{\"deliveryConfirmation\":\"%s\"}", deliveryConfirmation));
        return event;
    }
    
    // Business Logic Methods
    public boolean isStateTransition() {
        return eventType == OrderEventType.STATUS_CHANGED && 
               previousStatus != null && newStatus != null;
    }
    
    public boolean isTerminalEvent() {
        return newStatus != null && newStatus.isTerminal();
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    
    public OrderEventType getEventType() { return eventType; }
    public void setEventType(OrderEventType eventType) { this.eventType = eventType; }
    
    public OrderStatus getPreviousStatus() { return previousStatus; }
    public void setPreviousStatus(OrderStatus previousStatus) { this.previousStatus = previousStatus; }
    
    public OrderStatus getNewStatus() { return newStatus; }
    public void setNewStatus(OrderStatus newStatus) { this.newStatus = newStatus; }
    
    public String getEventSource() { return eventSource; }
    public void setEventSource(String eventSource) { this.eventSource = eventSource; }
    
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    
    public String getEventData() { return eventData; }
    public void setEventData(String eventData) { this.eventData = eventData; }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    
    public Long getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(Long sequenceNumber) { this.sequenceNumber = sequenceNumber; }
    
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    
    @Override
    public String toString() {
        return String.format("OrderEvent{id=%d, orderId=%d, eventType=%s, %s->%s, source='%s', timestamp=%s}", 
                           id, orderId, eventType, previousStatus, newStatus, eventSource, timestamp);
    }
} 