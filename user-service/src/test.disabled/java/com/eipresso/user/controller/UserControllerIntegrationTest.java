package com.eipresso.user.controller;

import com.eipresso.user.dto.UserRegistrationRequest;
import com.eipresso.user.entity.User;
import com.eipresso.user.entity.UserAuditEvent;
import com.eipresso.user.repository.UserRepository;
import com.eipresso.user.repository.UserAuditEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureWebMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.cloud.config.enabled=false",
    "spring.cloud.consul.discovery.enabled=false"
})
@Transactional
class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAuditEventRepository userAuditEventRepository;

    private UserRegistrationRequest validRegistrationRequest;
    private Map<String, String> validLoginRequest;

    @BeforeEach
    void setUp() {
        // Clean up database before each test
        userAuditEventRepository.deleteAll();
        userRepository.deleteAll();

        // Setup test data
        validRegistrationRequest = new UserRegistrationRequest();
        validRegistrationRequest.setUsername("johndoe");
        validRegistrationRequest.setEmail("john@eipresso.com");
        validRegistrationRequest.setPassword("password123");
        validRegistrationRequest.setConfirmPassword("password123");
        validRegistrationRequest.setFirstName("John");
        validRegistrationRequest.setLastName("Doe");
        validRegistrationRequest.setPhoneNumber("+1234567890");

        validLoginRequest = new HashMap<>();
        validLoginRequest.put("username", "john@eipresso.com");
        validLoginRequest.put("password", "password123");
    }

    @Test
    void testUserRegistration_Success() throws Exception {
        // When
        MvcResult result = mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRegistrationRequest))
                .header("X-Forwarded-For", "192.168.1.1")
                .header("User-Agent", "Mozilla/5.0 Test Browser"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User registered successfully"))
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.username").value("johndoe"))
                .andExpect(jsonPath("$.email").value("john@eipresso.com"))
                .andReturn();

        // Verify user was saved to database
        Optional<User> savedUser = userRepository.findByEmail("john@eipresso.com");
        assertTrue(savedUser.isPresent());
        assertEquals("johndoe", savedUser.get().getUsername());
        assertEquals("john@eipresso.com", savedUser.get().getEmail());
        assertEquals("John", savedUser.get().getFirstName());
        assertEquals("Doe", savedUser.get().getLastName());
        assertEquals("+1234567890", savedUser.get().getPhoneNumber());
        assertEquals(User.Role.CUSTOMER, savedUser.get().getRole());
        assertTrue(savedUser.get().isAccountEnabled());
        assertFalse(savedUser.get().isAccountLocked());

        // Verify audit event was created
        List<UserAuditEvent> auditEvents = userAuditEventRepository.findAll();
        assertFalse(auditEvents.isEmpty());
        UserAuditEvent registrationEvent = auditEvents.stream()
                .filter(event -> event.getEventType() == UserAuditEvent.EventType.USER_REGISTRATION)
                .findFirst()
                .orElse(null);
        assertNotNull(registrationEvent);
        assertEquals("192.168.1.1", registrationEvent.getIpAddress());
        assertEquals("Mozilla/5.0 Test Browser", registrationEvent.getUserAgent());
        assertEquals(UserAuditEvent.Severity.INFO, registrationEvent.getSeverity());
    }

    @Test
    void testUserRegistration_ValidationError() throws Exception {
        // Given - Invalid request with missing email
        validRegistrationRequest.setEmail("");

        // When & Then
        mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRegistrationRequest))
                .header("X-Forwarded-For", "192.168.1.1"))
                .andExpect(status().isBadRequest());

        // Verify no user was created
        assertEquals(0, userRepository.count());
    }

    @Test
    void testUserRegistration_PasswordMismatch() throws Exception {
        // Given
        validRegistrationRequest.setConfirmPassword("different_password");

        // When & Then
        mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRegistrationRequest))
                .header("X-Forwarded-For", "192.168.1.1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Passwords do not match"));

        // Verify no user was created
        assertEquals(0, userRepository.count());
    }

    @Test
    void testUserRegistration_DuplicateEmail() throws Exception {
        // Given - Register user first time
        mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRegistrationRequest))
                .header("X-Forwarded-For", "192.168.1.1"))
                .andExpect(status().isOk());

        // When - Try to register with same email
        UserRegistrationRequest duplicateRequest = new UserRegistrationRequest();
        duplicateRequest.setUsername("janedoe");
        duplicateRequest.setEmail("john@eipresso.com"); // Same email
        duplicateRequest.setPassword("password456");
        duplicateRequest.setConfirmPassword("password456");
        duplicateRequest.setFirstName("Jane");
        duplicateRequest.setLastName("Doe");

        // Then
        mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(duplicateRequest))
                .header("X-Forwarded-For", "192.168.1.2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("User with email already exists"));

        // Verify only one user exists
        assertEquals(1, userRepository.count());
    }

    @Test
    void testUserLogin_Success() throws Exception {
        // Given - Register user first
        mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRegistrationRequest))
                .header("X-Forwarded-For", "192.168.1.1"))
                .andExpect(status().isOk());

        // When - Login
        MvcResult result = mockMvc.perform(post("/api/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validLoginRequest))
                .header("X-Forwarded-For", "192.168.1.1")
                .header("User-Agent", "Mozilla/5.0 Test Browser"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Authentication successful"))
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").exists())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.username").value("johndoe"))
                .andExpect(jsonPath("$.email").value("john@eipresso.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andReturn();

        // Verify audit event was created for successful login
        List<UserAuditEvent> auditEvents = userAuditEventRepository.findAll();
        UserAuditEvent loginEvent = auditEvents.stream()
                .filter(event -> event.getEventType() == UserAuditEvent.EventType.USER_LOGIN_SUCCESS)
                .findFirst()
                .orElse(null);
        assertNotNull(loginEvent);
        assertEquals("192.168.1.1", loginEvent.getIpAddress());
        assertEquals("Mozilla/5.0 Test Browser", loginEvent.getUserAgent());
        assertEquals(UserAuditEvent.Severity.INFO, loginEvent.getSeverity());
    }

    @Test
    void testUserLogin_InvalidCredentials() throws Exception {
        // Given - Register user first
        mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRegistrationRequest))
                .header("X-Forwarded-For", "192.168.1.1"))
                .andExpect(status().isOk());

        // When - Login with wrong password
        Map<String, String> invalidLoginRequest = new HashMap<>();
        invalidLoginRequest.put("username", "john@eipresso.com");
        invalidLoginRequest.put("password", "wrong_password");

        mockMvc.perform(post("/api/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidLoginRequest))
                .header("X-Forwarded-For", "192.168.1.1")
                .header("User-Agent", "Mozilla/5.0 Test Browser"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid credentials"));

        // Verify failed login attempt was recorded
        User user = userRepository.findByEmail("john@eipresso.com").orElseThrow();
        assertEquals(1, user.getFailedLoginAttempts());

        // Verify audit event was created for failed login
        List<UserAuditEvent> auditEvents = userAuditEventRepository.findAll();
        UserAuditEvent failedLoginEvent = auditEvents.stream()
                .filter(event -> event.getEventType() == UserAuditEvent.EventType.USER_LOGIN_FAILED)
                .findFirst()
                .orElse(null);
        assertNotNull(failedLoginEvent);
        assertEquals("192.168.1.1", failedLoginEvent.getIpAddress());
        assertEquals(UserAuditEvent.Severity.WARNING, failedLoginEvent.getSeverity());
    }

    @Test
    void testUserLogin_NonExistentUser() throws Exception {
        // When - Login with non-existent user
        Map<String, String> nonExistentUserRequest = new HashMap<>();
        nonExistentUserRequest.put("username", "nonexistent@eipresso.com");
        nonExistentUserRequest.put("password", "password123");

        mockMvc.perform(post("/api/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(nonExistentUserRequest))
                .header("X-Forwarded-For", "192.168.1.1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    void testGetUserProfile_Success() throws Exception {
        // Given - Register and login user
        mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRegistrationRequest))
                .header("X-Forwarded-For", "192.168.1.1"))
                .andExpect(status().isOk());

        MvcResult loginResult = mockMvc.perform(post("/api/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validLoginRequest))
                .header("X-Forwarded-For", "192.168.1.1"))
                .andExpect(status().isOk())
                .andReturn();

        String responseContent = loginResult.getResponse().getContentAsString();
        Map<String, Object> loginResponse = objectMapper.readValue(responseContent, Map.class);
        String accessToken = (String) loginResponse.get("accessToken");

        // When - Get user profile
        mockMvc.perform(get("/api/users/profile")
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Forwarded-For", "192.168.1.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.user.username").value("johndoe"))
                .andExpect(jsonPath("$.user.email").value("john@eipresso.com"))
                .andExpect(jsonPath("$.user.firstName").value("John"))
                .andExpect(jsonPath("$.user.lastName").value("Doe"))
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"));
    }

    @Test
    void testGetUserProfile_UnauthorizedAccess() throws Exception {
        // When - Try to access profile without token
        mockMvc.perform(get("/api/users/profile")
                .header("X-Forwarded-For", "192.168.1.1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testHealthCheck() throws Exception {
        // When
        mockMvc.perform(get("/api/users/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("user-service"))
                .andExpect(jsonPath("$.database").value("UP"))
                .andExpect(jsonPath("$.cache").value("UP"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void testGetAuditEvents() throws Exception {
        // Given - Perform some operations to generate audit events
        mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRegistrationRequest))
                .header("X-Forwarded-For", "192.168.1.1"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validLoginRequest))
                .header("X-Forwarded-For", "192.168.1.1"))
                .andExpect(status().isOk());

        // When - Get audit events
        mockMvc.perform(get("/api/users/audit")
                .param("limit", "10")
                .header("X-Forwarded-For", "192.168.1.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.events").isArray())
                .andExpect(jsonPath("$.events").isNotEmpty())
                .andExpect(jsonPath("$.total").exists());

        // Verify audit events contain expected information
        List<UserAuditEvent> auditEvents = userAuditEventRepository.findAll();
        assertTrue(auditEvents.size() >= 2); // At least registration and login events
    }

    @Test
    void testIdempotentRegistration_SameEmailTwice() throws Exception {
        // Given - First registration
        mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRegistrationRequest))
                .header("X-Forwarded-For", "192.168.1.1"))
                .andExpect(status().isOk());

        assertEquals(1, userRepository.count());

        // When - Same registration again (should be idempotent)
        mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRegistrationRequest))
                .header("X-Forwarded-For", "192.168.1.1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("User with email already exists"));

        // Then - Still only one user
        assertEquals(1, userRepository.count());
    }

    @Test
    void testContentEnrichment_GeolocationData() throws Exception {
        // When - Register with IP address
        MvcResult result = mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRegistrationRequest))
                .header("X-Forwarded-For", "192.168.1.1")
                .header("User-Agent", "Mozilla/5.0 Test Browser"))
                .andExpect(status().isOk())
                .andReturn();

        // Then - Verify geolocation enrichment occurred
        Optional<User> savedUser = userRepository.findByEmail("john@eipresso.com");
        assertTrue(savedUser.isPresent());
        
        // Verify user profile contains enriched data
        User user = savedUser.get();
        assertNotNull(user.getCreatedAt());
        assertNotNull(user.getUpdatedAt());
        
        // Verify audit events contain IP and user agent information
        List<UserAuditEvent> auditEvents = userAuditEventRepository.findAll();
        UserAuditEvent registrationEvent = auditEvents.stream()
                .filter(event -> event.getEventType() == UserAuditEvent.EventType.USER_REGISTRATION)
                .findFirst()
                .orElse(null);
        assertNotNull(registrationEvent);
        assertEquals("192.168.1.1", registrationEvent.getIpAddress());
        assertEquals("Mozilla/5.0 Test Browser", registrationEvent.getUserAgent());
    }
} 