package com.eipresso.order.repository;

import com.eipresso.order.model.OrderEvent;
import com.eipresso.order.model.OrderEventType;
import com.eipresso.order.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Order Event Repository for Event Sourcing
 * Handles all event persistence and querying for order audit trail
 */
@Repository
public interface OrderEventRepository extends JpaRepository<OrderEvent, Long> {
    
    // Basic Event Queries
    List<OrderEvent> findByOrderIdOrderBySequenceNumberAsc(Long orderId);
    
    List<OrderEvent> findByOrderIdAndEventType(Long orderId, OrderEventType eventType);
    
    List<OrderEvent> findByEventType(OrderEventType eventType);
    
    List<OrderEvent> findByEventSource(String eventSource);
    
    List<OrderEvent> findByCorrelationId(String correlationId);
    
    // Sequence-based Queries for Event Sourcing
    @Query("SELECT e FROM OrderEvent e WHERE e.orderId = :orderId ORDER BY e.sequenceNumber ASC")
    List<OrderEvent> findOrderEventHistory(@Param("orderId") Long orderId);
    
    @Query("SELECT MAX(e.sequenceNumber) FROM OrderEvent e WHERE e.orderId = :orderId")
    Optional<Long> findMaxSequenceNumberForOrder(@Param("orderId") Long orderId);
    
    @Query("SELECT e FROM OrderEvent e WHERE e.orderId = :orderId AND e.sequenceNumber > :fromSequence ORDER BY e.sequenceNumber ASC")
    List<OrderEvent> findEventsSinceSequence(@Param("orderId") Long orderId, @Param("fromSequence") Long fromSequence);
    
    // State Transition Queries
    @Query("SELECT e FROM OrderEvent e WHERE e.eventType = 'STATUS_CHANGED' AND e.newStatus = :status")
    List<OrderEvent> findStatusTransitionEvents(@Param("status") OrderStatus status);
    
    @Query("SELECT e FROM OrderEvent e WHERE e.orderId = :orderId AND e.eventType = 'STATUS_CHANGED' ORDER BY e.sequenceNumber DESC LIMIT 1")
    Optional<OrderEvent> findLastStatusChangeEvent(@Param("orderId") Long orderId);
    
    // Time-based Event Queries
    List<OrderEvent> findByTimestampBetween(LocalDateTime startTime, LocalDateTime endTime);
    
    @Query("SELECT e FROM OrderEvent e WHERE e.eventType = :eventType AND e.timestamp >= :since")
    List<OrderEvent> findRecentEventsByType(@Param("eventType") OrderEventType eventType, @Param("since") LocalDateTime since);
    
    // Analytics and Reporting Queries
    @Query("SELECT COUNT(e) FROM OrderEvent e WHERE e.eventType = :eventType")
    Long countByEventType(@Param("eventType") OrderEventType eventType);
    
    @Query("SELECT COUNT(e) FROM OrderEvent e WHERE e.eventType = :eventType AND e.timestamp >= :since")
    Long countEventsSince(@Param("eventType") OrderEventType eventType, @Param("since") LocalDateTime since);
    
    @Query("SELECT e.eventSource, COUNT(e) FROM OrderEvent e GROUP BY e.eventSource")
    List<Object[]> getEventCountBySource();
    
    @Query("SELECT e.eventType, COUNT(e) FROM OrderEvent e WHERE e.timestamp >= :since GROUP BY e.eventType")
    List<Object[]> getEventTypeDistributionSince(@Param("since") LocalDateTime since);
    
    // Correlation and Saga Tracking
    @Query("SELECT DISTINCT e.correlationId FROM OrderEvent e WHERE e.correlationId IS NOT NULL")
    List<String> findAllCorrelationIds();
    
    @Query("SELECT e FROM OrderEvent e WHERE e.correlationId = :correlationId ORDER BY e.timestamp ASC")
    List<OrderEvent> findEventsByCorrelationId(@Param("correlationId") String correlationId);
    
    @Query("SELECT COUNT(DISTINCT e.correlationId) FROM OrderEvent e WHERE e.eventType = :eventType AND e.timestamp >= :since")
    Long countUniqueCorrelationsSince(@Param("eventType") OrderEventType eventType, @Param("since") LocalDateTime since);
    
    // Error and Recovery Queries
    @Query("SELECT e FROM OrderEvent e WHERE e.eventType IN ('PAYMENT_FAILED', 'CANCELLED') ORDER BY e.timestamp DESC")
    List<OrderEvent> findFailureEvents();
    
    @Query("SELECT e FROM OrderEvent e WHERE e.eventSource = :source AND e.timestamp >= :since ORDER BY e.timestamp DESC")
    List<OrderEvent> findRecentEventsBySource(@Param("source") String source, @Param("since") LocalDateTime since);
    
    // Event Replay Support
    @Query("SELECT e FROM OrderEvent e WHERE e.timestamp >= :fromTime ORDER BY e.orderId, e.sequenceNumber ASC")
    List<OrderEvent> findEventsForReplaySince(@Param("fromTime") LocalDateTime fromTime);
    
    @Query("SELECT e FROM OrderEvent e WHERE e.orderId IN :orderIds ORDER BY e.orderId, e.sequenceNumber ASC")
    List<OrderEvent> findEventsForOrders(@Param("orderIds") List<Long> orderIds);
    
    // Performance and Monitoring
    @Query("SELECT COUNT(e) FROM OrderEvent e WHERE e.timestamp >= :since")
    Long countEventsSince(@Param("since") LocalDateTime since);
    
    @Query("SELECT AVG(TIMESTAMPDIFF(MINUTE, e1.timestamp, e2.timestamp)) " +
           "FROM OrderEvent e1, OrderEvent e2 " +
           "WHERE e1.orderId = e2.orderId " +
           "AND e1.eventType = 'CREATED' " +
           "AND e2.eventType = 'DELIVERED'")
    Optional<Double> getAverageOrderFulfillmentTimeInMinutes();
} 