package com.eipresso.user.service;

import com.eipresso.user.dto.UserRegistrationRequest;
import com.eipresso.user.entity.User;
import com.eipresso.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private UserService userService;

    private UserRegistrationRequest validRegistrationRequest;
    private User savedUser;

    @BeforeEach
    void setUp() {
        validRegistrationRequest = new UserRegistrationRequest();
        validRegistrationRequest.setUsername("johndoe");
        validRegistrationRequest.setEmail("john@eipresso.com");
        validRegistrationRequest.setPassword("password123");
        validRegistrationRequest.setConfirmPassword("password123");
        validRegistrationRequest.setFirstName("John");
        validRegistrationRequest.setLastName("Doe");

        savedUser = new User();
        savedUser.setId(1L);
        savedUser.setUsername("johndoe");
        savedUser.setEmail("john@eipresso.com");
        savedUser.setPassword("encoded_password");
        savedUser.setRole(User.Role.CUSTOMER);
        savedUser.setAccountEnabled(true);
    }

    @Test
    void testProcessUserRegistration_Success() {
        // Given
        when(userRepository.existsByEmail(validRegistrationRequest.getEmail())).thenReturn(false);
        when(userRepository.existsByUsername(validRegistrationRequest.getUsername())).thenReturn(false);
        when(passwordEncoder.encode(validRegistrationRequest.getPassword())).thenReturn("encoded_password");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // When
        Map<String, Object> result = userService.processUserRegistration(validRegistrationRequest);

        // Then
        assertNotNull(result);
        assertTrue((Boolean) result.get("success"));
        assertEquals("User registered successfully", result.get("message"));
        assertEquals(1L, result.get("userId"));
        assertEquals("johndoe", result.get("username"));
        assertEquals("john@eipresso.com", result.get("email"));

        verify(userRepository).existsByEmail(validRegistrationRequest.getEmail());
        verify(userRepository).existsByUsername(validRegistrationRequest.getUsername());
        verify(passwordEncoder).encode(validRegistrationRequest.getPassword());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void testProcessUserRegistration_PasswordMismatch() {
        // Given
        validRegistrationRequest.setConfirmPassword("different_password");

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            userService.processUserRegistration(validRegistrationRequest);
        });

        assertTrue(exception.getMessage().contains("Passwords do not match"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testProcessUserRegistration_EmailAlreadyExists() {
        // Given
        when(userRepository.existsByEmail(validRegistrationRequest.getEmail())).thenReturn(true);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            userService.processUserRegistration(validRegistrationRequest);
        });

        assertTrue(exception.getMessage().contains("User with email already exists"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testProcessUserRegistration_UsernameAlreadyExists() {
        // Given
        when(userRepository.existsByEmail(validRegistrationRequest.getEmail())).thenReturn(false);
        when(userRepository.existsByUsername(validRegistrationRequest.getUsername())).thenReturn(true);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            userService.processUserRegistration(validRegistrationRequest);
        });

        assertTrue(exception.getMessage().contains("Username already taken"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testProcessUserAuthentication_Success() {
        // Given
        Map<String, String> credentials = new HashMap<>();
        credentials.put("username", "john@eipresso.com");
        credentials.put("password", "password123");

        when(userRepository.findByUsernameOrEmail("john@eipresso.com", "john@eipresso.com"))
                .thenReturn(Optional.of(savedUser));
        when(passwordEncoder.matches("password123", savedUser.getPassword())).thenReturn(true);
        when(jwtTokenService.generateToken(savedUser)).thenReturn("access_token");
        when(jwtTokenService.generateRefreshToken(savedUser)).thenReturn("refresh_token");
        when(jwtTokenService.getTokenExpirationTime()).thenReturn(900L);

        // When
        Map<String, Object> result = userService.processUserAuthentication(credentials);

        // Then
        assertNotNull(result);
        assertTrue((Boolean) result.get("success"));
        assertEquals("Authentication successful", result.get("message"));
        assertEquals(1L, result.get("userId"));
        assertEquals("johndoe", result.get("username"));
        assertEquals("john@eipresso.com", result.get("email"));
        assertEquals("CUSTOMER", result.get("role"));
        assertEquals("access_token", result.get("accessToken"));
        assertEquals("refresh_token", result.get("refreshToken"));
        assertEquals("Bearer", result.get("tokenType"));
        assertEquals(900L, result.get("expiresIn"));

        verify(userRepository).findByUsernameOrEmail("john@eipresso.com", "john@eipresso.com");
        verify(passwordEncoder).matches("password123", savedUser.getPassword());
        verify(jwtTokenService).generateToken(savedUser);
        verify(jwtTokenService).generateRefreshToken(savedUser);
    }

    @Test
    void testProcessUserAuthentication_InvalidCredentials() {
        // Given
        Map<String, String> credentials = new HashMap<>();
        credentials.put("username", "john@eipresso.com");
        credentials.put("password", "wrong_password");

        when(userRepository.findByUsernameOrEmail("john@eipresso.com", "john@eipresso.com"))
                .thenReturn(Optional.of(savedUser));
        when(passwordEncoder.matches("wrong_password", savedUser.getPassword())).thenReturn(false);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            userService.processUserAuthentication(credentials);
        });

        assertTrue(exception.getMessage().contains("Invalid credentials"));
        verify(userRepository).save(savedUser); // Failed attempt should be recorded
    }

    @Test
    void testProcessUserAuthentication_UserNotFound() {
        // Given
        Map<String, String> credentials = new HashMap<>();
        credentials.put("username", "nonexistent@eipresso.com");
        credentials.put("password", "password123");

        when(userRepository.findByUsernameOrEmail("nonexistent@eipresso.com", "nonexistent@eipresso.com"))
                .thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            userService.processUserAuthentication(credentials);
        });

        assertTrue(exception.getMessage().contains("Invalid credentials"));
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void testProcessUserAuthentication_AccountDisabled() {
        // Given
        savedUser.setAccountEnabled(false);
        Map<String, String> credentials = new HashMap<>();
        credentials.put("username", "john@eipresso.com");
        credentials.put("password", "password123");

        when(userRepository.findByUsernameOrEmail("john@eipresso.com", "john@eipresso.com"))
                .thenReturn(Optional.of(savedUser));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            userService.processUserAuthentication(credentials);
        });

        assertTrue(exception.getMessage().contains("Account is disabled"));
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void testProcessUserAuthentication_AccountLocked() {
        // Given
        savedUser.setAccountLocked(true);
        savedUser.setLockedUntil(java.time.LocalDateTime.now().plusMinutes(30));
        Map<String, String> credentials = new HashMap<>();
        credentials.put("username", "john@eipresso.com");
        credentials.put("password", "password123");

        when(userRepository.findByUsernameOrEmail("john@eipresso.com", "john@eipresso.com"))
                .thenReturn(Optional.of(savedUser));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            userService.processUserAuthentication(credentials);
        });

        assertTrue(exception.getMessage().contains("Account is locked"));
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void testFindByUsernameOrEmail_UserExists() {
        // Given
        when(userRepository.findByUsernameOrEmail("john@eipresso.com", "john@eipresso.com"))
                .thenReturn(Optional.of(savedUser));

        // When
        User result = userService.findByUsernameOrEmail("john@eipresso.com");

        // Then
        assertNotNull(result);
        assertEquals(savedUser.getId(), result.getId());
        assertEquals(savedUser.getEmail(), result.getEmail());
    }

    @Test
    void testFindByUsernameOrEmail_UserNotFound() {
        // Given
        when(userRepository.findByUsernameOrEmail("nonexistent@eipresso.com", "nonexistent@eipresso.com"))
                .thenReturn(Optional.empty());

        // When
        User result = userService.findByUsernameOrEmail("nonexistent@eipresso.com");

        // Then
        assertNull(result);
    }

    @Test
    void testIsDatabaseHealthy_Success() {
        // Given
        when(userRepository.count()).thenReturn(5L);

        // When
        boolean result = userService.isDatabaseHealthy();

        // Then
        assertTrue(result);
        verify(userRepository).count();
    }

    @Test
    void testIsDatabaseHealthy_Failure() {
        // Given
        when(userRepository.count()).thenThrow(new RuntimeException("Database connection failed"));

        // When
        boolean result = userService.isDatabaseHealthy();

        // Then
        assertFalse(result);
        verify(userRepository).count();
    }

    @Test
    void testIsCacheHealthy() {
        // When
        boolean result = userService.isCacheHealthy();

        // Then
        assertTrue(result); // Always returns true in current implementation
    }
} 