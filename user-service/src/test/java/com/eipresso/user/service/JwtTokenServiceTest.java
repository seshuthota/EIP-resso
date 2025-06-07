package com.eipresso.user.service;

import com.eipresso.user.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Key;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class JwtTokenServiceTest {

    private JwtTokenService jwtTokenService;
    private User testUser;
    private Key secretKey;

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService();
        
        // Set up test configuration
        String secret = "mySecretKeyThatIsAtLeast32CharactersLongForHS256";
        ReflectionTestUtils.setField(jwtTokenService, "jwtSecret", secret);
        ReflectionTestUtils.setField(jwtTokenService, "accessTokenExpirationSeconds", 86400L); // 24 hours
        ReflectionTestUtils.setField(jwtTokenService, "refreshTokenExpirationSeconds", 604800L); // 7 days
        ReflectionTestUtils.setField(jwtTokenService, "issuer", "eip-resso-test");
        
        // No need to initialize - no @PostConstruct method in actual implementation
        
        // Set up test user
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("johndoe");
        testUser.setEmail("john@eipresso.com");
        testUser.setRole(User.Role.CUSTOMER);
        
        secretKey = Keys.hmacShaKeyFor(secret.getBytes());
    }

    @Test
    void testGenerateToken_Success() {
        // When
        String token = jwtTokenService.generateToken(testUser);

        // Then
        assertNotNull(token);
        assertFalse(token.isEmpty());
        
        // Verify token structure (should have 3 parts separated by dots)
        assertEquals(3, token.split("\\.").length);
        
        // Verify token contents
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
        
        assertEquals("johndoe", claims.getSubject());
        assertEquals("john@eipresso.com", claims.get("email"));
        assertEquals("CUSTOMER", claims.get("role"));
        assertEquals("ACCESS", claims.get("tokenType"));
        assertEquals(1L, claims.get("userId", Long.class));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
        assertTrue(claims.getExpiration().after(new Date()));
    }

    @Test
    void testGenerateRefreshToken_Success() {
        // When
        String refreshToken = jwtTokenService.generateRefreshToken(testUser);

        // Then
        assertNotNull(refreshToken);
        assertFalse(refreshToken.isEmpty());
        
        // Verify token structure
        assertEquals(3, refreshToken.split("\\.").length);
        
        // Verify token contents
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(refreshToken)
                .getBody();
        
        assertEquals("johndoe", claims.getSubject());
        assertEquals("REFRESH", claims.get("tokenType"));
        assertEquals(1L, claims.get("userId", Long.class));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
        assertTrue(claims.getExpiration().after(new Date()));
    }

    @Test
    void testValidateToken_ValidToken() {
        // Given
        String token = jwtTokenService.generateToken(testUser);

        // When
        boolean isValid = jwtTokenService.validateToken(token, testUser);

        // Then
        assertTrue(isValid);
    }

    @Test
    void testValidateToken_InvalidToken() {
        // Given
        String invalidToken = "invalid.jwt.token";

        // When
        boolean isValid = jwtTokenService.isValidToken(invalidToken);

        // Then
        assertFalse(isValid);
    }

    @Test
    void testValidateToken_ExpiredToken() {
        // Given - Create an expired token
        String expiredToken = Jwts.builder()
                .setSubject(testUser.getUsername())
                .claim("email", testUser.getEmail())
                .claim("role", testUser.getRole().toString())
                .claim("userId", testUser.getId())
                .setIssuedAt(new Date(System.currentTimeMillis() - 2000))
                .setExpiration(new Date(System.currentTimeMillis() - 1000)) // Expired 1 second ago
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();

        // When
        boolean isValid = jwtTokenService.isValidToken(expiredToken);

        // Then
        assertFalse(isValid);
    }

    @Test
    void testValidateToken_MalformedToken() {
        // Given
        String malformedToken = "malformed.token";

        // When
        boolean isValid = jwtTokenService.isValidToken(malformedToken);

        // Then
        assertFalse(isValid);
    }

    @Test
    void testGetUsernameFromToken_Success() {
        // Given
        String token = jwtTokenService.generateToken(testUser);

        // When
        String username = jwtTokenService.extractUsername(token);

        // Then
        assertEquals("johndoe", username);
    }

    @Test
    void testGetUsernameFromToken_InvalidToken() {
        // Given
        String invalidToken = "invalid.jwt.token";

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            jwtTokenService.extractUsername(invalidToken);
        });
    }

    @Test
    void testGetUserIdFromToken_Success() {
        // Given
        String token = jwtTokenService.generateToken(testUser);

        // When
        Long userId = jwtTokenService.extractUserId(token);

        // Then
        assertEquals(1L, userId);
    }

    @Test
    void testGetUserIdFromToken_InvalidToken() {
        // Given
        String invalidToken = "invalid.jwt.token";

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            jwtTokenService.extractUserId(invalidToken);
        });
    }

    @Test
    void testGetTokenExpirationTime() {
        // When
        Long expirationTime = jwtTokenService.getTokenExpirationTime();

        // Then
        assertEquals(86400L, expirationTime); // 24 hours in seconds (from our test setup)
    }

    @Test
    void testIsTokenExpired_NotExpired() {
        // Given
        String token = jwtTokenService.generateToken(testUser);

        // When
        boolean isExpired = jwtTokenService.isTokenExpired(token);

        // Then
        assertFalse(isExpired);
    }

    @Test
    void testIsTokenExpired_Expired() {
        // Given - Create an expired token
        String expiredToken = Jwts.builder()
                .setSubject(testUser.getUsername())
                .claim("email", testUser.getEmail())
                .claim("role", testUser.getRole().toString())
                .claim("userId", testUser.getId())
                .setIssuedAt(new Date(System.currentTimeMillis() - 2000))
                .setExpiration(new Date(System.currentTimeMillis() - 1000)) // Expired 1 second ago
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();

        // When
        boolean isExpired = jwtTokenService.isTokenExpired(expiredToken);

        // Then
        assertTrue(isExpired);
    }

    @Test
    void testRefreshTokenValidation() {
        // Given
        String refreshToken = jwtTokenService.generateRefreshToken(testUser);

        // When
        boolean isValid = jwtTokenService.validateToken(refreshToken, testUser);
        String tokenType = jwtTokenService.extractTokenType(refreshToken);

        // Then
        assertTrue(isValid);
        assertEquals("REFRESH", tokenType);
    }

    @Test
    void testTokenDifferentiation() {
        // Given
        String accessToken = jwtTokenService.generateToken(testUser);
        String refreshToken = jwtTokenService.generateRefreshToken(testUser);

        // Then
        assertNotEquals(accessToken, refreshToken);
        
        // Verify access token has ACCESS type
        String accessTokenType = jwtTokenService.extractTokenType(accessToken);
        assertEquals("ACCESS", accessTokenType);
        
        // Verify refresh token has REFRESH type
        String refreshTokenType = jwtTokenService.extractTokenType(refreshToken);
        assertEquals("REFRESH", refreshTokenType);
    }
} 