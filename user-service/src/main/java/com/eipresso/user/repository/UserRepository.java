package com.eipresso.user.repository;

import com.eipresso.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    // Basic lookup methods
    Optional<User> findByUsername(String username);
    
    Optional<User> findByEmail(String email);
    
    Optional<User> findByUsernameOrEmail(String username, String email);
    
    // Check existence methods for Idempotent Consumer pattern
    boolean existsByUsername(String username);
    
    boolean existsByEmail(String email);
    
    boolean existsByUsernameOrEmail(String username, String email);
    
    // Account management queries
    @Query("SELECT u FROM User u WHERE u.accountLocked = true AND u.lockedUntil < :currentTime")
    List<User> findUsersToUnlock(@Param("currentTime") LocalDateTime currentTime);
    
    @Modifying
    @Query("UPDATE User u SET u.accountLocked = false, u.lockedUntil = null, u.failedLoginAttempts = 0 WHERE u.id = :userId")
    void unlockUser(@Param("userId") Long userId);
    
    @Modifying
    @Query("UPDATE User u SET u.lastLogin = :loginTime WHERE u.id = :userId")
    void updateLastLogin(@Param("userId") Long userId, @Param("loginTime") LocalDateTime loginTime);
    
    @Modifying
    @Query("UPDATE User u SET u.failedLoginAttempts = u.failedLoginAttempts + 1 WHERE u.id = :userId")
    void incrementFailedLoginAttempts(@Param("userId") Long userId);
    
    // Search and filtering
    @Query("SELECT u FROM User u WHERE u.accountEnabled = true AND u.accountLocked = false")
    List<User> findActiveUsers();
    
    @Query("SELECT u FROM User u WHERE u.role = :role")
    List<User> findUsersByRole(@Param("role") User.Role role);
    
    @Query("SELECT u FROM User u WHERE u.emailVerified = false")
    List<User> findUnverifiedUsers();
    
    @Query("SELECT u FROM User u WHERE u.createdAt BETWEEN :startDate AND :endDate")
    List<User> findUsersCreatedBetween(@Param("startDate") LocalDateTime startDate, 
                                      @Param("endDate") LocalDateTime endDate);
    
    // Statistics queries
    @Query("SELECT COUNT(u) FROM User u WHERE u.accountEnabled = true")
    long countActiveUsers();
    
    @Query("SELECT COUNT(u) FROM User u WHERE u.accountLocked = true")
    long countLockedUsers();
    
    @Query("SELECT COUNT(u) FROM User u WHERE u.emailVerified = false")
    long countUnverifiedUsers();
    
    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt >= :since")
    long countNewUsersAfter(@Param("since") LocalDateTime since);
} 