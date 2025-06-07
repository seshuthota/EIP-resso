package com.eipresso.order.repository;

import com.eipresso.order.model.Order;
import com.eipresso.order.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Order Repository with advanced queries for order management
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    // Basic Finder Methods
    List<Order> findByUserId(Long userId);
    
    List<Order> findByStatus(OrderStatus status);
    
    List<Order> findByUserIdAndStatus(Long userId, OrderStatus status);
    
    Optional<Order> findByIdAndUserId(Long id, Long userId);
    
    // Time-based Queries
    List<Order> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);
    
    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime dateTime);
    
    // Active Orders (not cancelled or delivered)
    @Query("SELECT o FROM Order o WHERE o.status NOT IN ('CANCELLED', 'DELIVERED')")
    List<Order> findActiveOrders();
    
    @Query("SELECT o FROM Order o WHERE o.userId = :userId AND o.status NOT IN ('CANCELLED', 'DELIVERED')")
    List<Order> findActiveOrdersByUserId(@Param("userId") Long userId);
    
    // Orders requiring action
    @Query("SELECT o FROM Order o WHERE o.status = 'PENDING' AND o.createdAt < :cutoffTime")
    List<Order> findPendingOrdersOlderThan(@Param("cutoffTime") LocalDateTime cutoffTime);
    
    @Query("SELECT o FROM Order o WHERE o.status = 'PAID' ORDER BY o.createdAt ASC")
    List<Order> findOrdersReadyForFulfillment();
    
    // Analytics Queries
    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = :status")
    Long countByStatus(@Param("status") OrderStatus status);
    
    @Query("SELECT COUNT(o) FROM Order o WHERE o.userId = :userId")
    Long countByUserId(@Param("userId") Long userId);
    
    @Query("SELECT COUNT(o) FROM Order o WHERE o.createdAt >= :startDate")
    Long countOrdersSince(@Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.status = 'PAID' AND o.createdAt >= :startDate")
    Optional<java.math.BigDecimal> getTotalRevenueSince(@Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT AVG(o.totalAmount) FROM Order o WHERE o.status = 'PAID'")
    Optional<Double> getAverageOrderValue();
    
    // Complex Business Queries
    @Query("SELECT o FROM Order o WHERE o.customerEmail = :email ORDER BY o.createdAt DESC")
    List<Order> findByCustomerEmailOrderByCreatedAtDesc(@Param("email") String email);
    
    @Query("SELECT o FROM Order o WHERE o.deliveryAddress IS NOT NULL AND o.status IN ('PREPARING', 'SHIPPED')")
    List<Order> findOrdersRequiringDelivery();
    
    @Query("SELECT o FROM Order o WHERE o.estimatedDeliveryTime < :currentTime AND o.status = 'SHIPPED'")
    List<Order> findOverdueShippedOrders(@Param("currentTime") LocalDateTime currentTime);
    
    // Customer Relationship Queries
    @Query("SELECT DISTINCT o.customerEmail FROM Order o WHERE o.status = 'DELIVERED'")
    List<String> findEmailsOfCustomersWithDeliveredOrders();
    
    @Query("SELECT o FROM Order o WHERE o.userId = :userId ORDER BY o.createdAt DESC LIMIT 5")
    List<Order> findRecentOrdersByUserId(@Param("userId") Long userId);
    
    // Version-aware queries for optimistic locking
    @Query("SELECT o FROM Order o WHERE o.id = :id AND o.version = :version")
    Optional<Order> findByIdAndVersion(@Param("id") Long id, @Param("version") Long version);
} 