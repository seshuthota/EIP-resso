package com.eipresso.user.service;

import com.eipresso.user.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class JwtTokenService {
    
    private static final Logger logger = LoggerFactory.getLogger(JwtTokenService.class);
    
    @Value("${eipresso.jwt.secret:eipresso-super-secret-key-for-jwt-tokens-must-be-256-bits-minimum}")
    private String jwtSecret;
    
    @Value("${eipresso.jwt.access-token-expiration:900}") // 15 minutes
    private long accessTokenExpirationSeconds;
    
    @Value("${eipresso.jwt.refresh-token-expiration:604800}") // 7 days
    private long refreshTokenExpirationSeconds;
    
    @Value("${eipresso.jwt.issuer:eip-resso-user-service}")
    private String issuer;
    
    /**
     * Generate JWT access token for user
     */
    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("username", user.getUsername());
        claims.put("email", user.getEmail());
        claims.put("role", user.getRole().name());
        claims.put("tokenType", "ACCESS");
        
        return createToken(claims, user.getUsername(), accessTokenExpirationSeconds);
    }
    
    /**
     * Generate JWT refresh token for user
     */
    public String generateRefreshToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("username", user.getUsername());
        claims.put("tokenType", "REFRESH");
        
        return createToken(claims, user.getUsername(), refreshTokenExpirationSeconds);
    }
    
    /**
     * Create JWT token with claims and expiration
     */
    private String createToken(Map<String, Object> claims, String subject, long expirationSeconds) {
        Instant now = Instant.now();
        Instant expiration = now.plus(expirationSeconds, ChronoUnit.SECONDS);
        
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuer(issuer)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiration))
                .signWith(getSigningKey())
                .compact();
    }
    
    /**
     * Extract username from JWT token
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }
    
    /**
     * Extract user ID from JWT token
     */
    public Long extractUserId(String token) {
        return extractClaim(token, claims -> claims.get("userId", Long.class));
    }
    
    /**
     * Extract user role from JWT token
     */
    public String extractUserRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }
    
    /**
     * Extract token type (ACCESS or REFRESH)
     */
    public String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get("tokenType", String.class));
    }
    
    /**
     * Extract expiration date from JWT token
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }
    
    /**
     * Extract specific claim from JWT token
     */
    public <T> T extractClaim(String token, ClaimsResolver<T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.resolve(claims);
    }
    
    /**
     * Extract all claims from JWT token
     */
    private Claims extractAllClaims(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (JwtException e) {
            logger.error("Failed to parse JWT token: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid JWT token", e);
        }
    }
    
    /**
     * Check if JWT token is expired
     */
    public boolean isTokenExpired(String token) {
        try {
            return extractExpiration(token).before(new Date());
        } catch (Exception e) {
            logger.error("Error checking token expiration: {}", e.getMessage());
            return true;
        }
    }
    
    /**
     * Validate JWT token against user
     */
    public boolean validateToken(String token, User user) {
        try {
            final String username = extractUsername(token);
            final Long userId = extractUserId(token);
            
            return (username.equals(user.getUsername()) || 
                    username.equals(user.getEmail())) &&
                   userId.equals(user.getId()) &&
                   !isTokenExpired(token);
        } catch (Exception e) {
            logger.error("Token validation failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Validate token format and signature
     */
    public boolean isValidToken(String token) {
        try {
            extractAllClaims(token);
            return !isTokenExpired(token);
        } catch (Exception e) {
            logger.error("Token validation failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Get signing key for JWT
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }
    
    /**
     * Get access token expiration time in seconds
     */
    public long getTokenExpirationTime() {
        return accessTokenExpirationSeconds;
    }
    
    /**
     * Get refresh token expiration time in seconds
     */
    public long getRefreshTokenExpirationTime() {
        return refreshTokenExpirationSeconds;
    }
    
    /**
     * Functional interface for claims resolution
     */
    @FunctionalInterface
    public interface ClaimsResolver<T> {
        T resolve(Claims claims);
    }
} 