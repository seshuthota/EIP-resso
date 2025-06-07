package com.eipresso.user.service;

import com.eipresso.user.entity.UserAuditEvent;
import com.eipresso.user.repository.UserAuditEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class UserAuditService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserAuditService.class);
    
    @Autowired
    private UserAuditEventRepository auditEventRepository;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * Save audit event to database - called from Camel Wire Tap routes
     */
    @Transactional
    public void saveAuditEvent(UserAuditEvent auditEvent) {
        try {
            if (auditEvent.getTimestamp() == null) {
                auditEvent.setTimestamp(LocalDateTime.now());
            }
            
            UserAuditEvent saved = auditEventRepository.save(auditEvent);
            logger.debug("Audit event saved with ID: {}, Type: {}, Severity: {}", 
                        saved.getId(), saved.getEventType(), saved.getSeverity());
            
        } catch (Exception e) {
            logger.error("Failed to save audit event: {}", e.getMessage(), e);
            // Don't throw exception to avoid breaking the main flow
        }
    }
    
    /**
     * Enrich audit event with request information from Camel Exchange
     * Wire Tap Pattern implementation
     */
    public void enrichAuditEventFromRequest(UserAuditEvent auditEvent, Exchange exchange) {
        try {
            // Extract HTTP headers if available
            String ipAddress = extractIPAddress(exchange);
            String userAgent = exchange.getIn().getHeader("User-Agent", String.class);
            String sessionId = exchange.getIn().getHeader("X-Session-ID", String.class);
            
            // Set enriched data
            auditEvent.setIpAddress(ipAddress);
            auditEvent.setUserAgent(userAgent);
            auditEvent.setSessionId(sessionId);
            
            // Extract user information from request body
            Object requestBody = exchange.getIn().getBody();
            enrichUserInfoFromRequestBody(auditEvent, requestBody);
            
            // Add additional context data
            String additionalData = createAdditionalDataJson(exchange);
            auditEvent.setAdditionalData(additionalData);
            
        } catch (Exception e) {
            logger.warn("Failed to enrich audit event: {}", e.getMessage());
            // Continue with partial enrichment
        }
    }
    
    /**
     * Extract IP address from exchange headers
     */
    private String extractIPAddress(Exchange exchange) {
        // Check various headers for real IP
        String ipAddress = exchange.getIn().getHeader("X-Forwarded-For", String.class);
        if (ipAddress != null && !ipAddress.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            return ipAddress.split(",")[0].trim();
        }
        
        ipAddress = exchange.getIn().getHeader("X-Real-IP", String.class);
        if (ipAddress != null && !ipAddress.isEmpty()) {
            return ipAddress;
        }
        
        ipAddress = exchange.getIn().getHeader("Remote-Addr", String.class);
        if (ipAddress != null && !ipAddress.isEmpty()) {
            return ipAddress;
        }
        
        return "127.0.0.1"; // Default for local development
    }
    
    /**
     * Extract user information from request body
     */
    private void enrichUserInfoFromRequestBody(UserAuditEvent auditEvent, Object requestBody) {
        try {
            if (requestBody instanceof String) {
                // Try to parse JSON request body
                String jsonBody = (String) requestBody;
                if (jsonBody.contains("email")) {
                    // Extract email using simple parsing (could use Jackson for complex cases)
                    String email = extractFieldFromJson(jsonBody, "email");
                    auditEvent.setEmail(email);
                }
                if (jsonBody.contains("username")) {
                    String username = extractFieldFromJson(jsonBody, "username");
                    auditEvent.setUsername(username);
                }
            }
        } catch (Exception e) {
            logger.debug("Could not extract user info from request body: {}", e.getMessage());
        }
    }
    
    /**
     * Simple JSON field extraction (for basic cases)
     */
    private String extractFieldFromJson(String json, String fieldName) {
        try {
            String pattern = "\"" + fieldName + "\"\\s*:\\s*\"([^\"]+)\"";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                return m.group(1);
            }
        } catch (Exception e) {
            logger.debug("Failed to extract field {} from JSON: {}", fieldName, e.getMessage());
        }
        return null;
    }
    
    /**
     * Create additional data JSON from exchange context
     */
    private String createAdditionalDataJson(Exchange exchange) {
        try {
            StringBuilder additionalData = new StringBuilder("{");
            
            // Add exchange properties
            String operation = exchange.getIn().getHeader("operation", String.class);
            if (operation != null) {
                additionalData.append("\"operation\":\"").append(operation).append("\",");
            }
            
            // Add enrichment data if available
            String enrichedIP = exchange.getProperty("enrichedIP", String.class);
            String enrichedTimezone = exchange.getProperty("enrichedTimezone", String.class);
            String enrichedCountry = exchange.getProperty("enrichedCountry", String.class);
            
            if (enrichedIP != null) {
                additionalData.append("\"enrichedIP\":\"").append(enrichedIP).append("\",");
            }
            if (enrichedTimezone != null) {
                additionalData.append("\"enrichedTimezone\":\"").append(enrichedTimezone).append("\",");
            }
            if (enrichedCountry != null) {
                additionalData.append("\"enrichedCountry\":\"").append(enrichedCountry).append("\",");
            }
            
            // Add timestamp
            additionalData.append("\"processedAt\":\"").append(LocalDateTime.now()).append("\"");
            additionalData.append("}");
            
            return additionalData.toString();
            
        } catch (Exception e) {
            logger.warn("Failed to create additional data JSON: {}", e.getMessage());
            return "{}";
        }
    }
    
    /**
     * Create audit event for authentication attempt
     */
    public UserAuditEvent createAuthenticationAuditEvent(String username, String email, 
                                                        UserAuditEvent.EventType eventType, 
                                                        String description,
                                                        UserAuditEvent.Severity severity) {
        UserAuditEvent auditEvent = new UserAuditEvent();
        auditEvent.setUsername(username);
        auditEvent.setEmail(email);
        auditEvent.setEventType(eventType);
        auditEvent.setEventDescription(description);
        auditEvent.setSeverity(severity);
        auditEvent.setTimestamp(LocalDateTime.now());
        
        return auditEvent;
    }
    
    /**
     * Create audit event for user registration
     */
    public UserAuditEvent createRegistrationAuditEvent(String username, String email, 
                                                      boolean successful, String details) {
        UserAuditEvent auditEvent = new UserAuditEvent();
        auditEvent.setUsername(username);
        auditEvent.setEmail(email);
        auditEvent.setEventType(UserAuditEvent.EventType.USER_REGISTRATION);
        auditEvent.setEventDescription(successful ? 
            "User registration successful" : "User registration failed: " + details);
        auditEvent.setSeverity(successful ? 
            UserAuditEvent.Severity.INFO : UserAuditEvent.Severity.WARNING);
        auditEvent.setTimestamp(LocalDateTime.now());
        
        return auditEvent;
    }
    
    /**
     * Get recent security events for monitoring
     */
    public java.util.List<UserAuditEvent> getRecentSecurityEvents(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return auditEventRepository.findRecentEvents(since);
    }
    
    /**
     * Get failed login attempts for a user
     */
    public java.util.List<UserAuditEvent> getFailedLoginAttempts(Long userId, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return auditEventRepository.findFailedLoginsByUserSince(userId, since);
    }
    
    /**
     * Get failed login attempts from an IP address
     */
    public java.util.List<UserAuditEvent> getFailedLoginsByIP(String ipAddress, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return auditEventRepository.findFailedLoginsByIpSince(ipAddress, since);
    }
} 