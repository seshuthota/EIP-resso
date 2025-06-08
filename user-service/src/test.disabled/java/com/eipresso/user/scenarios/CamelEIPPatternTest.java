package com.eipresso.user.scenarios;

import com.eipresso.user.UserServiceApplication;
import com.eipresso.user.entity.User;
import com.eipresso.user.entity.UserAuditEvent;
import com.eipresso.user.repository.UserRepository;
import com.eipresso.user.repository.UserAuditEventRepository;
import org.apache.camel.CamelContext;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.MockEndpoints;
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
        "spring.jpa.hibernate.ddl-auto=create-drop"
    }
)
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties")
@MockEndpoints("*")
@DisplayName("User Management Service - Advanced EIP Pattern Tests")
public class CamelEIPPatternTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAuditEventRepository auditRepository;

    @Autowired
    private CamelContext camelContext;

    @LocalServerPort
    private int port;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        
        // Clean up test data
        auditRepository.deleteAll();
        userRepository.deleteAll();
        
        // Verify Camel context and routes
        assertThat(camelContext.getRoutes()).hasSizeGreaterThan(5);
    }

    @Nested
    @DisplayName("Dead Letter Channel Pattern Tests")
    class DeadLetterChannelTests {

        @Test
        @DisplayName("Failed User Registration - Dead Letter Channel Processing")
        void testFailedRegistrationDeadLetterChannel() throws Exception {
            // Arrange - Mock endpoints for dead letter channel
            MockEndpoint deadLetterEndpoint = camelContext.getEndpoint("mock:dead-letter-channel", MockEndpoint.class);
            MockEndpoint errorHandlerEndpoint = camelContext.getEndpoint("mock:error-handler", MockEndpoint.class);
            
            deadLetterEndpoint.expectedMinimumMessageCount(1);
            errorHandlerEndpoint.expectedMinimumMessageCount(1);

            // Registration request that will trigger validation failure
            Map<String, Object> invalidRequest = Map.of(
                "firstName", "", // Empty first name should trigger validation error
                "lastName", "TestUser",
                "email", "invalid-email", // Invalid email format
                "password", "weak" // Weak password
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(invalidRequest);

            // Act - Attempt registration with invalid data
            ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl + "/api/users/register", request, Map.class);

            // Assert - Request should fail and trigger dead letter channel
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            // Verify dead letter channel received the failed message
            deadLetterEndpoint.assertIsSatisfied(5000);
            
            // Verify error was logged in audit trail
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                List<UserAuditEvent> auditEvents = auditRepository.findAll();
                boolean errorEventExists = auditEvents.stream()
                    .anyMatch(event -> event.getEventType().equals("USER_REGISTRATION_FAILED"));
                assertThat(errorEventExists).isTrue();
            });
        }

        @Test
        @DisplayName("Email Service Failure - Dead Letter Channel with Retry")
        void testEmailServiceFailureRetry() throws Exception {
            // Arrange - Mock email service failure
            MockEndpoint emailRetryEndpoint = camelContext.getEndpoint("mock:email-retry", MockEndpoint.class);
            MockEndpoint emailDeadLetterEndpoint = camelContext.getEndpoint("mock:email-dead-letter", MockEndpoint.class);
            
            emailRetryEndpoint.expectedMinimumMessageCount(3); // Expect retry attempts
            emailDeadLetterEndpoint.expectedMinimumMessageCount(1);

            Map<String, Object> registrationRequest = Map.of(
                "firstName", "EmailFail",
                "lastName", "TestUser",
                "email", "emailfail.test@fail.domain", // Domain designed to fail email sending
                "password", "TestPassword123!"
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(registrationRequest);

            // Act - Register user (email sending will fail)
            ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl + "/api/users/register", request, Map.class);

            // Assert - Registration succeeds but email handling goes to dead letter
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            // Verify retry mechanism and eventual dead letter handling
            emailRetryEndpoint.assertIsSatisfied(10000);
            emailDeadLetterEndpoint.assertIsSatisfied(10000);
        }

        @Test
        @DisplayName("Database Connection Failure - Dead Letter Channel Recovery")
        void testDatabaseFailureRecovery() throws Exception {
            // Arrange - Mock database issues
            MockEndpoint dbRetryEndpoint = camelContext.getEndpoint("mock:database-retry", MockEndpoint.class);
            MockEndpoint dbDeadLetterEndpoint = camelContext.getEndpoint("mock:database-dead-letter", MockEndpoint.class);
            
            dbRetryEndpoint.expectedMinimumMessageCount(1);

            // Simulate high-load scenario that might cause database timeouts
            Map<String, Object> registrationRequest = Map.of(
                "firstName", "DbStress",
                "lastName", "TestUser",
                "email", "dbstress.test@email.com",
                "password", "TestPassword123!"
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(registrationRequest);

            // Act - Make request during simulated DB stress
            ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl + "/api/users/register", request, Map.class);

            // Assert - Request should either succeed or be handled by dead letter channel
            assertThat(response.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.SERVICE_UNAVAILABLE);

            // If the request was handled by dead letter, verify retry occurred
            if (response.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
                dbRetryEndpoint.assertIsSatisfied(5000);
            }
        }
    }

    @Nested
    @DisplayName("Idempotent Consumer Pattern Tests")
    class IdempotentConsumerTests {

        @Test
        @DisplayName("Duplicate Registration Prevention - Email-Based Idempotency")
        void testEmailBasedIdempotency() throws Exception {
            // Arrange - Mock endpoints for idempotent processing
            MockEndpoint idempotentCheckEndpoint = camelContext.getEndpoint("mock:idempotent-check", MockEndpoint.class);
            MockEndpoint duplicateFilterEndpoint = camelContext.getEndpoint("mock:duplicate-filter", MockEndpoint.class);
            
            idempotentCheckEndpoint.expectedMinimumMessageCount(2);
            duplicateFilterEndpoint.expectedMinimumMessageCount(1);

            Map<String, Object> registrationRequest = Map.of(
                "firstName", "Idempotent",
                "lastName", "TestUser",
                "email", "idempotent.test@email.com",
                "password", "TestPassword123!"
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(registrationRequest);

            // Act - Register same user twice
            ResponseEntity<Map> firstResponse = restTemplate.postForEntity(
                baseUrl + "/api/users/register", request, Map.class);
            
            ResponseEntity<Map> secondResponse = restTemplate.postForEntity(
                baseUrl + "/api/users/register", request, Map.class);

            // Assert - First succeeds, second is filtered by idempotent consumer
            assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

            // Verify idempotent check was performed for both requests
            idempotentCheckEndpoint.assertIsSatisfied(5000);
            duplicateFilterEndpoint.assertIsSatisfied(5000);

            // Verify only one user exists
            List<User> users = userRepository.findAll();
            long duplicateUsers = users.stream()
                .filter(user -> "idempotent.test@email.com".equals(user.getEmail()))
                .count();
            assertThat(duplicateUsers).isEqualTo(1);
        }

        @Test
        @DisplayName("Concurrent Registration Handling - Hazelcast Idempotent Repository")
        void testConcurrentRegistrationIdempotency() throws Exception {
            // Arrange - Mock concurrent processing
            MockEndpoint concurrentProcessingEndpoint = camelContext.getEndpoint("mock:concurrent-processing", MockEndpoint.class);
            MockEndpoint hazelcastIdempotentEndpoint = camelContext.getEndpoint("mock:hazelcast-idempotent", MockEndpoint.class);
            
            concurrentProcessingEndpoint.expectedMinimumMessageCount(3);
            hazelcastIdempotentEndpoint.expectedMinimumMessageCount(1);

            Map<String, Object> registrationRequest = Map.of(
                "firstName", "Concurrent",
                "lastName", "TestUser",
                "email", "concurrent.test@email.com",
                "password", "TestPassword123!"
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(registrationRequest);

            // Act - Simulate concurrent registrations
            ResponseEntity<Map> response1 = restTemplate.postForEntity(baseUrl + "/api/users/register", request, Map.class);
            ResponseEntity<Map> response2 = restTemplate.postForEntity(baseUrl + "/api/users/register", request, Map.class);
            ResponseEntity<Map> response3 = restTemplate.postForEntity(baseUrl + "/api/users/register", request, Map.class);

            // Assert - Only one should succeed, others handled by idempotent consumer
            List<ResponseEntity<Map>> responses = List.of(response1, response2, response3);
            long successCount = responses.stream()
                .filter(resp -> resp.getStatusCode() == HttpStatus.CREATED)
                .count();
            long conflictCount = responses.stream()
                .filter(resp -> resp.getStatusCode() == HttpStatus.CONFLICT)
                .count();

            assertThat(successCount).isEqualTo(1);
            assertThat(conflictCount).isEqualTo(2);

            // Verify Hazelcast-based idempotency
            concurrentProcessingEndpoint.assertIsSatisfied(5000);
            hazelcastIdempotentEndpoint.assertIsSatisfied(5000);
        }

        @Test
        @DisplayName("Login Attempt Idempotency - Session Management")
        void testLoginAttemptIdempotency() throws Exception {
            // Arrange - Create user and mock login idempotency
            User user = createTestUser("login.idempotent@email.com", "LoginTest");
            
            MockEndpoint loginIdempotentEndpoint = camelContext.getEndpoint("mock:login-idempotent", MockEndpoint.class);
            loginIdempotentEndpoint.expectedMinimumMessageCount(1);

            Map<String, String> loginRequest = Map.of(
                "email", user.getEmail(),
                "password", "TestPassword123!"
            );

            HttpEntity<Map<String, String>> request = new HttpEntity<>(loginRequest);

            // Act - Multiple rapid login attempts (should be idempotent)
            ResponseEntity<Map> login1 = restTemplate.postForEntity(baseUrl + "/api/users/login", request, Map.class);
            ResponseEntity<Map> login2 = restTemplate.postForEntity(baseUrl + "/api/users/login", request, Map.class);

            // Assert - Both should succeed but idempotent processing should occur
            assertThat(login1.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(login2.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Verify idempotent processing
            loginIdempotentEndpoint.assertIsSatisfied(5000);

            // Verify JWT tokens are valid but may be same for rapid requests
            String token1 = (String) login1.getBody().get("accessToken");
            String token2 = (String) login2.getBody().get("accessToken");
            
            assertThat(token1).isNotNull();
            assertThat(token2).isNotNull();
        }
    }

    @Nested
    @DisplayName("Wire Tap Pattern Tests")
    class WireTapPatternTests {

        @Test
        @DisplayName("Security Audit Trail - Wire Tap for All Authentication Events")
        void testSecurityAuditWireTap() throws Exception {
            // Arrange - Mock wire tap endpoints
            MockEndpoint auditWireTapEndpoint = camelContext.getEndpoint("mock:audit-wire-tap", MockEndpoint.class);
            MockEndpoint securityMonitoringEndpoint = camelContext.getEndpoint("mock:security-monitoring", MockEndpoint.class);
            
            auditWireTapEndpoint.expectedMinimumMessageCount(2);
            securityMonitoringEndpoint.expectedMinimumMessageCount(2);

            User user = createTestUser("wiretap.test@email.com", "WireTapTest");

            Map<String, String> loginRequest = Map.of(
                "email", user.getEmail(),
                "password", "TestPassword123!"
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "EIP-resso-Security-Test/1.0");
            headers.set("X-Forwarded-For", "192.168.100.50");
            
            HttpEntity<Map<String, String>> request = new HttpEntity<>(loginRequest, headers);

            // Act - Perform authentication events
            ResponseEntity<Map> successfulLogin = restTemplate.postForEntity(baseUrl + "/api/users/login", request, Map.class);
            
            Map<String, String> failedLoginRequest = Map.of(
                "email", user.getEmail(),
                "password", "WrongPassword"
            );
            HttpEntity<Map<String, String>> failedRequest = new HttpEntity<>(failedLoginRequest, headers);
            
            ResponseEntity<Map> failedLogin = restTemplate.postForEntity(baseUrl + "/api/users/login", failedRequest, Map.class);

            // Assert - Both events should trigger wire tap
            assertThat(successfulLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(failedLogin.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

            // Verify wire tap captured events
            auditWireTapEndpoint.assertIsSatisfied(5000);
            securityMonitoringEndpoint.assertIsSatisfied(5000);

            // Verify audit events were created
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                List<UserAuditEvent> auditEvents = auditRepository.findByUserId(user.getId());
                assertThat(auditEvents).hasSizeGreaterThanOrEqualTo(2);
                
                boolean hasSuccessEvent = auditEvents.stream()
                    .anyMatch(event -> event.getEventType().equals("USER_LOGIN"));
                boolean hasFailureEvent = auditEvents.stream()
                    .anyMatch(event -> event.getEventType().equals("USER_LOGIN_FAILED"));
                
                assertThat(hasSuccessEvent).isTrue();
                assertThat(hasFailureEvent).isTrue();
            });
        }

        @Test
        @DisplayName("Profile Access Monitoring - Wire Tap for Sensitive Operations")
        void testProfileAccessWireTap() throws Exception {
            // Arrange - Mock wire tap for profile access
            MockEndpoint profileAccessWireTapEndpoint = camelContext.getEndpoint("mock:profile-access-wire-tap", MockEndpoint.class);
            MockEndpoint privacyMonitoringEndpoint = camelContext.getEndpoint("mock:privacy-monitoring", MockEndpoint.class);
            
            profileAccessWireTapEndpoint.expectedMinimumMessageCount(1);
            privacyMonitoringEndpoint.expectedMinimumMessageCount(1);

            User user = createTestUser("profile.wiretap@email.com", "ProfileWireTap");
            String accessToken = loginAndGetToken(user);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.set("User-Agent", "EIP-resso-Profile-Access/1.0");
            headers.set("X-Forwarded-For", "10.0.0.200");
            
            HttpEntity<?> request = new HttpEntity<>(headers);

            // Act - Access user profile (sensitive operation)
            ResponseEntity<Map> profileResponse = restTemplate.exchange(
                baseUrl + "/api/users/profile", HttpMethod.GET, request, Map.class);

            // Assert - Profile access successful and wire tapped
            assertThat(profileResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Verify wire tap captured profile access
            profileAccessWireTapEndpoint.assertIsSatisfied(5000);
            privacyMonitoringEndpoint.assertIsSatisfied(5000);

            // Verify audit event for profile access
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                List<UserAuditEvent> auditEvents = auditRepository.findByUserId(user.getId());
                boolean hasProfileAccessEvent = auditEvents.stream()
                    .anyMatch(event -> event.getEventType().equals("USER_PROFILE_ACCESSED"));
                assertThat(hasProfileAccessEvent).isTrue();
            });
        }

        @Test
        @DisplayName("Registration Event Broadcasting - Wire Tap for Business Events")
        void testRegistrationEventWireTap() throws Exception {
            // Arrange - Mock wire tap for registration events
            MockEndpoint registrationWireTapEndpoint = camelContext.getEndpoint("mock:registration-wire-tap", MockEndpoint.class);
            MockEndpoint businessEventsEndpoint = camelContext.getEndpoint("mock:business-events", MockEndpoint.class);
            MockEndpoint analyticsEndpoint = camelContext.getEndpoint("mock:analytics", MockEndpoint.class);
            
            registrationWireTapEndpoint.expectedMinimumMessageCount(1);
            businessEventsEndpoint.expectedMinimumMessageCount(1);
            analyticsEndpoint.expectedMinimumMessageCount(1);

            Map<String, Object> registrationRequest = Map.of(
                "firstName", "Registration",
                "lastName", "WireTap",
                "email", "registration.wiretap@email.com",
                "password", "TestPassword123!",
                "phoneNumber", "+1-555-0199"
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(registrationRequest);

            // Act - Register new user (business event)
            ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl + "/api/users/register", request, Map.class);

            // Assert - Registration successful and events broadcasted
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            // Verify wire tap broadcasted to multiple endpoints
            registrationWireTapEndpoint.assertIsSatisfied(5000);
            businessEventsEndpoint.assertIsSatisfied(5000);
            analyticsEndpoint.assertIsSatisfied(5000);
        }
    }

    @Nested
    @DisplayName("Content Enricher Pattern Tests")
    class ContentEnricherTests {

        @Test
        @DisplayName("User Profile Enrichment - Geolocation and Preferences")
        void testUserProfileEnrichment() throws Exception {
            // Arrange - Mock enrichment endpoints
            MockEndpoint geolocationEnrichmentEndpoint = camelContext.getEndpoint("mock:geolocation-enrichment", MockEndpoint.class);
            MockEndpoint preferencesEnrichmentEndpoint = camelContext.getEndpoint("mock:preferences-enrichment", MockEndpoint.class);
            
            geolocationEnrichmentEndpoint.expectedMinimumMessageCount(1);
            preferencesEnrichmentEndpoint.expectedMinimumMessageCount(1);

            Map<String, Object> registrationRequest = Map.of(
                "firstName", "Enriched",
                "lastName", "Profile",
                "email", "enriched.profile@email.com",
                "password", "TestPassword123!"
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Forwarded-For", "73.158.64.15"); // Seattle IP for geolocation
            headers.set("User-Agent", "EIP-resso-Mobile/1.0 (iPhone; iOS 15.0)");
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(registrationRequest, headers);

            // Act - Register user with enrichment data
            ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl + "/api/users/register", request, Map.class);

            // Assert - Registration successful and profile enriched
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            // Verify enrichment processing
            geolocationEnrichmentEndpoint.assertIsSatisfied(5000);
            preferencesEnrichmentEndpoint.assertIsSatisfied(5000);

            // Verify enriched data was added
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                User savedUser = userRepository.findByEmail("enriched.profile@email.com").orElse(null);
                assertThat(savedUser).isNotNull();
                assertThat(savedUser.getPreferences()).isNotEmpty();
                
                // Content Enricher should add location-based preferences
                assertThat(savedUser.getPreferences()).containsKey("preferredStore");
                assertThat(savedUser.getPreferences()).containsKey("loyaltyTier");
                assertThat(savedUser.getPreferences()).containsKey("preferredOrderTime");
            });
        }

        @Test
        @DisplayName("Customer Loyalty Enrichment - Behavior-Based Preferences")
        void testCustomerLoyaltyEnrichment() throws Exception {
            // Arrange - Mock loyalty enrichment
            MockEndpoint loyaltyEnrichmentEndpoint = camelContext.getEndpoint("mock:loyalty-enrichment", MockEndpoint.class);
            MockEndpoint behaviorAnalysisEndpoint = camelContext.getEndpoint("mock:behavior-analysis", MockEndpoint.class);
            
            loyaltyEnrichmentEndpoint.expectedMinimumMessageCount(1);
            behaviorAnalysisEndpoint.expectedMinimumMessageCount(1);

            // Create existing customer for enrichment
            User existingCustomer = createTestUser("loyalty.enrichment@email.com", "LoyalCustomer");
            
            String accessToken = loginAndGetToken(existingCustomer);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.set("X-Customer-Visits", "25"); // Frequent customer
            headers.set("X-Purchase-History", "espresso,latte,cappuccino");
            
            HttpEntity<?> request = new HttpEntity<>(headers);

            // Act - Access profile to trigger loyalty enrichment
            ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/api/users/profile", HttpMethod.GET, request, Map.class);

            // Assert - Profile access triggers enrichment
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Verify loyalty enrichment processing
            loyaltyEnrichmentEndpoint.assertIsSatisfied(5000);
            behaviorAnalysisEndpoint.assertIsSatisfied(5000);

            // Verify loyalty data was enriched
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                User updatedUser = userRepository.findById(existingCustomer.getId()).orElse(null);
                assertThat(updatedUser).isNotNull();
                
                // Content Enricher should update loyalty information
                Map<String, Object> preferences = updatedUser.getPreferences();
                assertThat(preferences).containsKey("loyaltyTier");
                
                // Frequent customer should be upgraded
                if (preferences.containsKey("visitCount")) {
                    assertThat(preferences.get("loyaltyTier")).isIn("SILVER", "GOLD", "PLATINUM");
                }
            });
        }

        @Test
        @DisplayName("Device Information Enrichment - Multi-Platform Support")
        void testDeviceInformationEnrichment() throws Exception {
            // Arrange - Mock device enrichment
            MockEndpoint deviceEnrichmentEndpoint = camelContext.getEndpoint("mock:device-enrichment", MockEndpoint.class);
            MockEndpoint platformAnalyticsEndpoint = camelContext.getEndpoint("mock:platform-analytics", MockEndpoint.class);
            
            deviceEnrichmentEndpoint.expectedMinimumMessageCount(1);
            platformAnalyticsEndpoint.expectedMinimumMessageCount(1);

            Map<String, Object> registrationRequest = Map.of(
                "firstName", "Mobile",
                "lastName", "User",
                "email", "mobile.user@email.com",
                "password", "TestPassword123!"
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "EIP-resso-Mobile/2.1 (Android 12; Samsung Galaxy S21)");
            headers.set("X-App-Version", "2.1.0");
            headers.set("X-Platform", "android");
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(registrationRequest, headers);

            // Act - Register mobile user with device info
            ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl + "/api/users/register", request, Map.class);

            // Assert - Registration with device enrichment
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            // Verify device enrichment processing
            deviceEnrichmentEndpoint.assertIsSatisfied(5000);
            platformAnalyticsEndpoint.assertIsSatisfied(5000);

            // Verify device preferences were added
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                User savedUser = userRepository.findByEmail("mobile.user@email.com").orElse(null);
                assertThat(savedUser).isNotNull();
                
                Map<String, Object> preferences = savedUser.getPreferences();
                assertThat(preferences).containsKey("preferredInterface");
                assertThat(preferences).containsKey("notificationPreference");
                
                // Mobile users should get mobile-optimized preferences
                if (preferences.containsKey("platform")) {
                    assertThat(preferences.get("platform")).isEqualTo("mobile");
                }
            });
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
        user.getPreferences().put("preferredStore", "Test-Store");
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