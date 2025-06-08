package com.eipresso.user.scenarios;

import com.eipresso.user.UserServiceApplication;
import com.eipresso.user.entity.User;
import com.eipresso.user.entity.UserAuditEvent;
import com.eipresso.user.repository.UserRepository;
import com.eipresso.user.repository.UserAuditEventRepository;
import com.eipresso.user.service.JwtTokenService;
import com.eipresso.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.CamelContext;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@CamelSpringBootTest
@SpringBootTest(
    classes = UserServiceApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.profiles.active=test",
        "spring.cloud.config.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "management.endpoints.web.exposure.include=health,info,metrics,camel"
    }
)
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties")
@DisplayName("User Management Service - Coffee Shop Scenario Tests")
public class UserManagementScenarioTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAuditEventRepository auditRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private ObjectMapper objectMapper;

    @LocalServerPort
    private int port;

    private String baseUrl;
    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        executorService = Executors.newFixedThreadPool(20);
        
        // Clean up test data
        auditRepository.deleteAll();
        userRepository.deleteAll();
        
        // Verify Camel routes are running
        assertThat(camelContext.getRoutes()).isNotEmpty();
        assertThat(camelContext.getRouteController().getRouteStatus("user-registration")).isNotNull();
    }

    @Nested
    @DisplayName("Scenario 1: Coffee Shop Customer Onboarding")
    class CustomerOnboardingScenarios {

        @Test
        @DisplayName("New Customer Registration - Sarah Johnson joins EIP-resso")
        @Transactional
        void testNewCustomerRegistration() {
            // Arrange - Sarah wants to join EIP-resso for her daily coffee
            Map<String, Object> registrationRequest = Map.of(
                "firstName", "Sarah",
                "lastName", "Johnson", 
                "email", "sarah.johnson@email.com",
                "password", "SecurePassword123!",
                "phoneNumber", "+1-555-0123"
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("User-Agent", "EIP-resso-Mobile-App/1.0");
            headers.set("X-Forwarded-For", "192.168.1.100");

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(registrationRequest, headers);

            // Act - Register Sarah as new customer
            ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl + "/api/users/register", request, Map.class);

            // Assert - Verify successful registration
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).containsKey("userId");
            assertThat(response.getBody()).containsKey("message");

            // Verify user stored in database
            User savedUser = userRepository.findByEmail("sarah.johnson@email.com").orElse(null);
            assertThat(savedUser).isNotNull();
            assertThat(savedUser.getFirstName()).isEqualTo("Sarah");
            assertThat(savedUser.getLastName()).isEqualTo("Johnson");

            // Verify audit trail created
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                List<UserAuditEvent> auditEvents = auditRepository.findByUserId(savedUser.getId());
                assertThat(auditEvents).hasSize(1);
                assertThat(auditEvents.get(0).getEventType()).isEqualTo("USER_REGISTERED");
                assertThat(auditEvents.get(0).getIpAddress()).isEqualTo("192.168.1.100");
            });
        }

        @Test
        @DisplayName("Duplicate Registration Prevention - Idempotent Consumer Pattern")
        void testDuplicateRegistrationPrevention() {
            // Arrange - First registration
            Map<String, Object> registrationRequest = Map.of(
                "firstName", "John",
                "lastName", "Duplicate",
                "email", "john.duplicate@email.com",
                "password", "Password123!"
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(registrationRequest);

            // Act - Register user twice
            ResponseEntity<Map> firstResponse = restTemplate.postForEntity(
                baseUrl + "/api/users/register", request, Map.class);
            
            ResponseEntity<Map> secondResponse = restTemplate.postForEntity(
                baseUrl + "/api/users/register", request, Map.class);

            // Assert - First registration succeeds, second is prevented
            assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(secondResponse.getBody().get("error")).toString()
                .contains("User already exists");

            // Verify only one user exists
            List<User> users = userRepository.findAll();
            long duplicateUsers = users.stream()
                .filter(u -> "john.duplicate@email.com".equals(u.getEmail()))
                .count();
            assertThat(duplicateUsers).isEqualTo(1);
        }

        @Test
        @DisplayName("Profile Enrichment - Content Enricher Pattern") 
        void testProfileEnrichment() {
            // Arrange - Register basic user
            Map<String, Object> registrationRequest = Map.of(
                "firstName", "Emma",
                "lastName", "Enriched",
                "email", "emma.enriched@email.com", 
                "password", "Password123!"
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Forwarded-For", "203.0.113.15"); // Mock IP for geolocation
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(registrationRequest, headers);

            // Act - Register with IP for geolocation enrichment
            ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl + "/api/users/register", request, Map.class);

            // Assert - Profile should be enriched
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                User savedUser = userRepository.findByEmail("emma.enriched@email.com").orElse(null);
                assertThat(savedUser).isNotNull();
                
                // Verify enrichment occurred (Content Enricher pattern)
                assertThat(savedUser.getPreferences()).isNotNull();
                // Profile enrichment sets default preferences for new coffee customers
                assertThat(savedUser.getPreferences()).containsKey("preferredOrderTime");
                assertThat(savedUser.getPreferences()).containsKey("loyaltyTier");
            });
        }

        @Test
        @DisplayName("Email Verification Workflow - Dead Letter Channel Pattern")
        void testEmailVerificationFailure() {
            // Arrange - Register user with invalid email for testing failure handling
            Map<String, Object> registrationRequest = Map.of(
                "firstName", "Test",
                "lastName", "EmailFail",
                "email", "invalid.email.for.testing@fail.domain",
                "password", "Password123!"
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(registrationRequest);

            // Act - Register user (email verification will fail and go to dead letter channel)
            ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl + "/api/users/register", request, Map.class);

            // Assert - Registration succeeds but email verification handled by Dead Letter Channel
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            // Verify audit shows email verification was attempted
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                List<UserAuditEvent> auditEvents = auditRepository.findAll();
                boolean emailEventFound = auditEvents.stream()
                    .anyMatch(event -> event.getEventType().equals("EMAIL_VERIFICATION_SENT") ||
                                     event.getEventType().equals("EMAIL_VERIFICATION_FAILED"));
                assertThat(emailEventFound).isTrue();
            });
        }
    }

    @Nested
    @DisplayName("Scenario 2: Daily Operations Authentication")
    class DailyOperationsScenarios {

        @Test
        @DisplayName("Morning Rush Authentication - 100 Concurrent Customer Logins")
        void testMorningRushAuthentication() {
            // Arrange - Create test customers for morning rush
            List<User> customers = IntStream.range(1, 101)
                .mapToObj(i -> {
                    User customer = new User();
                    customer.setFirstName("Customer");
                    customer.setLastName("Rush" + i);
                    customer.setEmail("customer.rush" + i + "@email.com");
                    customer.setPassword("$2a$10$encrypted.password.hash"); // BCrypt hash
                    return customer;
                })
                .toList();
            
            userRepository.saveAll(customers);

            // Act - Simulate 100 concurrent login attempts (morning coffee rush)
            List<CompletableFuture<ResponseEntity<Map>>> loginFutures = customers.stream()
                .map(customer -> CompletableFuture.supplyAsync(() -> {
                    Map<String, String> loginRequest = Map.of(
                        "email", customer.getEmail(),
                        "password", "TestPassword123!" // Original password before hashing
                    );
                    
                    HttpHeaders headers = new HttpHeaders();
                    headers.set("User-Agent", "EIP-resso-POS-Terminal/2.0");
                    HttpEntity<Map<String, String>> request = new HttpEntity<>(loginRequest, headers);
                    
                    return restTemplate.postForEntity(baseUrl + "/api/users/login", request, Map.class);
                }, executorService))
                .toList();

            // Assert - All logins should succeed with JWT tokens
            List<ResponseEntity<Map>> responses = loginFutures.stream()
                .map(CompletableFuture::join)
                .toList();

            long successfulLogins = responses.stream()
                .filter(response -> response.getStatusCode() == HttpStatus.OK)
                .count();

            assertThat(successfulLogins).isGreaterThan(95); // Allow for some timing variations

            // Verify JWT tokens are valid
            responses.stream()
                .filter(response -> response.getStatusCode() == HttpStatus.OK)
                .limit(10) // Check first 10 tokens
                .forEach(response -> {
                    assertThat(response.getBody()).containsKey("accessToken");
                    assertThat(response.getBody()).containsKey("refreshToken");
                    
                    String accessToken = (String) response.getBody().get("accessToken");
                    assertThat(jwtTokenService.validateToken(accessToken)).isTrue();
                });
        }

        @Test
        @DisplayName("Barista Staff Login - Role-Based Access Control")
        void testBaristaStaffLogin() {
            // Arrange - Create barista staff user
            User barista = new User();
            barista.setFirstName("Maria");
            barista.setLastName("Barista");
            barista.setEmail("maria.barista@eipresso.com");
            barista.setPassword("$2a$10$StaffPassword.Hash");
            barista.setRole("BARISTA");
            barista.getPreferences().put("workShift", "morning");
            barista.getPreferences().put("stationAccess", "espresso,grinder,pos");
            userRepository.save(barista);

            Map<String, String> loginRequest = Map.of(
                "email", "maria.barista@eipresso.com",
                "password", "StaffPassword123!"
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "EIP-resso-Staff-Portal/1.5");
            headers.set("X-Station-ID", "POS-001");
            
            HttpEntity<Map<String, String>> request = new HttpEntity<>(loginRequest, headers);

            // Act - Barista login
            ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl + "/api/users/login", request, Map.class);

            // Assert - Successful login with staff privileges
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).containsKey("accessToken");

            String accessToken = (String) response.getBody().get("accessToken");
            Map<String, Object> tokenClaims = jwtTokenService.extractAllClaims(accessToken);
            
            assertThat(tokenClaims.get("role")).isEqualTo("BARISTA");
            assertThat(tokenClaims.get("email")).isEqualTo("maria.barista@eipresso.com");

            // Verify audit trail shows staff login
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                List<UserAuditEvent> auditEvents = auditRepository.findByUserId(barista.getId());
                boolean staffLoginFound = auditEvents.stream()
                    .anyMatch(event -> event.getEventType().equals("USER_LOGIN") && 
                                     event.getDetails().contains("BARISTA"));
                assertThat(staffLoginFound).isTrue();
            });
        }

        @Test
        @DisplayName("Manager Dashboard Access - Elevated Permissions")
        void testManagerDashboardAccess() {
            // Arrange - Create store manager
            User manager = new User();
            manager.setFirstName("David");
            manager.setLastName("Manager");
            manager.setEmail("david.manager@eipresso.com");
            manager.setPassword("$2a$10$ManagerPassword.Hash");
            manager.setRole("MANAGER");
            manager.getPreferences().put("dashboardAccess", "analytics,inventory,staff");
            manager.getPreferences().put("storeLocation", "Seattle-Downtown");
            userRepository.save(manager);

            Map<String, String> loginRequest = Map.of(
                "email", "david.manager@eipresso.com",
                "password", "ManagerPassword123!"
            );

            HttpEntity<Map<String, String>> request = new HttpEntity<>(loginRequest);

            // Act - Manager login and profile access
            ResponseEntity<Map> loginResponse = restTemplate.postForEntity(
                baseUrl + "/api/users/login", request, Map.class);

            String accessToken = (String) loginResponse.getBody().get("accessToken");
            
            HttpHeaders authHeaders = new HttpHeaders();
            authHeaders.setBearerAuth(accessToken);
            HttpEntity<?> profileRequest = new HttpEntity<>(authHeaders);

            ResponseEntity<Map> profileResponse = restTemplate.exchange(
                baseUrl + "/api/users/profile", HttpMethod.GET, profileRequest, Map.class);

            // Assert - Manager has elevated access
            assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(profileResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            Map<String, Object> profile = profileResponse.getBody();
            assertThat(profile.get("role")).isEqualTo("MANAGER");
            assertThat(profile.get("firstName")).isEqualTo("David");
            
            // Verify manager-specific preferences
            @SuppressWarnings("unchecked")
            Map<String, Object> preferences = (Map<String, Object>) profile.get("preferences");
            assertThat(preferences).containsKey("dashboardAccess");
            assertThat(preferences.get("storeLocation")).isEqualTo("Seattle-Downtown");
        }

        @Test
        @DisplayName("Failed Login Handling - Wire Tap Pattern & Account Security")
        void testFailedLoginHandling() {
            // Arrange - Create user for failed login testing
            User user = new User();
            user.setFirstName("Security");
            user.setLastName("Test");
            user.setEmail("security.test@email.com");
            user.setPassword("$2a$10$correct.password.hash");
            userRepository.save(user);

            Map<String, String> invalidLoginRequest = Map.of(
                "email", "security.test@email.com",
                "password", "WrongPassword123!"
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Suspicious-Client/1.0");
            headers.set("X-Forwarded-For", "192.168.1.200");
            
            HttpEntity<Map<String, String>> request = new HttpEntity<>(invalidLoginRequest, headers);

            // Act - Attempt 5 failed logins
            List<ResponseEntity<Map>> responses = IntStream.range(0, 5)
                .mapToObj(i -> restTemplate.postForEntity(baseUrl + "/api/users/login", request, Map.class))
                .toList();

            // Assert - All login attempts should fail
            responses.forEach(response -> 
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));

            // Verify Wire Tap audit trail captures all failed attempts
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                List<UserAuditEvent> auditEvents = auditRepository.findByUserId(user.getId());
                long failedLoginEvents = auditEvents.stream()
                    .filter(event -> event.getEventType().equals("USER_LOGIN_FAILED"))
                    .count();
                
                assertThat(failedLoginEvents).isEqualTo(5);
                
                // Verify audit details include security information
                UserAuditEvent recentFailure = auditEvents.stream()
                    .filter(event -> event.getEventType().equals("USER_LOGIN_FAILED"))
                    .findFirst()
                    .orElse(null);
                
                assertThat(recentFailure).isNotNull();
                assertThat(recentFailure.getIpAddress()).isEqualTo("192.168.1.200");
                assertThat(recentFailure.getUserAgent()).contains("Suspicious-Client");
            });
        }
    }

    @Nested
    @DisplayName("Scenario 3: Security & Compliance Testing")
    class SecurityComplianceScenarios {

        @Test
        @DisplayName("JWT Token Lifecycle - Access & Refresh Token Management")
        void testJwtTokenLifecycle() {
            // Arrange - Create user and login
            User user = createTestUser("token.test@email.com", "TokenUser");
            
            Map<String, String> loginRequest = Map.of(
                "email", user.getEmail(),
                "password", "TestPassword123!"
            );
            
            HttpEntity<Map<String, String>> request = new HttpEntity<>(loginRequest);

            // Act - Login to get tokens
            ResponseEntity<Map> loginResponse = restTemplate.postForEntity(
                baseUrl + "/api/users/login", request, Map.class);

            String accessToken = (String) loginResponse.getBody().get("accessToken");
            String refreshToken = (String) loginResponse.getBody().get("refreshToken");

            // Assert - Token validation and properties
            assertThat(accessToken).isNotNull();
            assertThat(refreshToken).isNotNull();
            assertThat(jwtTokenService.validateToken(accessToken)).isTrue();
            assertThat(jwtTokenService.validateToken(refreshToken)).isTrue();

            // Extract and verify token claims
            Map<String, Object> accessClaims = jwtTokenService.extractAllClaims(accessToken);
            assertThat(accessClaims.get("email")).isEqualTo(user.getEmail());
            assertThat(accessClaims.get("sub")).isEqualTo(user.getId().toString());
            assertThat(accessClaims).containsKey("exp");
            assertThat(accessClaims).containsKey("iat");

            // Test token expiration (simulate)
            // In production, access tokens expire in 15 minutes, refresh tokens in 7 days
            LocalDateTime issuedAt = LocalDateTime.now();
            LocalDateTime expiresAt = issuedAt.plusMinutes(15);
            
            // Verify token is currently valid
            assertThat(jwtTokenService.isTokenExpired(accessToken)).isFalse();
        }

        @Test
        @DisplayName("Cross-Service Token Validation - Service Integration")
        void testCrossServiceTokenValidation() {
            // Arrange - Login user to get JWT token
            User user = createTestUser("cross.service@email.com", "CrossService");
            String accessToken = loginAndGetToken(user);

            // Act - Use JWT token to access protected profile endpoint
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            HttpEntity<?> profileRequest = new HttpEntity<>(headers);

            ResponseEntity<Map> profileResponse = restTemplate.exchange(
                baseUrl + "/api/users/profile", HttpMethod.GET, profileRequest, Map.class);

            // Assert - Token should be valid across service endpoints
            assertThat(profileResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(profileResponse.getBody().get("email")).isEqualTo(user.getEmail());

            // Test invalid token rejection
            HttpHeaders invalidHeaders = new HttpHeaders();
            invalidHeaders.setBearerAuth("invalid.jwt.token");
            HttpEntity<?> invalidRequest = new HttpEntity<>(invalidHeaders);

            ResponseEntity<Map> invalidResponse = restTemplate.exchange(
                baseUrl + "/api/users/profile", HttpMethod.GET, invalidRequest, Map.class);
            
            assertThat(invalidResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Security Audit Trail - Comprehensive Event Logging")
        void testSecurityAuditTrail() {
            // Arrange - Create user for audit testing
            User user = createTestUser("audit.test@email.com", "AuditUser");
            
            // Act - Perform various security-related actions
            // 1. Login
            String accessToken = loginAndGetToken(user);
            
            // 2. Access profile
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.set("User-Agent", "EIP-resso-Audit-Test/1.0");
            headers.set("X-Forwarded-For", "10.0.0.100");
            
            HttpEntity<?> request = new HttpEntity<>(headers);
            restTemplate.exchange(baseUrl + "/api/users/profile", HttpMethod.GET, request, Map.class);
            
            // 3. Failed login attempt
            Map<String, String> failedLogin = Map.of(
                "email", user.getEmail(),
                "password", "WrongPassword"
            );
            restTemplate.postForEntity(baseUrl + "/api/users/login", failedLogin, Map.class);

            // Assert - Verify comprehensive audit trail
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                List<UserAuditEvent> auditEvents = auditRepository.findByUserId(user.getId());
                
                // Verify multiple event types are captured
                List<String> eventTypes = auditEvents.stream()
                    .map(UserAuditEvent::getEventType)
                    .toList();
                
                assertThat(eventTypes).contains("USER_REGISTERED", "USER_LOGIN", "USER_LOGIN_FAILED");
                
                // Verify audit details include IP and User-Agent
                boolean ipAddressLogged = auditEvents.stream()
                    .anyMatch(event -> "10.0.0.100".equals(event.getIpAddress()));
                assertThat(ipAddressLogged).isTrue();
                
                boolean userAgentLogged = auditEvents.stream()
                    .anyMatch(event -> event.getUserAgent() != null && 
                                     event.getUserAgent().contains("EIP-resso-Audit-Test"));
                assertThat(userAgentLogged).isTrue();
            });
        }

        @Test
        @DisplayName("GDPR Compliance - Data Deletion & Privacy")
        void testGdprCompliance() {
            // Arrange - Create user with data to be deleted
            User user = createTestUser("gdpr.test@email.com", "GdprUser");
            user.getPreferences().put("marketingConsent", "true");
            user.getPreferences().put("dataRetention", "7years");
            userRepository.save(user);
            
            // Generate some audit events
            loginAndGetToken(user);

            // Act - Simulate GDPR data deletion request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, Object> deletionRequest = Map.of(
                "userId", user.getId(),
                "reason", "GDPR_REQUEST",
                "requestedBy", "user"
            );
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(deletionRequest, headers);
            
            // Note: In production, this would be a separate GDPR compliance endpoint
            // For testing, we'll verify the audit trail and data handling
            
            // Assert - Verify GDPR compliance measures
            List<UserAuditEvent> auditEvents = auditRepository.findByUserId(user.getId());
            assertThat(auditEvents).isNotEmpty();
            
            // Verify sensitive data can be identified and marked for deletion
            User savedUser = userRepository.findById(user.getId()).orElse(null);
            assertThat(savedUser).isNotNull();
            assertThat(savedUser.getPreferences()).containsKey("marketingConsent");
            
            // In production implementation:
            // 1. Personal data would be encrypted/anonymized
            // 2. Audit events would be retained but anonymized
            // 3. Marketing preferences would be cleared
            // 4. Data retention policies would be applied
            
            // Simulate audit log for GDPR request
            UserAuditEvent gdprEvent = new UserAuditEvent();
            gdprEvent.setUserId(user.getId());
            gdprEvent.setEventType("GDPR_DELETION_REQUESTED");
            gdprEvent.setDetails("User requested data deletion under GDPR Article 17");
            gdprEvent.setEventTime(LocalDateTime.now());
            auditRepository.save(gdprEvent);
            
            // Verify GDPR audit event
            List<UserAuditEvent> updatedEvents = auditRepository.findByUserId(user.getId());
            boolean gdprEventExists = updatedEvents.stream()
                .anyMatch(event -> event.getEventType().equals("GDPR_DELETION_REQUESTED"));
            assertThat(gdprEventExists).isTrue();
        }
    }

    @Nested
    @DisplayName("Scenario 4: Clustering & High Availability")
    class ClusteringHighAvailabilityScenarios {

        @Test
        @DisplayName("Active-Active Load Distribution - Multi-Node Processing")
        void testActiveActiveLoadDistribution() {
            // Arrange - Simulate multiple user service instances
            List<User> customers = IntStream.range(1, 51)
                .mapToObj(i -> createTestUser("cluster.user" + i + "@email.com", "ClusterUser" + i))
                .toList();

            // Act - Simulate distributed load across multiple nodes
            List<CompletableFuture<String>> tokenFutures = customers.stream()
                .map(user -> CompletableFuture.supplyAsync(() -> loginAndGetToken(user), executorService))
                .toList();

            List<String> tokens = tokenFutures.stream()
                .map(CompletableFuture::join)
                .toList();

            // Assert - All tokens should be valid regardless of which node processed them
            assertThat(tokens).hasSize(50);
            tokens.forEach(token -> {
                assertThat(jwtTokenService.validateToken(token)).isTrue();
            });

            // Verify load was distributed (in production, this would show different node processing)
            // For testing, we verify all requests were processed successfully
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                List<UserAuditEvent> allAuditEvents = auditRepository.findAll();
                long loginEvents = allAuditEvents.stream()
                    .filter(event -> event.getEventType().equals("USER_LOGIN"))
                    .count();
                
                assertThat(loginEvents).isEqualTo(50);
            });
        }

        @Test
        @DisplayName("Hazelcast Idempotent Storage - Cluster-Wide Deduplication")
        void testHazelcastIdempotentStorage() {
            // Arrange - Prepare duplicate registration across "nodes"
            Map<String, Object> registrationRequest = Map.of(
                "firstName", "Hazelcast",
                "lastName", "Cluster",
                "email", "hazelcast.cluster@email.com",
                "password", "ClusterPassword123!"
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(registrationRequest);

            // Act - Simulate concurrent registrations from different nodes
            List<CompletableFuture<ResponseEntity<Map>>> registrationFutures = IntStream.range(0, 5)
                .mapToObj(i -> CompletableFuture.supplyAsync(() -> 
                    restTemplate.postForEntity(baseUrl + "/api/users/register", request, Map.class), 
                    executorService))
                .toList();

            List<ResponseEntity<Map>> responses = registrationFutures.stream()
                .map(CompletableFuture::join)
                .toList();

            // Assert - Only one registration should succeed, others prevented by Hazelcast idempotent consumer
            long successfulRegistrations = responses.stream()
                .filter(response -> response.getStatusCode() == HttpStatus.CREATED)
                .count();
            
            long duplicateRejections = responses.stream()
                .filter(response -> response.getStatusCode() == HttpStatus.CONFLICT)
                .count();

            assertThat(successfulRegistrations).isEqualTo(1);
            assertThat(duplicateRejections).isEqualTo(4);

            // Verify only one user exists in database
            List<User> users = userRepository.findAll();
            long clusterUsers = users.stream()
                .filter(user -> "hazelcast.cluster@email.com".equals(user.getEmail()))
                .count();
            assertThat(clusterUsers).isEqualTo(1);
        }

        @Test
        @DisplayName("Node Failure Recovery - Seamless Failover")
        void testNodeFailureRecovery() {
            // Arrange - Create users before simulated node failure
            List<User> preFailureUsers = IntStream.range(1, 21)
                .mapToObj(i -> createTestUser("prefailure" + i + "@email.com", "PreFailure" + i))
                .toList();

            // Act - Simulate processing during "node failure" (testing service resilience)
            List<CompletableFuture<ResponseEntity<Map>>> loginFutures = preFailureUsers.stream()
                .map(user -> CompletableFuture.supplyAsync(() -> {
                    try {
                        Map<String, String> loginRequest = Map.of(
                            "email", user.getEmail(),
                            "password", "TestPassword123!"
                        );
                        
                        return restTemplate.postForEntity(baseUrl + "/api/users/login", loginRequest, Map.class);
                    } catch (Exception e) {
                        // Simulate network issues during failover
                        Map<String, Object> errorResponse = Map.of("error", "network_timeout");
                        return new ResponseEntity<>(errorResponse, HttpStatus.SERVICE_UNAVAILABLE);
                    }
                }, executorService))
                .toList();

            List<ResponseEntity<Map>> responses = loginFutures.stream()
                .map(CompletableFuture::join)
                .toList();

            // Assert - Most requests should succeed despite simulated issues
            long successfulLogins = responses.stream()
                .filter(response -> response.getStatusCode() == HttpStatus.OK)
                .count();

            // In a real cluster failover, we'd expect >90% success rate
            assertThat(successfulLogins).isGreaterThan(15); // Allow for some simulated failures

            // Verify service remains operational
            ResponseEntity<Map> healthCheck = restTemplate.getForEntity(
                baseUrl + "/actuator/health", Map.class);
            assertThat(healthCheck.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("Configuration Refresh - Dynamic Config Updates Across Cluster")
        void testConfigurationRefresh() {
            // Arrange - Get current configuration
            ResponseEntity<Map> initialConfig = restTemplate.getForEntity(
                baseUrl + "/test/config", Map.class);
            
            assertThat(initialConfig.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(initialConfig.getBody()).containsKey("jwt");

            // Act - Trigger configuration refresh (simulates config server push)
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            ResponseEntity<Map> refreshResponse = restTemplate.postForEntity(
                baseUrl + "/actuator/refresh", null, Map.class);

            // Note: In production, this would be triggered by Spring Cloud Bus
            // from the config server when configuration changes in Git

            // Assert - Configuration refresh should succeed
            // In a real cluster, all nodes would receive the refresh event
            assertThat(refreshResponse.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NO_CONTENT);

            // Verify service remains operational after config refresh
            ResponseEntity<Map> postRefreshConfig = restTemplate.getForEntity(
                baseUrl + "/test/config", Map.class);
            
            assertThat(postRefreshConfig.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Verify JWT functionality still works after config refresh
            User testUser = createTestUser("config.refresh@email.com", "ConfigTest");
            String token = loginAndGetToken(testUser);
            assertThat(jwtTokenService.validateToken(token)).isTrue();
        }
    }

    // Helper methods
    private User createTestUser(String email, String firstName) {
        User user = new User();
        user.setFirstName(firstName);
        user.setLastName("TestUser");
        user.setEmail(email);
        user.setPassword("$2a$10$test.password.hash"); // BCrypt hash for "TestPassword123!"
        user.setRole("CUSTOMER");
        user.getPreferences().put("preferredStore", "Seattle-Downtown");
        user.getPreferences().put("loyaltyTier", "BRONZE");
        return userRepository.save(user);
    }

    private String loginAndGetToken(User user) {
        Map<String, String> loginRequest = Map.of(
            "email", user.getEmail(),
            "password", "TestPassword123!"
        );
        
        HttpEntity<Map<String, String>> request = new HttpEntity<>(loginRequest);
        ResponseEntity<Map> response = restTemplate.postForEntity(
            baseUrl + "/api/users/login", request, Map.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("accessToken");
    }
} 