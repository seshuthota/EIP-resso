package com.eipresso.user.scenarios;

import com.eipresso.user.UserServiceApplication;
import com.eipresso.user.entity.User;
import com.eipresso.user.entity.UserAuditEvent;
import com.eipresso.user.repository.UserRepository;
import com.eipresso.user.repository.UserAuditEventRepository;
import com.eipresso.user.service.JwtTokenService;
import org.apache.camel.CamelContext;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
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
        "spring.jpa.hibernate.ddl-auto=create-drop"
    }
)
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties")
@DisplayName("User Management Service - Coffee Shop Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UserServiceIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAuditEventRepository auditRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private CamelContext camelContext;

    @LocalServerPort
    private int port;

    private String baseUrl;
    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        executorService = Executors.newFixedThreadPool(10);
        
        // Clean up test data
        auditRepository.deleteAll();
        userRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    @Nested
    @DisplayName("Integration Scenario 1: Coffee Shop Opening Day")
    class CoffeeShopOpeningDayScenarios {

        @Test
        @Order(1)
        @DisplayName("Staff Setup - Manager and Baristas Registration")
        void testStaffSetupRegistration() {
            // Arrange - Opening day staff registration
            List<Map<String, Object>> staffRegistrations = List.of(
                Map.of(
                    "firstName", "Sarah",
                    "lastName", "Manager",
                    "email", "sarah.manager@eipresso.com",
                    "password", "ManagerSecure123!",
                    "role", "MANAGER",
                    "phoneNumber", "+1-555-0001"
                ),
                Map.of(
                    "firstName", "Mike",
                    "lastName", "Barista",
                    "email", "mike.barista@eipresso.com", 
                    "password", "BaristaSecure123!",
                    "role", "BARISTA",
                    "phoneNumber", "+1-555-0002"
                ),
                Map.of(
                    "firstName", "Elena",
                    "lastName", "Barista",
                    "email", "elena.barista@eipresso.com",
                    "password", "BaristaSecure123!",
                    "role", "BARISTA", 
                    "phoneNumber", "+1-555-0003"
                )
            );

            // Act - Register all staff members
            List<ResponseEntity<Map>> responses = staffRegistrations.stream()
                .map(registration -> {
                    HttpEntity<Map<String, Object>> request = new HttpEntity<>(registration);
                    return restTemplate.postForEntity(baseUrl + "/api/users/register", request, Map.class);
                })
                .toList();

            // Assert - All staff registered successfully
            responses.forEach(response -> {
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                assertThat(response.getBody()).containsKey("userId");
            });

            // Verify staff in database
            List<User> staff = userRepository.findAll();
            assertThat(staff).hasSize(3);

            long managers = staff.stream().filter(user -> "MANAGER".equals(user.getRole())).count();
            long baristas = staff.stream().filter(user -> "BARISTA".equals(user.getRole())).count();
            
            assertThat(managers).isEqualTo(1);
            assertThat(baristas).isEqualTo(2);

            // Verify audit trail for all staff
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                List<UserAuditEvent> auditEvents = auditRepository.findAll();
                long registrationEvents = auditEvents.stream()
                    .filter(event -> event.getEventType().equals("USER_REGISTERED"))
                    .count();
                assertThat(registrationEvents).isEqualTo(3);
            });
        }

        @Test
        @Order(2)
        @DisplayName("First Customer Wave - Early Morning Coffee Rush")
        void testFirstCustomerWave() {
            // Arrange - First 20 customers of opening day
            List<Map<String, Object>> customerRegistrations = IntStream.range(1, 21)
                .mapToObj(i -> Map.of(
                    "firstName", "Customer",
                    "lastName", "Day" + i,
                    "email", "customer.day" + i + "@email.com",
                    "password", "CustomerPass123!",
                    "phoneNumber", "+1-555-" + String.format("%04d", 1000 + i)
                ))
                .toList();

            // Act - Register customers concurrently (morning rush simulation)
            List<CompletableFuture<ResponseEntity<Map>>> futures = customerRegistrations.stream()
                .map(registration -> CompletableFuture.supplyAsync(() -> {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.set("X-Forwarded-For", "192.168.1." + (100 + (int)(Math.random() * 50)));
                    headers.set("User-Agent", "EIP-resso-Mobile/1.0");
                    
                    HttpEntity<Map<String, Object>> request = new HttpEntity<>(registration, headers);
                    return restTemplate.postForEntity(baseUrl + "/api/users/register", request, Map.class);
                }, executorService))
                .toList();

            List<ResponseEntity<Map>> responses = futures.stream()
                .map(CompletableFuture::join)
                .toList();

            // Assert - All customers registered successfully
            long successfulRegistrations = responses.stream()
                .filter(response -> response.getStatusCode() == HttpStatus.CREATED)
                .count();

            assertThat(successfulRegistrations).isEqualTo(20);

            // Verify customer count in database
            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
                List<User> allUsers = userRepository.findAll();
                long customers = allUsers.stream()
                    .filter(user -> "CUSTOMER".equals(user.getRole()))
                    .count();
                assertThat(customers).isEqualTo(20);
            });
        }

        @Test
        @Order(3)
        @DisplayName("Service Health Check - System Status Verification")
        void testServiceHealthCheck() {
            // Act - Check service health and readiness
            ResponseEntity<Map> healthResponse = restTemplate.getForEntity(
                baseUrl + "/actuator/health", Map.class);

            ResponseEntity<Map> infoResponse = restTemplate.getForEntity(
                baseUrl + "/actuator/info", Map.class);

            // Assert - Service is healthy and ready
            assertThat(healthResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(healthResponse.getBody().get("status")).isEqualTo("UP");

            assertThat(infoResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Verify Camel routes are active
            assertThat(camelContext.getRoutes()).hasSizeGreaterThan(5);
            assertThat(camelContext.getStatus().isStarted()).isTrue();

            // Verify database connectivity
            long userCount = userRepository.count();
            assertThat(userCount).isGreaterThan(0); // Should have users from previous tests
        }
    }

    @Nested
    @DisplayName("Integration Scenario 2: Peak Hours Operations")
    class PeakHoursOperationsScenarios {

        @Test
        @DisplayName("Rush Hour Authentication - 100 Concurrent Customer Logins")
        void testRushHourAuthentication() {
            // Arrange - Pre-register customers for rush hour
            List<User> customers = IntStream.range(1, 101)
                .mapToObj(i -> {
                    User customer = new User();
                    customer.setFirstName("Rush");
                    customer.setLastName("Customer" + i);
                    customer.setEmail("rush.customer" + i + "@email.com");
                    customer.setPassword("$2a$10$test.password.hash");
                    customer.setRole("CUSTOMER");
                    return customer;
                })
                .toList();

            userRepository.saveAll(customers);

            // Act - Simulate rush hour concurrent logins
            List<CompletableFuture<ResponseEntity<Map>>> loginFutures = customers.stream()
                .map(customer -> CompletableFuture.supplyAsync(() -> {
                    Map<String, String> loginRequest = Map.of(
                        "email", customer.getEmail(),
                        "password", "TestPassword123!"
                    );

                    HttpHeaders headers = new HttpHeaders();
                    headers.set("User-Agent", "EIP-resso-POS/1.0");
                    headers.set("X-Terminal-ID", "POS-" + String.format("%03d", (int)(Math.random() * 10) + 1));
                    
                    HttpEntity<Map<String, String>> request = new HttpEntity<>(loginRequest, headers);
                    return restTemplate.postForEntity(baseUrl + "/api/users/login", request, Map.class);
                }, executorService))
                .toList();

            List<ResponseEntity<Map>> responses = loginFutures.stream()
                .map(CompletableFuture::join)
                .toList();

            // Assert - High success rate during peak load
            long successfulLogins = responses.stream()
                .filter(response -> response.getStatusCode() == HttpStatus.OK)
                .count();

            assertThat(successfulLogins).isGreaterThan(95); // >95% success rate expected

            // Verify JWT tokens are valid
            responses.stream()
                .filter(response -> response.getStatusCode() == HttpStatus.OK)
                .limit(20) // Check sample of tokens
                .forEach(response -> {
                    String accessToken = (String) response.getBody().get("accessToken");
                    assertThat(jwtTokenService.validateToken(accessToken)).isTrue();
                });

            // Verify audit trail captures rush hour activity
            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
                List<UserAuditEvent> auditEvents = auditRepository.findAll();
                long loginEvents = auditEvents.stream()
                    .filter(event -> event.getEventType().equals("USER_LOGIN"))
                    .count();
                assertThat(loginEvents).isGreaterThan(90);
            });
        }

        @Test
        @DisplayName("Staff Shift Change - Authentication Handover")
        void testStaffShiftChange() {
            // Arrange - Morning and afternoon shift staff
            List<User> morningStaff = List.of(
                createStaffUser("morning.manager@eipresso.com", "Morning", "Manager", "MANAGER"),
                createStaffUser("morning.barista1@eipresso.com", "Alice", "Barista", "BARISTA"),
                createStaffUser("morning.barista2@eipresso.com", "Bob", "Barista", "BARISTA")
            );

            List<User> afternoonStaff = List.of(
                createStaffUser("afternoon.manager@eipresso.com", "Afternoon", "Manager", "MANAGER"),
                createStaffUser("afternoon.barista1@eipresso.com", "Carol", "Barista", "BARISTA"),
                createStaffUser("afternoon.barista2@eipresso.com", "David", "Barista", "BARISTA")
            );

            // Act - Morning staff login
            List<String> morningTokens = morningStaff.stream()
                .map(this::authenticateStaff)
                .toList();

            // Simulate shift change - afternoon staff login
            List<String> afternoonTokens = afternoonStaff.stream()
                .map(this::authenticateStaff)
                .toList();

            // Assert - All staff authenticated successfully
            assertThat(morningTokens).hasSize(3);
            assertThat(afternoonTokens).hasSize(3);

            morningTokens.forEach(token -> assertThat(jwtTokenService.validateToken(token)).isTrue());
            afternoonTokens.forEach(token -> assertThat(jwtTokenService.validateToken(token)).isTrue());

            // Verify role-based access in tokens
            String managerToken = afternoonTokens.get(0); // First is manager
            Map<String, Object> managerClaims = jwtTokenService.extractAllClaims(managerToken);
            assertThat(managerClaims.get("role")).isEqualTo("MANAGER");

            String baristaToken = afternoonTokens.get(1); // Second is barista
            Map<String, Object> baristaClaims = jwtTokenService.extractAllClaims(baristaToken);
            assertThat(baristaClaims.get("role")).isEqualTo("BARISTA");
        }

        @Test
        @DisplayName("Customer Loyalty Program - Frequent Customer Identification")
        void testCustomerLoyaltyProgram() {
            // Arrange - Loyal customers with different tiers
            List<User> loyalCustomers = List.of(
                createLoyalCustomer("bronze.customer@email.com", "Bronze", "Customer", "BRONZE", 5),
                createLoyalCustomer("silver.customer@email.com", "Silver", "Customer", "SILVER", 15),
                createLoyalCustomer("gold.customer@email.com", "Gold", "Customer", "GOLD", 30),
                createLoyalCustomer("platinum.customer@email.com", "Platinum", "Customer", "PLATINUM", 100)
            );

            // Act - Authenticate loyal customers and check their profiles
            List<Map<String, Object>> customerProfiles = loyalCustomers.stream()
                .map(customer -> {
                    String token = authenticateCustomer(customer);
                    return getCustomerProfile(token);
                })
                .toList();

            // Assert - Verify loyalty tier information in profiles
            assertThat(customerProfiles).hasSize(4);

            customerProfiles.forEach(profile -> {
                assertThat(profile).containsKey("preferences");
                @SuppressWarnings("unchecked")
                Map<String, Object> preferences = (Map<String, Object>) profile.get("preferences");
                assertThat(preferences).containsKey("loyaltyTier");
                assertThat(preferences.get("loyaltyTier")).isIn("BRONZE", "SILVER", "GOLD", "PLATINUM");
            });

            // Verify platinum customer has premium preferences
            Map<String, Object> platinumProfile = customerProfiles.get(3);
            @SuppressWarnings("unchecked")
            Map<String, Object> platinumPrefs = (Map<String, Object>) platinumProfile.get("preferences");
            assertThat(platinumPrefs.get("loyaltyTier")).isEqualTo("PLATINUM");
        }
    }

    @Nested
    @DisplayName("Integration Scenario 3: Security & Compliance Operations")
    class SecurityComplianceOperationsScenarios {

        @Test
        @DisplayName("Security Incident Response - Suspicious Activity Detection")
        void testSecurityIncidentResponse() {
            // Arrange - Create target user for security testing
            User targetUser = createTestUser("security.target@email.com", "Security", "Target");

            // Act - Simulate suspicious activity pattern
            // 1. Multiple failed login attempts from different IPs
            List<String> suspiciousIPs = List.of("192.168.1.100", "10.0.0.50", "172.16.0.25");
            
            suspiciousIPs.forEach(ip -> {
                for (int i = 0; i < 3; i++) {
                    attemptLogin(targetUser.getEmail(), "WrongPassword" + i, ip, "Suspicious-Bot/1.0");
                }
            });

            // 2. Successful login from known IP
            String validToken = attemptLogin(targetUser.getEmail(), "TestPassword123!", 
                "192.168.1.200", "EIP-resso-Mobile/1.0");

            // Assert - Security events captured and valid login succeeded
            assertThat(validToken).isNotNull();

            // Verify comprehensive audit trail
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                List<UserAuditEvent> securityEvents = auditRepository.findByUserId(targetUser.getId());
                
                long failedAttempts = securityEvents.stream()
                    .filter(event -> event.getEventType().equals("USER_LOGIN_FAILED"))
                    .count();
                
                long successfulLogins = securityEvents.stream()
                    .filter(event -> event.getEventType().equals("USER_LOGIN"))
                    .count();

                assertThat(failedAttempts).isEqualTo(9); // 3 IPs × 3 attempts each
                assertThat(successfulLogins).isEqualTo(1);

                // Verify IP addresses are captured
                List<String> capturedIPs = securityEvents.stream()
                    .map(UserAuditEvent::getIpAddress)
                    .distinct()
                    .toList();
                
                assertThat(capturedIPs).hasSize(4); // 3 suspicious + 1 valid
            });
        }

        @Test
        @DisplayName("Data Privacy Compliance - User Data Access Control")
        void testDataPrivacyCompliance() {
            // Arrange - Create users with different access levels
            User regularCustomer = createTestUser("regular.customer@email.com", "Regular", "Customer");
            User premiumCustomer = createTestUser("premium.customer@email.com", "Premium", "Customer");
            User staffMember = createStaffUser("staff.member@eipresso.com", "Staff", "Member", "BARISTA");

            // Act - Test access control for different user types
            String customerToken = authenticateCustomer(regularCustomer);
            String premiumToken = authenticateCustomer(premiumCustomer);  
            String staffToken = authenticateStaff(staffMember);

            // Regular customer accessing own profile
            Map<String, Object> customerProfile = getCustomerProfile(customerToken);
            
            // Staff member accessing profile (should work)
            Map<String, Object> staffProfile = getCustomerProfile(staffToken);

            // Assert - Appropriate access control
            assertThat(customerProfile).containsKey("email");
            assertThat(customerProfile).containsKey("firstName");
            assertThat(customerProfile).doesNotContainKey("password"); // Sensitive data excluded

            assertThat(staffProfile).containsKey("email");
            assertThat(staffProfile.get("role")).isEqualTo("BARISTA");

            // Verify audit events track data access
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                List<UserAuditEvent> accessEvents = auditRepository.findAll();
                long profileAccessEvents = accessEvents.stream()
                    .filter(event -> event.getEventType().equals("USER_PROFILE_ACCESSED"))
                    .count();
                
                assertThat(profileAccessEvents).isGreaterThanOrEqualTo(2);
            });
        }

        @Test
        @DisplayName("Token Security - JWT Lifecycle and Validation")
        void testTokenSecurity() {
            // Arrange - Create test user
            User testUser = createTestUser("token.security@email.com", "Token", "Security");

            // Act - Generate and validate tokens
            String accessToken = authenticateCustomer(testUser);
            
            // Test token validation
            boolean isValid = jwtTokenService.validateToken(accessToken);
            Map<String, Object> claims = jwtTokenService.extractAllClaims(accessToken);

            // Test token expiration checking
            boolean isExpired = jwtTokenService.isTokenExpired(accessToken);

            // Assert - Token security properties
            assertThat(isValid).isTrue();
            assertThat(isExpired).isFalse();
            
            assertThat(claims).containsKey("sub"); // Subject (user ID)
            assertThat(claims).containsKey("email");
            assertThat(claims).containsKey("role");
            assertThat(claims).containsKey("exp"); // Expiration
            assertThat(claims).containsKey("iat"); // Issued at

            // Verify user information in token
            assertThat(claims.get("email")).isEqualTo(testUser.getEmail());
            assertThat(claims.get("sub")).isEqualTo(testUser.getId().toString());

            // Test token usage for protected endpoint
            Map<String, Object> protectedProfile = getCustomerProfile(accessToken);
            assertThat(protectedProfile.get("email")).isEqualTo(testUser.getEmail());
        }
    }

    @Nested
    @DisplayName("Integration Scenario 4: System Integration & Performance")
    class SystemIntegrationPerformanceScenarios {

        @Test
        @DisplayName("Database Integration - High Volume User Operations")
        void testDatabaseIntegration() {
            // Arrange - High volume test data
            int userCount = 500;
            
            // Act - Create high volume of users
            List<CompletableFuture<User>> userCreationFutures = IntStream.range(1, userCount + 1)
                .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                    User user = new User();
                    user.setFirstName("Volume");
                    user.setLastName("User" + i);
                    user.setEmail("volume.user" + i + "@email.com");
                    user.setPassword("$2a$10$test.password.hash");
                    user.setRole("CUSTOMER");
                    user.getPreferences().put("batchNumber", i);
                    return userRepository.save(user);
                }, executorService))
                .toList();

            List<User> createdUsers = userCreationFutures.stream()
                .map(CompletableFuture::join)
                .toList();

            // Assert - All users created successfully
            assertThat(createdUsers).hasSize(userCount);

            // Verify database performance
            long startTime = System.currentTimeMillis();
            List<User> allUsers = userRepository.findAll();
            long queryTime = System.currentTimeMillis() - startTime;

            assertThat(allUsers.size()).isGreaterThanOrEqualTo(userCount);
            assertThat(queryTime).isLessThan(5000); // Query should complete within 5 seconds

            // Test database indexing performance
            startTime = System.currentTimeMillis();
            User foundUser = userRepository.findByEmail("volume.user250@email.com").orElse(null);
            long indexQueryTime = System.currentTimeMillis() - startTime;

            assertThat(foundUser).isNotNull();
            assertThat(indexQueryTime).isLessThan(100); // Indexed query should be fast
        }

        @Test
        @DisplayName("Camel Routes Integration - Message Processing Performance")
        void testCamelRoutesIntegration() {
            // Arrange - Verify all Camel routes are active
            assertThat(camelContext.getRoutes()).hasSizeGreaterThan(5);
            assertThat(camelContext.getStatus().isStarted()).isTrue();

            // Act - Process high volume of authentication events
            List<User> testUsers = IntStream.range(1, 101)
                .mapToObj(i -> createTestUser("camel.user" + i + "@email.com", "Camel", "User" + i))
                .toList();

            long startTime = System.currentTimeMillis();
            
            List<String> tokens = testUsers.parallelStream()
                .map(this::authenticateCustomer)
                .toList();
            
            long processingTime = System.currentTimeMillis() - startTime;

            // Assert - High throughput message processing
            assertThat(tokens).hasSize(100);
            assertThat(processingTime).isLessThan(30000); // Should process 100 users in <30 seconds

            tokens.forEach(token -> assertThat(jwtTokenService.validateToken(token)).isTrue());

            // Verify Camel route metrics
            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
                List<UserAuditEvent> auditEvents = auditRepository.findAll();
                long loginEvents = auditEvents.stream()
                    .filter(event -> event.getEventType().equals("USER_LOGIN"))
                    .count();
                
                assertThat(loginEvents).isGreaterThanOrEqualTo(100);
            });
        }

        @Test
        @DisplayName("Configuration Management Integration - Dynamic Config Refresh")
        void testConfigurationManagementIntegration() {
            // Act - Test configuration endpoints
            ResponseEntity<Map> configResponse = restTemplate.getForEntity(
                baseUrl + "/test/config", Map.class);

            ResponseEntity<Map> healthResponse = restTemplate.getForEntity(
                baseUrl + "/actuator/health", Map.class);

            // Assert - Configuration management working
            assertThat(configResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(configResponse.getBody()).containsKey("jwt");

            assertThat(healthResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(healthResponse.getBody().get("status")).isEqualTo("UP");

            // Test configuration refresh capability
            ResponseEntity<String> refreshResponse = restTemplate.postForEntity(
                baseUrl + "/actuator/refresh", null, String.class);

            // Configuration refresh should succeed or return no content
            assertThat(refreshResponse.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NO_CONTENT);

            // Verify service still operational after refresh
            User testUser = createTestUser("config.test@email.com", "Config", "Test");
            String token = authenticateCustomer(testUser);
            assertThat(jwtTokenService.validateToken(token)).isTrue();
        }
    }

    // Helper methods
    private User createTestUser(String email, String firstName, String lastName) {
        User user = new User();
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setPassword("$2a$10$test.password.hash");
        user.setRole("CUSTOMER");
        user.getPreferences().put("preferredStore", "Test-Store");
        user.getPreferences().put("loyaltyTier", "BRONZE");
        return userRepository.save(user);
    }

    private User createStaffUser(String email, String firstName, String lastName, String role) {
        User user = new User();
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setPassword("$2a$10$test.password.hash");
        user.setRole(role);
        user.getPreferences().put("workShift", "morning");
        user.getPreferences().put("department", "operations");
        return userRepository.save(user);
    }

    private User createLoyalCustomer(String email, String firstName, String lastName, 
                                   String loyaltyTier, int visitCount) {
        User user = new User();
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setPassword("$2a$10$test.password.hash");
        user.setRole("CUSTOMER");
        user.getPreferences().put("loyaltyTier", loyaltyTier);
        user.getPreferences().put("visitCount", visitCount);
        user.getPreferences().put("joinDate", LocalDateTime.now().minusMonths(visitCount / 2).toString());
        return userRepository.save(user);
    }

    private String authenticateCustomer(User user) {
        return authenticate(user.getEmail(), "TestPassword123!");
    }

    private String authenticateStaff(User user) {
        return authenticate(user.getEmail(), "TestPassword123!");
    }

    private String authenticate(String email, String password) {
        Map<String, String> loginRequest = Map.of("email", email, "password", password);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(loginRequest);
        
        ResponseEntity<Map> response = restTemplate.postForEntity(
            baseUrl + "/api/users/login", request, Map.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("accessToken");
    }

    private String attemptLogin(String email, String password, String ip, String userAgent) {
        Map<String, String> loginRequest = Map.of("email", email, "password", password);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Forwarded-For", ip);
        headers.set("User-Agent", userAgent);
        
        HttpEntity<Map<String, String>> request = new HttpEntity<>(loginRequest, headers);
        
        ResponseEntity<Map> response = restTemplate.postForEntity(
            baseUrl + "/api/users/login", request, Map.class);
        
        if (response.getStatusCode() == HttpStatus.OK) {
            return (String) response.getBody().get("accessToken");
        }
        return null;
    }

    private Map<String, Object> getCustomerProfile(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<?> request = new HttpEntity<>(headers);
        
        ResponseEntity<Map> response = restTemplate.exchange(
            baseUrl + "/api/users/profile", HttpMethod.GET, request, Map.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }
} 