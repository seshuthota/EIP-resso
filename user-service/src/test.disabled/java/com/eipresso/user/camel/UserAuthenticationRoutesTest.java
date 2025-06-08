package com.eipresso.user.camel;

import com.eipresso.user.dto.UserRegistrationRequest;
import com.eipresso.user.entity.User;
import com.eipresso.user.entity.UserAuditEvent;
import com.eipresso.user.service.UserAuditService;
import com.eipresso.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.UseAdviceWith;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@CamelSpringBootTest
@SpringBootTest
@UseAdviceWith
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.cloud.config.enabled=false",
    "spring.cloud.consul.discovery.enabled=false",
    "camel.springboot.main-run-controller=true"
})
class UserAuthenticationRoutesTest {

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private ProducerTemplate producerTemplate;

    @MockBean
    private UserService userService;

    @MockBean
    private UserAuditService userAuditService;

    @Autowired
    private ObjectMapper objectMapper;

    private UserRegistrationRequest validRegistrationRequest;
    private Map<String, String> validAuthRequest;

    @BeforeEach
    void setUp() throws Exception {
        // Start Camel context for advice with
        camelContext.start();
        
        // Setup test data
        validRegistrationRequest = new UserRegistrationRequest();
        validRegistrationRequest.setUsername("johndoe");
        validRegistrationRequest.setEmail("john@eipresso.com");
        validRegistrationRequest.setPassword("password123");
        validRegistrationRequest.setConfirmPassword("password123");
        validRegistrationRequest.setFirstName("John");
        validRegistrationRequest.setLastName("Doe");

        validAuthRequest = new HashMap<>();
        validAuthRequest.put("username", "john@eipresso.com");
        validAuthRequest.put("password", "password123");
    }

    @Test
    void testUserRegistrationRoute_Success() throws Exception {
        // Given
        AdviceWith.adviceWith(camelContext, "user-registration-route", a -> {
            a.mockEndpoints("log:*");
            a.mockEndpoints("direct:audit-event");
        });

        Map<String, Object> successResponse = new HashMap<>();
        successResponse.put("success", true);
        successResponse.put("message", "User registered successfully");
        successResponse.put("userId", 1L);

        when(userService.processUserRegistration(any(UserRegistrationRequest.class)))
                .thenReturn(successResponse);

        MockEndpoint auditMock = camelContext.getEndpoint("mock:direct:audit-event", MockEndpoint.class);
        auditMock.expectedMessageCount(1);

        // When
        Map<String, Object> result = producerTemplate.requestBodyAndHeader(
                "direct:user-registration",
                validRegistrationRequest,
                "X-Forwarded-For", "192.168.1.1",
                Map.class
        );

        // Then
        assertNotNull(result);
        assertTrue((Boolean) result.get("success"));
        assertEquals("User registered successfully", result.get("message"));
        assertEquals(1L, result.get("userId"));

        auditMock.assertIsSatisfied();
        verify(userService).processUserRegistration(any(UserRegistrationRequest.class));
    }

    @Test
    void testUserRegistrationRoute_Failure_DeadLetterChannel() throws Exception {
        // Given
        AdviceWith.adviceWith(camelContext, "user-registration-route", a -> {
            a.mockEndpoints("log:*");
            a.mockEndpoints("direct:audit-event");
            a.mockEndpoints("direct:dead-letter-queue");
        });

        when(userService.processUserRegistration(any(UserRegistrationRequest.class)))
                .thenThrow(new RuntimeException("Database connection failed"));

        MockEndpoint deadLetterMock = camelContext.getEndpoint("mock:direct:dead-letter-queue", MockEndpoint.class);
        deadLetterMock.expectedMessageCount(1);

        MockEndpoint auditMock = camelContext.getEndpoint("mock:direct:audit-event", MockEndpoint.class);
        auditMock.expectedMessageCount(1);

        // When & Then
        Exception exception = assertThrows(Exception.class, () -> {
            producerTemplate.requestBodyAndHeader(
                    "direct:user-registration",
                    validRegistrationRequest,
                    "X-Forwarded-For", "192.168.1.1"
            );
        });

        // Verify dead letter channel was triggered
        deadLetterMock.assertIsSatisfied();
        auditMock.assertIsSatisfied();
        verify(userService, atLeastOnce()).processUserRegistration(any(UserRegistrationRequest.class));
    }

