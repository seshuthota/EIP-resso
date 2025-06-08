package com.eipresso.user.controller;

import com.eipresso.user.dto.UserRegistrationRequest;
import com.eipresso.user.service.JwtTokenService;
import com.eipresso.user.service.SecurityAuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hazelcast.core.HazelcastInstance;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*", maxAge = 3600)
public class UserController {
    
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    
    @Autowired
    private ProducerTemplate producerTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private JwtTokenService jwtTokenService;
    
    @Autowired
    private SecurityAuditService securityAuditService;
    
    @Autowired
    private HazelcastInstance hazelcastInstance;
    
    /**
     * User registration endpoint
     * Delegates to Camel route with EIP patterns (Idempotent Consumer, Wire Tap, Content Enricher)
     */
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody UserRegistrationRequest request,
                                        HttpServletRequest httpRequest) {
        try {
            logger.info("Received user registration request for: {}", request.getEmail());
            
            // Set HTTP headers for Camel processing
            Map<String, Object> headers = extractHttpHeaders(httpRequest);
            
            // Send to Camel route for processing with EIP patterns
            Object result = producerTemplate.requestBodyAndHeaders("direct:user-registration", request, headers);
            
            if (result instanceof Map) {
                Map<String, Object> response = (Map<String, Object>) result;
                boolean success = (Boolean) response.getOrDefault("success", false);
                
                if (success) {
                    logger.info("User registration successful for: {}", request.getEmail());
                    return ResponseEntity.status(HttpStatus.CREATED).body(response);
                } else {
                    logger.warn("User registration failed for: {}", request.getEmail());
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                }
            }
            
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
            
        } catch (Exception e) {
            logger.error("User registration error for {}: {}", request.getEmail(), e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }
    
    /**
     * User authentication endpoint
     * Delegates to Camel route with security audit (Wire Tap) and error handling (Dead Letter Channel)
     */
    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody Map<String, String> credentials,
                                            HttpServletRequest httpRequest) {
        try {
            String usernameOrEmail = credentials.get("username");
            logger.info("Received authentication request for: {}", usernameOrEmail);
            
            // Set HTTP headers for Camel processing and audit trail
            Map<String, Object> headers = extractHttpHeaders(httpRequest);
            
            // Send to Camel route for processing with Wire Tap audit
            Object result = producerTemplate.requestBodyAndHeaders("direct:user-authentication", credentials, headers);
            
            if (result instanceof Map) {
                Map<String, Object> response = (Map<String, Object>) result;
                boolean success = (Boolean) response.getOrDefault("success", false);
                
                if (success) {
                    logger.info("Authentication successful for: {}", usernameOrEmail);
                    return ResponseEntity.ok(response);
                } else {
                    logger.warn("Authentication failed for: {}", usernameOrEmail);
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
                }
            }
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("Authentication error: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Authentication failed");
            errorResponse.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
        }
    }
    
    /**
     * Token validation endpoint for API Gateway
     */
    @PostMapping("/token/validate")
    public ResponseEntity<?> validateToken(@RequestBody Map<String, String> request, 
                                         HttpServletRequest httpRequest) {
        try {
            String token = request.get("token");
            if (token == null || token.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Token is required"));
            }
            
            // Check if token is blacklisted
            if (hazelcastInstance.getMap("jwt-blacklist").containsKey(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Token has been invalidated"));
            }
            
            // Validate token
            if (!jwtTokenService.isValidToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or expired token"));
            }
            
            return ResponseEntity.ok(Map.of("valid", true));
            
        } catch (Exception e) {
            logger.error("Token validation error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Token validation failed"));
        }
    }
    
    /**
     * User profile endpoint (requires authentication)
     */
    @GetMapping("/profile")
    public ResponseEntity<?> getUserProfile(@RequestHeader("Authorization") String authHeader,
                                          HttpServletRequest httpRequest) {
        try {
            // Extract JWT token and validate
            String token = authHeader.replace("Bearer ", "");
            
            if (!jwtTokenService.isValidToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or expired token"));
            }
            
            String username = jwtTokenService.extractUsername(token);
            Long userId = jwtTokenService.extractUserId(token);
            String userRole = jwtTokenService.extractUserRole(token);
            
            Map<String, Object> response = new HashMap<>();
            response.put("userId", userId);
            response.put("username", username);
            response.put("role", userRole);
            response.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Profile retrieval error: {}", e.getMessage());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Unauthorized");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
        }
    }
    
    /**
     * Logout endpoint - Blacklist JWT token
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String authHeader,
                                  HttpServletRequest httpRequest) {
        try {
            String token = authHeader.replace("Bearer ", "");
            String userId = jwtTokenService.extractUserId(token).toString();
            
            // Add token to blacklist
            hazelcastInstance.getMap("jwt-blacklist").put(token, "BLACKLISTED", 
                jwtTokenService.getTokenExpirationTime(), java.util.concurrent.TimeUnit.SECONDS);
            
            // Log security event
            String ipAddress = getClientIpAddress(httpRequest);
            securityAuditService.logTokenBlacklist(userId, "User logout");
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Logout successful");
            response.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Logout error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Logout failed"));
        }
    }
    
    /**
     * Health check endpoint for user service
     * Delegates to Camel health check route
     */
    @GetMapping("/health")
    public ResponseEntity<?> healthCheck() {
        try {
            Object result = producerTemplate.requestBody("direct:user-service-health", "");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Health check failed: {}", e.getMessage());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "DOWN");
            errorResponse.put("error", e.getMessage());
            errorResponse.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
        }
    }
    
    /**
     * Get recent audit events (admin endpoint)
     */
    @GetMapping("/audit/recent")
    public ResponseEntity<?> getRecentAuditEvents(@RequestParam(defaultValue = "24") int hours) {
        try {
            // This would typically require admin authorization
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Recent audit events for last " + hours + " hours");
            response.put("note", "This endpoint would return security audit events");
            response.put("hours", hours);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Audit retrieval error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Extract HTTP headers for Camel processing
     * Used for IP tracking, User-Agent, and audit trail
     */
    private Map<String, Object> extractHttpHeaders(HttpServletRequest request) {
        Map<String, Object> headers = new HashMap<>();
        
        // Extract IP address for geolocation enrichment
        String ipAddress = getClientIpAddress(request);
        headers.put("X-Real-IP", ipAddress);
        
        // Extract User-Agent for audit trail
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null) {
            headers.put("User-Agent", userAgent);
        }
        
        // Extract session information
        String sessionId = request.getSession().getId();
        headers.put("X-Session-ID", sessionId);
        
        // Extract forwarded headers
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null) {
            headers.put("X-Forwarded-For", xForwardedFor);
        }
        
        return headers;
    }
    
    /**
     * Get real client IP address
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String[] headerNames = {
            "X-Forwarded-For",
            "X-Real-IP", 
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "HTTP_VIA",
            "REMOTE_ADDR"
        };
        
        for (String header : headerNames) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // Handle comma-separated IPs
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        }
        
        return request.getRemoteAddr();
    }
} 