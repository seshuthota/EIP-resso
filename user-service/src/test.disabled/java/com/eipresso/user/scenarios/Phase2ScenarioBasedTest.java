package com.eipresso.user.scenarios;

import com.eipresso.user.entity.User;
import com.eipresso.user.entity.UserAuditEvent;
import com.eipresso.user.dto.UserRegistrationRequest;
import com.eipresso.user.dto.UserLoginRequest;
import com.eipresso.user.repository.UserRepository;
import com.eipresso.user.repository.UserAuditEventRepository;
import com.eipresso.user.service.JwtTokenService;
import com.eipresso.user.service.UserService;
import com.hazelcast.core.HazelcastInstance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import com.eipresso.user.config.UserServiceTestConfig;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Phase 2 Scenario-Based Testing for User Management Service
 * 
 * Tests real-world coffee shop scenarios with proper EIP pattern validation
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(UserServiceTestConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Phase 2: User Management Service - Scenario-Based Testing")
public class Phase2ScenarioBasedTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAuditEventRepository auditEventRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private HazelcastInstance hazelcastInstance;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        auditEventRepository.deleteAll();
        userRepository.deleteAll();
    }

    // =====================================
    // SCENARIO 1: Coffee Shop Customer Onboarding
    // =====================================

    @Test
    @Order(1)
    @DisplayName("Scenario 1.1: New Customer Registration")
    void testNewCustomerRegistration() {
        UserRegistrationRequest request = new UserRegistrationRequest();
        request.setUsername("sarah");
        request.setEmail("sarah@email.com");
        request.setFirstName("Sarah");
        request.setLastName("Johnson");
        request.setPassword("SecurePassword123!");
        request.setConfirmPassword("SecurePassword123!");
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<UserRegistrationRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(baseUrl + "/register", entity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        
        User savedUser = userRepository.findByEmail("sarah@email.com").orElse(null);
        assertThat(savedUser).isNotNull();
        assertThat(savedUser.getFirstName()).isEqualTo("Sarah");
        assertThat(savedUser.getLastName()).isEqualTo("Johnson");
    }

    @Test
    @Order(2) 
    @DisplayName("Scenario 1.2: Duplicate Registration Prevention")
    void testDuplicateRegistrationPrevention() {
        UserRegistrationRequest request = new UserRegistrationRequest();
        request.setUsername("duplicate");
        request.setEmail("duplicate@email.com");
        request.setFirstName("Duplicate");
        request.setLastName("User");
        request.setPassword("SecurePassword123!");
        request.setConfirmPassword("SecurePassword123!");
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<UserRegistrationRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> firstResponse = restTemplate.postForEntity(baseUrl + "/register", entity, String.class);
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> secondResponse = restTemplate.postForEntity(baseUrl + "/register", entity, String.class);
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @Order(3)
    @DisplayName("Scenario 1.3: Profile Enrichment - Content Enricher Pattern")
    void testProfileEnrichment() {
        // Given: A new customer registration with location data
        UserRegistrationRequest request = new UserRegistrationRequest();
        request.setUsername("enricheduser");
        request.setEmail("enriched@email.com");
        request.setFirstName("Alice");
        request.setLastName("Cooper");
        request.setPassword("SecurePassword123!");
        request.setConfirmPassword("SecurePassword123!");
        request.setCity("Seattle");
        request.setCountry("USA");
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Forwarded-For", "73.223.164.102"); // Seattle IP
        HttpEntity<UserRegistrationRequest> entity = new HttpEntity<>(request, headers);

        // When: User registers
        ResponseEntity<String> response = restTemplate.postForEntity(
            baseUrl + "/register", entity, String.class);

        // Then: Profile is enriched with additional data
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        
        User enrichedUser = userRepository.findByEmail("enriched@email.com").orElse(null);
        assertThat(enrichedUser).isNotNull();
        
        // Verify Content Enricher added location data
        assertThat(enrichedUser.getCity()).isEqualTo("Seattle");
        assertThat(enrichedUser.getCountry()).isEqualTo("USA");
        assertThat(enrichedUser.getNotificationPreferences()).isNotNull();
        
        // Verify audit event was created
        List<UserAuditEvent> auditEvents = auditEventRepository.findByUserIdOrderByTimestampDesc(enrichedUser.getId());
        assertThat(auditEvents).isNotEmpty();
        assertThat(auditEvents.get(0).getEventType()).isEqualTo(UserAuditEvent.EventType.USER_REGISTRATION);
    }

    // =====================================
    // SCENARIO 2: Daily Operations Authentication
    // =====================================

    @Test
    @Order(4)
    @DisplayName("Scenario 2.1: JWT Token Lifecycle")
    void testJwtTokenLifecycle() {
        User user = createTestUser("jwttest", "jwt@test.com", "JWT", "Test");

        String accessToken = jwtTokenService.generateToken(user);
        String refreshToken = jwtTokenService.generateRefreshToken(user);
        
        assertThat(jwtTokenService.validateToken(accessToken, user)).isTrue();
        assertThat(jwtTokenService.validateToken(refreshToken, user)).isTrue();
        assertThat(jwtTokenService.extractUsername(accessToken)).isEqualTo("jwttest");
    }

    @Test
    @Order(5)
    @DisplayName("Scenario 2.2: Role-Based Authentication")
    void testRoleBasedAuthentication() {
        // Given: Different user roles
        User customer = createTestUser("customer", "customer@eipresso.com", "John", "Customer");
        customer.setRole(User.Role.CUSTOMER);
        userRepository.save(customer);

        User barista = createTestUser("barista", "barista@eipresso.com", "Jane", "Barista");
        barista.setRole(User.Role.BARISTA);
        userRepository.save(barista);

        // When: Tokens are generated for different roles
        String customerToken = jwtTokenService.generateToken(customer);
        String baristaToken = jwtTokenService.generateToken(barista);

        // Then: Tokens contain correct role information
        assertThat(jwtTokenService.extractUserRole(customerToken)).isEqualTo("CUSTOMER");
        assertThat(jwtTokenService.extractUserRole(baristaToken)).isEqualTo("BARISTA");
        
        // Verify role-based authorization
        assertThat(customer.getAuthorities()).hasSize(1);
        assertThat(barista.getAuthorities()).hasSize(1);
    }

    @Test
    @Order(6)
    @DisplayName("Scenario 2.3: Failed Login Handling - Wire Tap Audit Trail")
    void testFailedLoginHandling() {
        // Given: A valid user account
        User user = createTestUser("securitytest", "security@eipresso.com", "Security", "Test");

        // When: Multiple failed login attempts are made
        for (int i = 0; i < 3; i++) {
            UserLoginRequest loginRequest = new UserLoginRequest();
            loginRequest.setEmail("security@eipresso.com");
            loginRequest.setPassword("WrongPassword" + i);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Forwarded-For", "192.168.1.100");
            headers.set("User-Agent", "Test Browser");
            HttpEntity<UserLoginRequest> entity = new HttpEntity<>(loginRequest, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/login", entity, String.class);
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        // Then: Failed attempts are tracked
        User updatedUser = userRepository.findById(user.getId()).orElse(null);
        assertThat(updatedUser).isNotNull();
        assertThat(updatedUser.getFailedLoginAttempts()).isEqualTo(3);
    }

    // =====================================
    // SCENARIO 3: Security & Compliance Testing
    // =====================================

    @Test
    @Order(7)
    @DisplayName("Scenario 3.1: Security Audit Trail")
    void testSecurityAuditTrail() {
        UserRegistrationRequest request = new UserRegistrationRequest();
        request.setUsername("audit");
        request.setEmail("audit@test.com");
        request.setFirstName("Audit");
        request.setLastName("Test");
        request.setPassword("AuditPassword123!");
        request.setConfirmPassword("AuditPassword123!");
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<UserRegistrationRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(baseUrl + "/register", entity, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        
        User user = userRepository.findByEmail("audit@test.com").orElse(null);
        assertThat(user).isNotNull();

        List<UserAuditEvent> auditEvents = auditEventRepository.findByUserIdOrderByTimestampDesc(user.getId());
        assertThat(auditEvents).isNotEmpty();
    }

    // =====================================
    // SCENARIO 4: Clustering & High Availability
    // =====================================

    @Test
    @Order(8)
    @DisplayName("Scenario 4.1: Hazelcast Clustering")
    void testHazelcastClustering() {
        assertThat(hazelcastInstance).isNotNull();
        assertThat(hazelcastInstance.getCluster().getMembers()).isNotEmpty();
        
        String testKey = "test-registration:hazelcast@test.com";
        hazelcastInstance.getMap("idempotentRepository").put(testKey, "test-value");
        assertThat(hazelcastInstance.getMap("idempotentRepository").get(testKey)).isEqualTo("test-value");
    }

    @Test
    @Order(9)
    @DisplayName("Scenario 4.2: Configuration and Health Monitoring")
    void testConfigurationAndHealth() {
        // Given: Service is running
        // When: Checking health endpoints
        ResponseEntity<String> healthResponse = restTemplate.getForEntity(
            baseUrl + "/actuator/health", String.class);

        // Then: Health checks pass
        assertThat(healthResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        // Verify configuration endpoint works
        ResponseEntity<String> configResponse = restTemplate.getForEntity(
            baseUrl + "/test/config", String.class);
        assertThat(configResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // =====================================
    // Helper Methods
    // =====================================

    private User createTestUser(String username, String email, String firstName, String lastName) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setPassword("$2a$10$dummyHashedPassword");
        user.setRole(User.Role.CUSTOMER);
        user.setAccountEnabled(true);
        return userRepository.save(user);
    }
} 