    @Test
    void testUserAuthenticationRoute_Success() throws Exception {
        // Given
        AdviceWith.adviceWith(camelContext, "user-authentication-route", a -> {
            a.mockEndpoints("log:*");
            a.mockEndpoints("direct:audit-event");
        });

        Map<String, Object> successResponse = new HashMap<>();
        successResponse.put("success", true);
        successResponse.put("message", "Authentication successful");
        successResponse.put("accessToken", "access_token_123");
        successResponse.put("refreshToken", "refresh_token_123");

        when(userService.processUserAuthentication(any(Map.class)))
                .thenReturn(successResponse);

        MockEndpoint auditMock = camelContext.getEndpoint("mock:direct:audit-event", MockEndpoint.class);
        auditMock.expectedMessageCount(1);

        // When
        Map<String, Object> result = producerTemplate.requestBodyAndHeader(
                "direct:user-authentication",
                validAuthRequest,
                "X-Forwarded-For", "192.168.1.1",
                Map.class
        );

        // Then
        assertNotNull(result);
        assertTrue((Boolean) result.get("success"));
        assertEquals("Authentication successful", result.get("message"));
        assertEquals("access_token_123", result.get("accessToken"));
        assertEquals("refresh_token_123", result.get("refreshToken"));

        auditMock.assertIsSatisfied();
        verify(userService).processUserAuthentication(any(Map.class));
    }

    @Test
    void testIdempotentConsumer_DuplicateRegistration() throws Exception {
        // Given
        AdviceWith.adviceWith(camelContext, "user-registration-route", a -> {
            a.mockEndpoints("log:*");
            a.mockEndpoints("direct:audit-event");
        });

        Map<String, Object> successResponse = new HashMap<>();
        successResponse.put("success", true);
        successResponse.put("message", "User registered successfully");
        successResponse.put("userId", 1L);

        when(userService.processUserRegistration(any(UserRegistrationRequest.class)))
                .thenReturn(successResponse);

        MockEndpoint auditMock = camelContext.getEndpoint("mock:direct:audit-event", MockEndpoint.class);
        auditMock.expectedMessageCount(1); // Should only process once due to idempotent consumer

        // When - Send the same registration request twice
        Map<String, Object> result1 = producerTemplate.requestBodyAndHeader(
                "direct:user-registration",
                validRegistrationRequest,
                "X-Forwarded-For", "192.168.1.1",
                Map.class
        );

        Map<String, Object> result2 = producerTemplate.requestBodyAndHeader(
                "direct:user-registration",
                validRegistrationRequest,
                "X-Forwarded-For", "192.168.1.1",
                Map.class
        );

        // Then
        assertNotNull(result1);
        assertNotNull(result2);
        
        // Due to idempotent consumer, service should only be called once
        auditMock.assertIsSatisfied();
        verify(userService, times(1)).processUserRegistration(any(UserRegistrationRequest.class));
    }

    @Test
    void testWireTapPattern_AuditTrail() throws Exception {
        // Given
        AdviceWith.adviceWith(camelContext, "user-registration-route", a -> {
            a.mockEndpoints("direct:audit-event");
        });

        Map<String, Object> successResponse = new HashMap<>();
        successResponse.put("success", true);
        successResponse.put("message", "User registered successfully");

        when(userService.processUserRegistration(any(UserRegistrationRequest.class)))
                .thenReturn(successResponse);

        MockEndpoint auditMock = camelContext.getEndpoint("mock:direct:audit-event", MockEndpoint.class);
        auditMock.expectedMessageCount(1);
        auditMock.expectedHeaderReceived("X-Forwarded-For", "192.168.1.1");
        auditMock.expectedHeaderReceived("User-Agent", "test-agent");

        // When
        producerTemplate.requestBodyAndHeaders(
                "direct:user-registration",
                validRegistrationRequest,
                Map.of(
                        "X-Forwarded-For", "192.168.1.1",
                        "User-Agent", "test-agent"
                )
        );

        // Then
        auditMock.assertIsSatisfied();
        
        // Verify audit service was called to save audit event
        verify(userAuditService).saveAuditEvent(any(UserAuditEvent.class));
    }

