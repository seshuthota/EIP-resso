package com.eipresso.user.service;

import com.eipresso.user.dto.UserRegistrationRequest;
import com.eipresso.user.entity.User;
import com.eipresso.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class UserService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private JwtTokenService jwtTokenService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * Process user registration - called from Camel route
     * Implements validation, password encoding, and user creation
     */
    @Transactional
    public Map<String, Object> processUserRegistration(Object requestBody) {
        try {
            UserRegistrationRequest request;
            
            // Handle different input types from Camel
            if (requestBody instanceof UserRegistrationRequest) {
                request = (UserRegistrationRequest) requestBody;
            } else if (requestBody instanceof String) {
                request = objectMapper.readValue((String) requestBody, UserRegistrationRequest.class);
            } else {
                throw new IllegalArgumentException("Invalid request body type: " + requestBody.getClass());
            }
            
            logger.info("Processing user registration for: {}", request.getEmail());
            
            // Validate password matching
            if (!request.isPasswordMatching()) {
                throw new IllegalArgumentException("Passwords do not match");
            }
            
            // Check for existing users (Idempotent Consumer pattern will handle duplicates at Camel level)
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new IllegalArgumentException("User with email already exists: " + request.getEmail());
            }
            
            if (userRepository.existsByUsername(request.getUsername())) {
                throw new IllegalArgumentException("Username already taken: " + request.getUsername());
            }
            
            // Create new user
            User user = new User();
            user.setUsername(request.getUsername());
            user.setEmail(request.getEmail());
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            user.setFirstName(request.getFirstName());
            user.setLastName(request.getLastName());
            user.setPhoneNumber(request.getPhoneNumber());
            user.setAddress(request.getAddress());
            user.setCity(request.getCity());
            user.setCountry(request.getCountry());
            user.setPostalCode(request.getPostalCode());
            user.setPreferredLanguage(request.getPreferredLanguage());
            user.setTimezone(request.getTimezone());
            user.setRole(User.Role.CUSTOMER);
            user.setAccountEnabled(true);
            user.setEmailVerified(false); // Will be verified through email confirmation
            
            // Save user
            User savedUser = userRepository.save(user);
            
            // Prepare response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "User registered successfully");
            response.put("userId", savedUser.getId());
            response.put("username", savedUser.getUsername());
            response.put("email", savedUser.getEmail());
            response.put("timestamp", LocalDateTime.now());
            
            logger.info("User registration completed successfully for: {}", savedUser.getEmail());
            return response;
            
        } catch (Exception e) {
            logger.error("User registration failed: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now());
            
            throw new RuntimeException("Registration failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Process user authentication - called from Camel route
     * Implements login validation, failed attempt tracking, and JWT generation
     */
    @Transactional
    public Map<String, Object> processUserAuthentication(Object requestBody) {
        try {
            Map<String, String> credentials;
            
            // Handle different input types from Camel
            if (requestBody instanceof Map) {
                credentials = (Map<String, String>) requestBody;
            } else if (requestBody instanceof String) {
                credentials = objectMapper.readValue((String) requestBody, Map.class);
            } else {
                throw new IllegalArgumentException("Invalid credentials format");
            }
            
            String usernameOrEmail = credentials.get("username");
            String password = credentials.get("password");
            
            if (usernameOrEmail == null || password == null) {
                throw new IllegalArgumentException("Username/email and password are required");
            }
            
            logger.info("Processing authentication for: {}", usernameOrEmail);
            
            // Find user
            User user = userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
            
            // Check account status
            if (!user.isEnabled()) {
                throw new IllegalArgumentException("Account is disabled");
            }
            
            if (!user.isAccountNonLocked()) {
                throw new IllegalArgumentException("Account is locked due to failed login attempts");
            }
            
            // Validate password
            if (!passwordEncoder.matches(password, user.getPassword())) {
                // Increment failed login attempts
                user.incrementFailedLoginAttempts();
                userRepository.save(user);
                
                throw new IllegalArgumentException("Invalid credentials");
            }
            
            // Successful authentication
            user.resetFailedLoginAttempts();
            userRepository.save(user);
            
            // Generate JWT token
            String accessToken = jwtTokenService.generateToken(user);
            String refreshToken = jwtTokenService.generateRefreshToken(user);
            
            // Prepare response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Authentication successful");
            response.put("userId", user.getId());
            response.put("username", user.getUsername());
            response.put("email", user.getEmail());
            response.put("role", user.getRole().name());
            response.put("accessToken", accessToken);
            response.put("refreshToken", refreshToken);
            response.put("tokenType", "Bearer");
            response.put("expiresIn", jwtTokenService.getTokenExpirationTime());
            response.put("timestamp", LocalDateTime.now());
            
            logger.info("Authentication successful for: {}", user.getEmail());
            return response;
            
        } catch (Exception e) {
            logger.error("Authentication failed: {}", e.getMessage());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now());
            
            throw new RuntimeException("Authentication failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Content Enricher Pattern - Enhance user profile with additional data
     * Called from Camel route during registration
     */
    public void enrichUserProfile(Exchange exchange) {
        try {
            // Extract enrichment data from exchange properties (set by Content Enricher route)
            String userIP = exchange.getProperty("userIP", String.class);
            String userTimezone = exchange.getProperty("userTimezone", String.class);
            String userCountry = exchange.getProperty("userCountry", String.class);
            
            // Get the request body
            Object requestBody = exchange.getIn().getBody();
            
            if (requestBody instanceof UserRegistrationRequest) {
                UserRegistrationRequest request = (UserRegistrationRequest) requestBody;
                
                // Enrich with geolocation data
                if (request.getTimezone() == null && userTimezone != null) {
                    request.setTimezone(userTimezone);
                }
                
                if (request.getCountry() == null && userCountry != null) {
                    request.setCountry(userCountry);
                }
                
                // Set enhanced properties back to exchange
                exchange.setProperty("enrichedIP", userIP);
                exchange.setProperty("enrichedTimezone", userTimezone);
                exchange.setProperty("enrichedCountry", userCountry);
                
                logger.info("User profile enriched with IP: {}, Timezone: {}, Country: {}", 
                           userIP, userTimezone, userCountry);
            }
            
        } catch (Exception e) {
            logger.warn("Failed to enrich user profile: {}", e.getMessage());
            // Don't fail the registration if enrichment fails
        }
    }
    
    /**
     * Health check methods for Camel health route
     */
    public boolean isDatabaseHealthy() {
        try {
            userRepository.count();
            return true;
        } catch (Exception e) {
            logger.error("Database health check failed: {}", e.getMessage());
            return false;
        }
    }
    
    public boolean isCacheHealthy() {
        // Implement cache health check when cache is added
        return true;
    }
    
    /**
     * Find user by username or email
     */
    public User findByUsernameOrEmail(String usernameOrEmail) {
        return userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                .orElse(null);
    }
    
    /**
     * Unlock expired user accounts (could be scheduled)
     */
    @Transactional
    public void unlockExpiredAccounts() {
        LocalDateTime now = LocalDateTime.now();
        userRepository.findUsersToUnlock(now).forEach(user -> {
            userRepository.unlockUser(user.getId());
            logger.info("Unlocked expired account for user: {}", user.getUsername());
        });
    }
} 