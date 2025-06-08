package com.eipresso.user.service;

import com.hazelcast.map.IMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class SecurityAuditService {
    
    private static final Logger logger = LoggerFactory.getLogger(SecurityAuditService.class);
    private static final Logger auditLogger = LoggerFactory.getLogger("SECURITY_AUDIT");
    
    @Autowired
    private IMap<String, String> auditCacheMap;
    
    private final AtomicLong auditEventCounter = new AtomicLong(0);
    
    public enum AuditEvent {
        LOGIN_SUCCESS,
        LOGIN_FAILED,
        LOGIN_LOCKED,
        TOKEN_GENERATED,
        TOKEN_REFRESHED,
        TOKEN_EXPIRED,
        TOKEN_BLACKLISTED,
        UNAUTHORIZED_ACCESS,
        RATE_LIMIT_EXCEEDED,
        SUSPICIOUS_ACTIVITY
    }
    
    /**
     * Log security audit event with full context
     */
    public void logSecurityEvent(AuditEvent event, String userId, String sessionId, 
                                String ipAddress, String userAgent, Map<String, Object> additionalData) {
        try {
            long eventId = auditEventCounter.incrementAndGet();
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            
            Map<String, Object> auditData = new HashMap<>();
            auditData.put("eventId", eventId);
            auditData.put("timestamp", timestamp);
            auditData.put("event", event.name());
            auditData.put("userId", userId);
            auditData.put("sessionId", sessionId);
            auditData.put("ipAddress", ipAddress);
            auditData.put("userAgent", userAgent);
            auditData.put("severity", getSeverityLevel(event));
            
            if (additionalData != null) {
                auditData.putAll(additionalData);
            }
            
            // Log to dedicated security audit logger
            auditLogger.info("SECURITY_EVENT: {}", auditData);
            
            // Store in distributed cache for real-time analysis
            String cacheKey = String.format("audit_%d_%s", eventId, timestamp);
            auditCacheMap.put(cacheKey, auditData.toString());
            
            // Check for suspicious patterns
            checkSuspiciousActivity(event, userId, ipAddress);
            
            logger.debug("Security audit event logged: {} for user: {}", event, userId);
            
        } catch (Exception e) {
            logger.error("Failed to log security audit event: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Log authentication success
     */
    public void logAuthenticationSuccess(String userId, String sessionId, String ipAddress, String userAgent) {
        Map<String, Object> data = new HashMap<>();
        data.put("authMethod", "JWT");
        data.put("loginTime", LocalDateTime.now());
        
        logSecurityEvent(AuditEvent.LOGIN_SUCCESS, userId, sessionId, ipAddress, userAgent, data);
    }
    
    /**
     * Log authentication failure
     */
    public void logAuthenticationFailure(String userIdentifier, String ipAddress, String userAgent, String reason) {
        Map<String, Object> data = new HashMap<>();
        data.put("failureReason", reason);
        data.put("userIdentifier", userIdentifier);
        
        logSecurityEvent(AuditEvent.LOGIN_FAILED, userIdentifier, null, ipAddress, userAgent, data);
    }
    
    /**
     * Log token generation
     */
    public void logTokenGeneration(String userId, String tokenType, String ipAddress) {
        Map<String, Object> data = new HashMap<>();
        data.put("tokenType", tokenType);
        data.put("generatedAt", LocalDateTime.now());
        
        logSecurityEvent(AuditEvent.TOKEN_GENERATED, userId, null, ipAddress, null, data);
    }
    
    /**
     * Log unauthorized access attempt
     */
    public void logUnauthorizedAccess(String requestPath, String ipAddress, String userAgent, String reason) {
        Map<String, Object> data = new HashMap<>();
        data.put("requestPath", requestPath);
        data.put("denialReason", reason);
        
        logSecurityEvent(AuditEvent.UNAUTHORIZED_ACCESS, "unknown", null, ipAddress, userAgent, data);
    }
    
    /**
     * Log token blacklisting
     */
    public void logTokenBlacklist(String userId, String reason) {
        Map<String, Object> data = new HashMap<>();
        data.put("blacklistReason", reason);
        data.put("blacklistedAt", LocalDateTime.now());
        
        logSecurityEvent(AuditEvent.TOKEN_BLACKLISTED, userId, null, null, null, data);
    }
    
    /**
     * Check for suspicious activity patterns
     */
    private void checkSuspiciousActivity(AuditEvent event, String userId, String ipAddress) {
        try {
            String patternKey = String.format("pattern_%s_%s", userId, ipAddress);
            String existingPattern = auditCacheMap.get(patternKey);
            
            if (existingPattern == null) {
                auditCacheMap.put(patternKey, "1", 300L, TimeUnit.SECONDS); // 5 minutes TTL
            } else {
                int count = Integer.parseInt(existingPattern) + 1;
                auditCacheMap.put(patternKey, String.valueOf(count), 300L, TimeUnit.SECONDS);
                
                // Alert on suspicious patterns
                if (event == AuditEvent.LOGIN_FAILED && count > 5) {
                    logSecurityEvent(AuditEvent.SUSPICIOUS_ACTIVITY, userId, null, ipAddress, null,
                        Map.of("suspiciousPattern", "Multiple failed logins", "count", count));
                }
                
                if (event == AuditEvent.UNAUTHORIZED_ACCESS && count > 10) {
                    logSecurityEvent(AuditEvent.SUSPICIOUS_ACTIVITY, userId, null, ipAddress, null,
                        Map.of("suspiciousPattern", "Multiple unauthorized attempts", "count", count));
                }
            }
            
        } catch (Exception e) {
            logger.error("Failed to check suspicious activity: {}", e.getMessage());
        }
    }
    
    /**
     * Get severity level for audit event
     */
    private String getSeverityLevel(AuditEvent event) {
        switch (event) {
            case LOGIN_SUCCESS:
            case TOKEN_GENERATED:
                return "INFO";
            case LOGIN_FAILED:
            case TOKEN_EXPIRED:
                return "WARN";
            case LOGIN_LOCKED:
            case UNAUTHORIZED_ACCESS:
            case TOKEN_BLACKLISTED:
                return "ERROR";
            case SUSPICIOUS_ACTIVITY:
            case RATE_LIMIT_EXCEEDED:
                return "CRITICAL";
            default:
                return "INFO";
        }
    }
    
    /**
     * Get audit statistics for monitoring
     */
    public Map<String, Object> getAuditStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalAuditEvents", auditEventCounter.get());
        stats.put("auditCacheSize", auditCacheMap.size());
        stats.put("timestamp", LocalDateTime.now());
        
        return stats;
    }
} 