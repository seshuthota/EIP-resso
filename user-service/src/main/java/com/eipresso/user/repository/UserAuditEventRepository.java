package com.eipresso.user.repository;

import com.eipresso.user.entity.UserAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserAuditEventRepository extends JpaRepository<UserAuditEvent, Long> {
    
    // Find events by user
    List<UserAuditEvent> findByUserIdOrderByTimestampDesc(Long userId);
    
    List<UserAuditEvent> findByUsernameOrderByTimestampDesc(String username);
    
    List<UserAuditEvent> findByEmailOrderByTimestampDesc(String email);
    
    // Find events by type
    List<UserAuditEvent> findByEventTypeOrderByTimestampDesc(UserAuditEvent.EventType eventType);
    
    List<UserAuditEvent> findBySeverityOrderByTimestampDesc(UserAuditEvent.Severity severity);
    
    // Security monitoring queries
    @Query("SELECT ae FROM UserAuditEvent ae WHERE ae.eventType = :eventType AND ae.timestamp >= :since")
    List<UserAuditEvent> findFailedLoginsSince(@Param("eventType") UserAuditEvent.EventType eventType, 
                                               @Param("since") LocalDateTime since);
    
    @Query("SELECT ae FROM UserAuditEvent ae WHERE ae.userId = :userId AND ae.eventType = 'USER_LOGIN_FAILED' AND ae.timestamp >= :since")
    List<UserAuditEvent> findFailedLoginsByUserSince(@Param("userId") Long userId, 
                                                     @Param("since") LocalDateTime since);
    
    @Query("SELECT ae FROM UserAuditEvent ae WHERE ae.ipAddress = :ipAddress AND ae.eventType = 'USER_LOGIN_FAILED' AND ae.timestamp >= :since")
    List<UserAuditEvent> findFailedLoginsByIpSince(@Param("ipAddress") String ipAddress, 
                                                   @Param("since") LocalDateTime since);
    
    // Time-based queries
    List<UserAuditEvent> findByTimestampBetweenOrderByTimestampDesc(LocalDateTime start, LocalDateTime end);
    
    @Query("SELECT ae FROM UserAuditEvent ae WHERE ae.timestamp >= :since ORDER BY ae.timestamp DESC")
    List<UserAuditEvent> findRecentEvents(@Param("since") LocalDateTime since);
    
    // Statistics
    @Query("SELECT COUNT(ae) FROM UserAuditEvent ae WHERE ae.eventType = :eventType AND ae.timestamp >= :since")
    long countEventsSince(@Param("eventType") UserAuditEvent.EventType eventType, 
                         @Param("since") LocalDateTime since);
    
    @Query("SELECT COUNT(ae) FROM UserAuditEvent ae WHERE ae.severity = :severity AND ae.timestamp >= :since")
    long countBySeveritySince(@Param("severity") UserAuditEvent.Severity severity, 
                             @Param("since") LocalDateTime since);
    
    // Cleanup query for old audit events
    @Query("DELETE FROM UserAuditEvent ae WHERE ae.timestamp < :cutoffDate")
    void deleteOldEvents(@Param("cutoffDate") LocalDateTime cutoffDate);
} 