package com.eipresso.user.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_audit_events", indexes = {
    @Index(name = "idx_audit_user_id", columnList = "user_id"),
    @Index(name = "idx_audit_event_type", columnList = "event_type"),
    @Index(name = "idx_audit_timestamp", columnList = "timestamp")
})
public class UserAuditEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id")
    private Long userId;
    
    @Column(name = "username")
    private String username;
    
    @Column(name = "email")
    private String email;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;
    
    @Column(name = "event_description")
    private String eventDescription;
    
    @Column(name = "ip_address")
    private String ipAddress;
    
    @Column(name = "user_agent")
    private String userAgent;
    
    @Column(name = "session_id")
    private String sessionId;
    
    @Column(name = "additional_data", columnDefinition = "TEXT")
    private String additionalData;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private Severity severity = Severity.INFO;
    
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();
    
    // Constructors
    public UserAuditEvent() {}
    
    public UserAuditEvent(Long userId, String username, String email, EventType eventType, String eventDescription) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.eventType = eventType;
        this.eventDescription = eventDescription;
    }
    
    public UserAuditEvent(EventType eventType, String eventDescription, Severity severity) {
        this.eventType = eventType;
        this.eventDescription = eventDescription;
        this.severity = severity;
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }
    
    public String getEventDescription() { return eventDescription; }
    public void setEventDescription(String eventDescription) { this.eventDescription = eventDescription; }
    
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    
    public String getAdditionalData() { return additionalData; }
    public void setAdditionalData(String additionalData) { this.additionalData = additionalData; }
    
    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    
    public enum EventType {
        USER_REGISTRATION,
        USER_LOGIN_SUCCESS,
        USER_LOGIN_FAILED,
        USER_LOGOUT,
        PASSWORD_CHANGE,
        PASSWORD_RESET_REQUEST,
        PASSWORD_RESET_SUCCESS,
        EMAIL_VERIFICATION,
        PROFILE_UPDATE,
        ACCOUNT_LOCKED,
        ACCOUNT_UNLOCKED,
        ACCOUNT_DISABLED,
        ACCOUNT_ENABLED,
        JWT_TOKEN_GENERATED,
        JWT_TOKEN_REFRESH,
        JWT_TOKEN_INVALIDATED,
        SUSPICIOUS_ACTIVITY,
        DATA_EXPORT_REQUEST,
        ACCOUNT_DELETION_REQUEST
    }
    
    public enum Severity {
        INFO, WARNING, ERROR, CRITICAL
    }
} 