package com.eipresso.user.config;

import com.eipresso.user.service.JwtTokenService;
import com.eipresso.user.service.UserService;
import com.hazelcast.map.IMap;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    
    @Autowired
    private JwtTokenService jwtTokenService;
    
    @Autowired
    private IMap<String, String> jwtBlacklistMap;
    
    private static final List<String> EXCLUDED_PATHS = Arrays.asList(
        "/api/users/register",
        "/api/users/login", 
        "/actuator",
        "/api/test",
        "/camel"
    );

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, 
                                  @NonNull HttpServletResponse response, 
                                  @NonNull FilterChain filterChain) throws ServletException, IOException {
        
        String requestPath = request.getRequestURI();
        
        // Skip JWT validation for excluded paths
        if (EXCLUDED_PATHS.stream().anyMatch(requestPath::startsWith)) {
            filterChain.doFilter(request, response);
            return;
        }
        
        try {
            String authHeader = request.getHeader("Authorization");
            
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                logger.debug("No valid Authorization header found for: {}", requestPath);
                filterChain.doFilter(request, response);
                return;
            }
            
            String jwt = authHeader.substring(7);
            
            // Check if token is blacklisted
            if (jwtBlacklistMap.containsKey(jwt)) {
                logger.warn("Blacklisted token attempted access to: {}", requestPath);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\":\"Token has been invalidated\"}");
                return;
            }
            
            // Validate token
            if (!jwtTokenService.isValidToken(jwt)) {
                logger.warn("Invalid JWT token for: {}", requestPath);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\":\"Invalid or expired token\"}");
                return;
            }
            
            // Extract user details from token
            String username = jwtTokenService.extractUsername(jwt);
            String userRole = jwtTokenService.extractUserRole(jwt);
            Long userId = jwtTokenService.extractUserId(jwt);
            
            // Create authentication token
            List<SimpleGrantedAuthority> authorities = Arrays.asList(
                new SimpleGrantedAuthority("ROLE_" + userRole)
            );
            
            UsernamePasswordAuthenticationToken authToken = 
                new UsernamePasswordAuthenticationToken(username, null, authorities);
            
            // Add additional details
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            
            // Set security context
            SecurityContextHolder.getContext().setAuthentication(authToken);
            
            // Add user info to request attributes for downstream processing
            request.setAttribute("userId", userId);
            request.setAttribute("username", username);
            request.setAttribute("userRole", userRole);
            
            logger.debug("JWT authentication successful for user: {} accessing: {}", username, requestPath);
            
        } catch (Exception e) {
            logger.error("JWT authentication failed: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Authentication failed\"}");
            return;
        }
        
        filterChain.doFilter(request, response);
    }
} 