package com.eipresso.user.camel;

import com.eipresso.user.entity.UserAuditEvent;
import com.eipresso.user.service.UserAuditService;
import com.eipresso.user.service.UserService;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Apache Camel Routes for User Authentication with Advanced EIP Patterns
 * 
 * EIP Patterns Implemented:
 * 1. Dead Letter Channel - Handle failed authentication attempts
 * 2. Idempotent Consumer - Prevent duplicate user registrations  
 * 3. Wire Tap - Security audit trail for all auth events
 * 4. Content Enricher - User profile enhancement with geolocation/preferences
 */
@Component
public class UserAuthenticationRoutes extends RouteBuilder {

    @Autowired
    private UserService userService;
    
    @Autowired
    private UserAuditService userAuditService;

    @Override
    public void configure() throws Exception {
        
        // Global Error Handler with Dead Letter Channel Pattern
        errorHandler(deadLetterChannel("direct:dead-letter-queue")
                .maximumRedeliveries(3)
                .redeliveryDelay(1000)
                .retryAttemptedLogLevel(LoggingLevel.WARN)
                .retriesExhaustedLogLevel(LoggingLevel.ERROR)
                .logStackTrace(true)
                .useOriginalMessage());
        
        // Dead Letter Channel Route - EIP Pattern Implementation
        from("direct:dead-letter-queue")
                .routeId("dead-letter-handler")
                .log(LoggingLevel.ERROR, "Processing failed message in Dead Letter Channel: ${body}")
                .wireTap("direct:audit-failed-operation")
                .to("log:dead-letter?level=ERROR&showBody=true&showHeaders=true")
                .process(exchange -> {
                    // Extract error information
                    Exception cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                    String errorMessage = cause != null ? cause.getMessage() : "Unknown error";
                    String operation = exchange.getIn().getHeader("operation", String.class);
                    
                    // Create audit event for failed operation
                    UserAuditEvent auditEvent = new UserAuditEvent(
                        UserAuditEvent.EventType.SUSPICIOUS_ACTIVITY,
                        "Failed operation: " + operation + " - " + errorMessage,
                        UserAuditEvent.Severity.ERROR
                    );
                    
                    exchange.getIn().setBody(auditEvent);
                })
                .to("direct:persist-audit-event");
        
        // User Registration Route with Idempotent Consumer Pattern
        from("direct:user-registration")
                .routeId("user-registration-route")
                .setHeader("operation", constant("USER_REGISTRATION"))
                .log(LoggingLevel.INFO, "Processing user registration: ${body}")
                
                // Idempotent Consumer Pattern - Prevent duplicate registrations using email
                .idempotentConsumer(jsonpath("$.email"))
                .idempotentRepository("hazelcastIdempotentRepository")
                .skipDuplicate(true)
                
                // Wire Tap Pattern - Audit trail for registration attempts
                .wireTap("direct:audit-registration-attempt")
                
                // Content Enricher Pattern - Enhance user profile with additional data
                .to("direct:enrich-user-profile")
                
                // Process registration
                .process(exchange -> {
                    try {
                        // Delegate to UserService for registration logic
                        Object result = userService.processUserRegistration(exchange.getIn().getBody());
                        exchange.getIn().setBody(result);
                        exchange.setProperty("registrationSuccess", true);
                    } catch (Exception e) {
                        exchange.setProperty("registrationSuccess", false);
                        exchange.setProperty("registrationError", e.getMessage());
                        throw e;
                    }
                })
                
                // Wire Tap - Audit successful registration
                .wireTap("direct:audit-registration-success")
                
                .log(LoggingLevel.INFO, "User registration completed successfully");
        
        // User Authentication Route with Security Audit
        from("direct:user-authentication")
                .routeId("user-authentication-route")
                .setHeader("operation", constant("USER_AUTHENTICATION"))
                .log(LoggingLevel.INFO, "Processing user authentication: ${headers}")
                
                // Wire Tap Pattern - Audit all authentication attempts
                .wireTap("direct:audit-authentication-attempt")
                
                .process(exchange -> {
                    try {
                        // Delegate to UserService for authentication logic
                        Object result = userService.processUserAuthentication(exchange.getIn().getBody());
                        exchange.getIn().setBody(result);
                        exchange.setProperty("authenticationSuccess", true);
                    } catch (Exception e) {
                        exchange.setProperty("authenticationSuccess", false);
                        exchange.setProperty("authenticationError", e.getMessage());
                        throw e;
                    }
                })
                
                // Wire Tap - Audit authentication result based on success/failure
                .wireTap("direct:audit-authentication-result")
                
                .log(LoggingLevel.INFO, "User authentication completed");
        
        // Content Enricher Pattern - Enhance user profile with geolocation and preferences
        from("direct:enrich-user-profile")
                .routeId("content-enricher-route")
                .log(LoggingLevel.INFO, "Enriching user profile with additional data")
                
                .process(exchange -> {
                    // Extract IP address from headers for geolocation enrichment
                    String ipAddress = exchange.getIn().getHeader("X-Forwarded-For", String.class);
                    if (ipAddress == null) {
                        ipAddress = exchange.getIn().getHeader("X-Real-IP", String.class);
                    }
                    if (ipAddress == null) {
                        ipAddress = "127.0.0.1"; // Default for local development
                    }
                    
                    // Enrich with geolocation data (mock implementation)
                    // In production, this would call a geolocation service
                    exchange.setProperty("userIP", ipAddress);
                    exchange.setProperty("userTimezone", "UTC"); // Default timezone
                    exchange.setProperty("userCountry", "Unknown");
                    
                    // Content enrichment logic would go here
                    userService.enrichUserProfile(exchange);
                })
                
                .log(LoggingLevel.INFO, "User profile enrichment completed");
        
        // Wire Tap Routes for Security Audit Trail
        from("direct:audit-registration-attempt")
                .routeId("audit-registration-attempt")
                .process(exchange -> {
                    UserAuditEvent auditEvent = new UserAuditEvent(
                        UserAuditEvent.EventType.USER_REGISTRATION,
                        "User registration attempt",
                        UserAuditEvent.Severity.INFO
                    );
                    
                    // Extract user details from registration request
                    userAuditService.enrichAuditEventFromRequest(auditEvent, exchange);
                    exchange.getIn().setBody(auditEvent);
                })
                .to("direct:persist-audit-event");
        
        from("direct:audit-registration-success")
                .routeId("audit-registration-success")
                .process(exchange -> {
                    UserAuditEvent auditEvent = new UserAuditEvent(
                        UserAuditEvent.EventType.USER_REGISTRATION,
                        "User registration successful",
                        UserAuditEvent.Severity.INFO
                    );
                    
                    userAuditService.enrichAuditEventFromRequest(auditEvent, exchange);
                    exchange.getIn().setBody(auditEvent);
                })
                .to("direct:persist-audit-event");
        
        from("direct:audit-authentication-attempt")
                .routeId("audit-authentication-attempt")
                .process(exchange -> {
                    UserAuditEvent auditEvent = new UserAuditEvent(
                        UserAuditEvent.EventType.USER_LOGIN_FAILED,
                        "User authentication attempt",
                        UserAuditEvent.Severity.INFO
                    );
                    
                    userAuditService.enrichAuditEventFromRequest(auditEvent, exchange);
                    exchange.getIn().setBody(auditEvent);
                })
                .to("direct:persist-audit-event");
        
        from("direct:audit-authentication-result")
                .routeId("audit-authentication-result")
                .process(exchange -> {
                    Boolean success = exchange.getProperty("authenticationSuccess", Boolean.class);
                    String errorMessage = exchange.getProperty("authenticationError", String.class);
                    
                    UserAuditEvent auditEvent;
                    if (Boolean.TRUE.equals(success)) {
                        auditEvent = new UserAuditEvent(
                            UserAuditEvent.EventType.USER_LOGIN_SUCCESS,
                            "User authentication successful",
                            UserAuditEvent.Severity.INFO
                        );
                    } else {
                        auditEvent = new UserAuditEvent(
                            UserAuditEvent.EventType.USER_LOGIN_FAILED,
                            "User authentication failed: " + (errorMessage != null ? errorMessage : "Unknown error"),
                            UserAuditEvent.Severity.WARNING
                        );
                    }
                    
                    userAuditService.enrichAuditEventFromRequest(auditEvent, exchange);
                    exchange.getIn().setBody(auditEvent);
                })
                .to("direct:persist-audit-event");
        
        from("direct:audit-failed-operation")
                .routeId("audit-failed-operation")
                .process(exchange -> {
                    String operation = exchange.getIn().getHeader("operation", String.class);
                    Exception cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                    String errorMessage = cause != null ? cause.getMessage() : "Unknown error";
                    
                    UserAuditEvent auditEvent = new UserAuditEvent(
                        UserAuditEvent.EventType.SUSPICIOUS_ACTIVITY,
                        "Failed operation: " + operation + " - " + errorMessage,
                        UserAuditEvent.Severity.ERROR
                    );
                    
                    userAuditService.enrichAuditEventFromRequest(auditEvent, exchange);
                    exchange.getIn().setBody(auditEvent);
                })
                .to("direct:persist-audit-event");
        
        // Audit Event Persistence Route
        from("direct:persist-audit-event")
                .routeId("persist-audit-event")
                .log(LoggingLevel.DEBUG, "Persisting audit event: ${body}")
                .process(exchange -> {
                    UserAuditEvent auditEvent = exchange.getIn().getBody(UserAuditEvent.class);
                    userAuditService.saveAuditEvent(auditEvent);
                })
                .log(LoggingLevel.DEBUG, "Audit event persisted successfully");
        
        // Health Check Route
        from("direct:user-service-health")
                .routeId("user-service-health-check")
                .process(exchange -> {
                    // Perform health checks
                    boolean dbHealthy = userService.isDatabaseHealthy();
                    boolean cacheHealthy = userService.isCacheHealthy();
                    
                    String status = (dbHealthy && cacheHealthy) ? "UP" : "DOWN";
                    
                    exchange.getIn().setBody(String.format(
                        "{\"status\": \"%s\", \"database\": \"%s\", \"cache\": \"%s\", \"timestamp\": \"%s\"}",
                        status,
                        dbHealthy ? "UP" : "DOWN",
                        cacheHealthy ? "UP" : "DOWN",
                        java.time.LocalDateTime.now()
                    ));
                })
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"));
    }
} 