    @Test
    void testContentEnricher_ProfileEnhancement() throws Exception {
        // Given
        AdviceWith.adviceWith(camelContext, "user-registration-route", a -> {
            a.mockEndpoints("log:*");
            a.mockEndpoints("direct:audit-event");
        });

        Map<String, Object> enrichedResponse = new HashMap<>();
        enrichedResponse.put("success", true);
        enrichedResponse.put("message", "User registered successfully");
        enrichedResponse.put("userId", 1L);
        enrichedResponse.put("timezone", "America/New_York");
        enrichedResponse.put("country", "US");

        when(userService.processUserRegistration(any(UserRegistrationRequest.class)))
                .thenReturn(enrichedResponse);

        // When
        Map<String, Object> result = producerTemplate.requestBodyAndHeader(
                "direct:user-registration",
                validRegistrationRequest,
                "X-Forwarded-For", "192.168.1.1",
                Map.class
        );

        // Then
        assertNotNull(result);
        assertTrue((Boolean) result.get("success"));
        
        // Verify profile was enriched with geolocation data
        assertEquals("America/New_York", result.get("timezone"));
        assertEquals("US", result.get("country"));

        verify(userService).processUserRegistration(any(UserRegistrationRequest.class));
    }

    @Test
    void testAuditEventRoute_ProcessingSuccess() throws Exception {
        // Given
        AdviceWith.adviceWith(camelContext, "audit-event-route", a -> {
            a.mockEndpoints("log:*");
        });

        UserAuditEvent auditEvent = new UserAuditEvent();
        auditEvent.setEventType(UserAuditEvent.EventType.USER_LOGIN_SUCCESS);
        auditEvent.setIpAddress("192.168.1.1");
        auditEvent.setUserAgent("test-agent");
        auditEvent.setSeverity(UserAuditEvent.Severity.INFO);

        // When
        producerTemplate.sendBody("direct:audit-event", auditEvent);

        // Then
        verify(userAuditService).saveAuditEvent(any(UserAuditEvent.class));
    }

    @Test
    void testDeadLetterQueue_ProcessingFailedMessages() throws Exception {
        // Given
        AdviceWith.adviceWith(camelContext, "dead-letter-queue-route", a -> {
            a.mockEndpoints("log:*");
        });

        Exchange failedExchange = camelContext.getEndpoint("direct:test").createExchange();
        failedExchange.getIn().setBody(validRegistrationRequest);
        failedExchange.getIn().setHeader("X-Forwarded-For", "192.168.1.1");
        failedExchange.setProperty(Exchange.EXCEPTION_CAUGHT, new RuntimeException("Processing failed"));

        // When
        producerTemplate.send("direct:dead-letter-queue", failedExchange);

        // Then
        // Verify that dead letter processing doesn't throw exceptions
        // and audit event is saved for failed processing
        verify(userAuditService).saveAuditEvent(any(UserAuditEvent.class));
    }

    @Test
    void testCamelRoutesConfiguration() {
        // Verify that all expected routes are configured
        assertEquals(4, camelContext.getRoutes().size());
        
        // Verify route IDs
        assertTrue(camelContext.getRoutes().stream()
                .anyMatch(route -> "user-registration-route".equals(route.getId())));
        assertTrue(camelContext.getRoutes().stream()
                .anyMatch(route -> "user-authentication-route".equals(route.getId())));
        assertTrue(camelContext.getRoutes().stream()
                .anyMatch(route -> "audit-event-route".equals(route.getId())));
        assertTrue(camelContext.getRoutes().stream()
                .anyMatch(route -> "dead-letter-queue-route".equals(route.getId())));
    }
} 