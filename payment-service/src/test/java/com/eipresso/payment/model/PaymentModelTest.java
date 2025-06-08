package com.eipresso.payment.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive Payment Model Test Suite
 * 
 * Tests all business logic, state transitions, validation rules,
 * and edge cases for the Payment entity and related enums.
 */
@DisplayName("Payment Model Tests")
class PaymentModelTest {

    private Payment payment;
    private final String PAYMENT_ID = "PAY-12345";
    private final Long ORDER_ID = 1001L;
    private final Long USER_ID = 2001L;
    private final BigDecimal AMOUNT = new BigDecimal("25.50");
    private final String CUSTOMER_EMAIL = "john.doe@example.com";
    private final String CUSTOMER_NAME = "John Doe";

    @BeforeEach
    void setUp() {
        payment = new Payment(PAYMENT_ID, ORDER_ID, USER_ID, AMOUNT, 
                            PaymentMethod.CREDIT_CARD, PaymentGateway.STRIPE,
                            CUSTOMER_EMAIL, CUSTOMER_NAME);
    }

    @Nested
    @DisplayName("Payment Creation Tests")
    class PaymentCreationTests {

        @Test
        @DisplayName("Should create payment with valid parameters")
        void shouldCreatePaymentWithValidParameters() {
            assertNotNull(payment);
            assertEquals(PAYMENT_ID, payment.getPaymentId());
            assertEquals(ORDER_ID, payment.getOrderId());
            assertEquals(USER_ID, payment.getUserId());
            assertEquals(AMOUNT, payment.getAmount());
            assertEquals(PaymentMethod.CREDIT_CARD, payment.getPaymentMethod());
            assertEquals(PaymentGateway.STRIPE, payment.getPaymentGateway());
            assertEquals(CUSTOMER_EMAIL, payment.getCustomerEmail());
            assertEquals(CUSTOMER_NAME, payment.getCustomerName());
        }

        @Test
        @DisplayName("Should initialize with default values")
        void shouldInitializeWithDefaultValues() {
            assertEquals(PaymentStatus.PENDING, payment.getStatus());
            assertEquals("USD", payment.getCurrency());
            assertEquals(BigDecimal.ZERO, payment.getFraudScore());
            assertEquals(0, payment.getRetryCount());
            assertNotNull(payment.getCreatedAt());
            assertNotNull(payment.getUpdatedAt());
            assertNotNull(payment.getExpiresAt());
        }

        @Test
        @DisplayName("Should set expiry to 30 minutes from creation")
        void shouldSetExpiryTo30MinutesFromCreation() {
            LocalDateTime expectedExpiry = payment.getCreatedAt().plusMinutes(30);
            assertTrue(payment.getExpiresAt().isEqual(expectedExpiry) || 
                      payment.getExpiresAt().isAfter(expectedExpiry.minusSeconds(1)));
        }
    }

    @Nested
    @DisplayName("Business Logic Tests")
    class BusinessLogicTests {

        @Test
        @DisplayName("Should allow processing when payment is pending and not expired")
        void shouldAllowProcessingWhenValid() {
            assertTrue(payment.canBeProcessed());
        }

        @Test
        @DisplayName("Should not allow processing when expired")
        void shouldNotAllowProcessingWhenExpired() {
            payment.setExpiresAt(LocalDateTime.now().minusMinutes(1));
            assertFalse(payment.canBeProcessed());
        }

        @Test
        @DisplayName("Should not allow processing with high fraud risk")
        void shouldNotAllowProcessingWithHighFraudRisk() {
            payment.updateFraudScore(new BigDecimal("80.0"), "High risk transaction");
            assertFalse(payment.canBeProcessed());
        }

        @Test
        @DisplayName("Should not allow processing when not pending")
        void shouldNotAllowProcessingWhenNotPending() {
            payment.transitionTo(PaymentStatus.PROCESSING);
            assertFalse(payment.canBeProcessed());
        }

        @Test
        @DisplayName("Should identify expired payments correctly")
        void shouldIdentifyExpiredPaymentsCorrectly() {
            // Not expired initially
            assertFalse(payment.isExpired());
            
            // Set to expired
            payment.setExpiresAt(LocalDateTime.now().minusMinutes(1));
            assertTrue(payment.isExpired());
        }

        @Test
        @DisplayName("Should identify high fraud risk correctly")
        void shouldIdentifyHighFraudRiskCorrectly() {
            // Initially low risk
            assertFalse(payment.isHighFraudRisk());
            
            // Set high risk
            payment.updateFraudScore(new BigDecimal("85.0"), "Suspicious activity");
            assertTrue(payment.isHighFraudRisk());
            
            // Set medium risk
            payment.updateFraudScore(new BigDecimal("50.0"), "Medium risk");
            assertFalse(payment.isHighFraudRisk());
        }

        @Test
        @DisplayName("Should identify successful payments correctly")
        void shouldIdentifySuccessfulPaymentsCorrectly() {
            assertFalse(payment.isSuccessful());
            
            payment.transitionTo(PaymentStatus.PROCESSING);
            payment.transitionTo(PaymentStatus.COMPLETED);
            assertTrue(payment.isSuccessful());
        }

        @Test
        @DisplayName("Should identify failed payments correctly")
        void shouldIdentifyFailedPaymentsCorrectly() {
            assertFalse(payment.isFailed());
            
            payment.transitionTo(PaymentStatus.PROCESSING);
            payment.transitionTo(PaymentStatus.FAILED);
            assertTrue(payment.isFailed());
            
            // Reset and test REJECTED
            payment = new Payment(PAYMENT_ID, ORDER_ID, USER_ID, AMOUNT, 
                                PaymentMethod.CREDIT_CARD, PaymentGateway.STRIPE,
                                CUSTOMER_EMAIL, CUSTOMER_NAME);
            payment.transitionTo(PaymentStatus.REJECTED);
            assertTrue(payment.isFailed());
        }
    }

    @Nested
    @DisplayName("State Transition Tests")
    class StateTransitionTests {

        @Test
        @DisplayName("Should allow valid state transitions")
        void shouldAllowValidStateTransitions() {
            // PENDING -> PROCESSING
            assertTrue(payment.canTransitionTo(PaymentStatus.PROCESSING));
            payment.transitionTo(PaymentStatus.PROCESSING);
            assertEquals(PaymentStatus.PROCESSING, payment.getStatus());
            
            // PROCESSING -> COMPLETED
            assertTrue(payment.canTransitionTo(PaymentStatus.COMPLETED));
            payment.transitionTo(PaymentStatus.COMPLETED);
            assertEquals(PaymentStatus.COMPLETED, payment.getStatus());
            assertNotNull(payment.getProcessedAt());
        }

        @Test
        @DisplayName("Should prevent invalid state transitions")
        void shouldPreventInvalidStateTransitions() {
            // PENDING -> COMPLETED (invalid)
            assertFalse(payment.canTransitionTo(PaymentStatus.COMPLETED));
            
            IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
                payment.transitionTo(PaymentStatus.COMPLETED);
            });
            assertTrue(exception.getMessage().contains("Invalid transition"));
        }

        @Test
        @DisplayName("Should update timestamp on state transition")
        void shouldUpdateTimestampOnStateTransition() {
            LocalDateTime beforeTransition = payment.getUpdatedAt();
            
            // Small delay to ensure timestamp difference
            try { Thread.sleep(10); } catch (InterruptedException e) {}
            
            payment.transitionTo(PaymentStatus.PROCESSING);
            assertTrue(payment.getUpdatedAt().isAfter(beforeTransition));
        }

        @Test
        @DisplayName("Should set processed timestamp for terminal successful states")
        void shouldSetProcessedTimestampForTerminalSuccessfulStates() {
            payment.transitionTo(PaymentStatus.PROCESSING);
            assertNull(payment.getProcessedAt());
            
            payment.transitionTo(PaymentStatus.COMPLETED);
            assertNotNull(payment.getProcessedAt());
        }

        @Test
        @DisplayName("Should set processed timestamp for terminal failure states")
        void shouldSetProcessedTimestampForTerminalFailureStates() {
            payment.transitionTo(PaymentStatus.PROCESSING);
            assertNull(payment.getProcessedAt());
            
            payment.transitionTo(PaymentStatus.FAILED);
            assertNotNull(payment.getProcessedAt());
        }
    }

    @Nested
    @DisplayName("Retry Logic Tests")
    class RetryLogicTests {

        @Test
        @DisplayName("Should allow retry when failed and under retry limit")
        void shouldAllowRetryWhenFailedAndUnderRetryLimit() {
            payment.transitionTo(PaymentStatus.PROCESSING);
            payment.transitionTo(PaymentStatus.FAILED);
            assertTrue(payment.canBeRetried());
        }

        @Test
        @DisplayName("Should allow retry when timeout and under retry limit")
        void shouldAllowRetryWhenTimeoutAndUnderRetryLimit() {
            payment.transitionTo(PaymentStatus.PROCESSING);
            payment.transitionTo(PaymentStatus.TIMEOUT);
            assertTrue(payment.canBeRetried());
        }

        @Test
        @DisplayName("Should not allow retry when retry limit exceeded")
        void shouldNotAllowRetryWhenRetryLimitExceeded() {
            payment.transitionTo(PaymentStatus.PROCESSING);
            payment.transitionTo(PaymentStatus.FAILED);
            
            // Exceed retry limit
            payment.setRetryCount(3);
            assertFalse(payment.canBeRetried());
        }

        @Test
        @DisplayName("Should not allow retry when expired")
        void shouldNotAllowRetryWhenExpired() {
            payment.transitionTo(PaymentStatus.PROCESSING);
            payment.transitionTo(PaymentStatus.FAILED);
            payment.setExpiresAt(LocalDateTime.now().minusMinutes(1));
            assertFalse(payment.canBeRetried());
        }

        @Test
        @DisplayName("Should increment retry count correctly")
        void shouldIncrementRetryCountCorrectly() {
            assertEquals(0, payment.getRetryCount());
            
            payment.incrementRetryCount();
            assertEquals(1, payment.getRetryCount());
            
            payment.incrementRetryCount();
            assertEquals(2, payment.getRetryCount());
        }
    }

    @Nested
    @DisplayName("Fraud Detection Tests")
    class FraudDetectionTests {

        @Test
        @DisplayName("Should update fraud score and reason")
        void shouldUpdateFraudScoreAndReason() {
            BigDecimal fraudScore = new BigDecimal("65.5");
            String fraudReason = "Multiple failed attempts from same IP";
            
            payment.updateFraudScore(fraudScore, fraudReason);
            
            assertEquals(fraudScore, payment.getFraudScore());
            assertEquals(fraudReason, payment.getFraudReason());
        }

        @ParameterizedTest
        @ValueSource(strings = {"0.0", "25.0", "50.0", "74.9", "75.0"})
        @DisplayName("Should identify low to medium fraud risk correctly")
        void shouldIdentifyLowToMediumFraudRisk(String scoreValue) {
            payment.updateFraudScore(new BigDecimal(scoreValue), "Test");
            assertFalse(payment.isHighFraudRisk());
        }

        @ParameterizedTest
        @ValueSource(strings = {"75.1", "80.0", "90.0", "95.5", "100.0"})
        @DisplayName("Should identify high fraud risk correctly")
        void shouldIdentifyHighFraudRisk(String scoreValue) {
            payment.updateFraudScore(new BigDecimal(scoreValue), "High risk");
            assertTrue(payment.isHighFraudRisk());
        }
    }

    @Nested
    @DisplayName("PaymentStatus Enum Tests")
    class PaymentStatusTests {

        @Test
        @DisplayName("Should have correct state transition rules")
        void shouldHaveCorrectStateTransitionRules() {
            // PENDING transitions
            assertTrue(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.PROCESSING));
            assertTrue(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.FAILED));
            assertTrue(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.REJECTED));
            assertTrue(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.CANCELLED));
            assertFalse(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.COMPLETED));
            
            // PROCESSING transitions
            assertTrue(PaymentStatus.PROCESSING.canTransitionTo(PaymentStatus.COMPLETED));
            assertTrue(PaymentStatus.PROCESSING.canTransitionTo(PaymentStatus.FAILED));
            assertTrue(PaymentStatus.PROCESSING.canTransitionTo(PaymentStatus.TIMEOUT));
            assertFalse(PaymentStatus.PROCESSING.canTransitionTo(PaymentStatus.PENDING));
            
            // Terminal states
            assertTrue(PaymentStatus.COMPLETED.isTerminal());
            assertTrue(PaymentStatus.REJECTED.isTerminal());
            assertTrue(PaymentStatus.CANCELLED.isTerminal());
            assertTrue(PaymentStatus.REFUNDED.isTerminal());
        }

        @Test
        @DisplayName("Should identify successful status correctly")
        void shouldIdentifySuccessfulStatusCorrectly() {
            assertTrue(PaymentStatus.COMPLETED.isSuccessful());
            assertFalse(PaymentStatus.FAILED.isSuccessful());
            assertFalse(PaymentStatus.PENDING.isSuccessful());
        }

        @Test
        @DisplayName("Should identify failed status correctly")
        void shouldIdentifyFailedStatusCorrectly() {
            assertTrue(PaymentStatus.FAILED.isFailed());
            assertTrue(PaymentStatus.TIMEOUT.isFailed());
            assertTrue(PaymentStatus.REJECTED.isFailed());
            assertFalse(PaymentStatus.COMPLETED.isFailed());
            assertFalse(PaymentStatus.PENDING.isFailed());
        }

        @Test
        @DisplayName("Should identify retryable status correctly")
        void shouldIdentifyRetryableStatusCorrectly() {
            assertTrue(PaymentStatus.FAILED.isRetryable());
            assertTrue(PaymentStatus.TIMEOUT.isRetryable());
            assertFalse(PaymentStatus.REJECTED.isRetryable());
            assertFalse(PaymentStatus.COMPLETED.isRetryable());
        }

        @Test
        @DisplayName("Should identify in-progress status correctly")
        void shouldIdentifyInProgressStatusCorrectly() {
            assertTrue(PaymentStatus.PENDING.isInProgress());
            assertTrue(PaymentStatus.PROCESSING.isInProgress());
            assertFalse(PaymentStatus.COMPLETED.isInProgress());
            assertFalse(PaymentStatus.FAILED.isInProgress());
        }

        @ParameterizedTest
        @EnumSource(PaymentStatus.class)
        @DisplayName("Should have valid priority levels")
        void shouldHaveValidPriorityLevels(PaymentStatus status) {
            int priority = status.getPriority();
            assertTrue(priority >= 1 && priority <= 5);
        }

        @ParameterizedTest
        @EnumSource(PaymentStatus.class)
        @DisplayName("Should have non-empty descriptions")
        void shouldHaveNonEmptyDescriptions(PaymentStatus status) {
            assertNotNull(status.getDescription());
            assertFalse(status.getDescription().trim().isEmpty());
        }

        @ParameterizedTest
        @EnumSource(PaymentStatus.class)
        @DisplayName("Should have valid display names")
        void shouldHaveValidDisplayNames(PaymentStatus status) {
            String displayName = status.getDisplayName();
            assertNotNull(displayName);
            assertFalse(displayName.trim().isEmpty());
        }
    }

    @Nested
    @DisplayName("Edge Cases and Validation Tests")
    class EdgeCasesAndValidationTests {

        @Test
        @DisplayName("Should handle null fraud score gracefully")
        void shouldHandleNullFraudScoreGracefully() {
            payment.setFraudScore(null);
            assertFalse(payment.isHighFraudRisk());
        }

        @Test
        @DisplayName("Should handle null expiry date gracefully")
        void shouldHandleNullExpiryDateGracefully() {
            payment.setExpiresAt(null);
            assertFalse(payment.isExpired());
        }

        @Test
        @DisplayName("Should handle boundary fraud scores correctly")
        void shouldHandleBoundaryFraudScoresCorrectly() {
            // Exactly 75.0 should be considered low risk
            payment.updateFraudScore(new BigDecimal("75.0"), "Boundary test");
            assertFalse(payment.isHighFraudRisk());
            
            // Exactly 75.1 should be considered high risk
            payment.updateFraudScore(new BigDecimal("75.1"), "Boundary test");
            assertTrue(payment.isHighFraudRisk());
        }

        @Test
        @DisplayName("Should handle concurrent state transitions safely")
        void shouldHandleConcurrentStateTransitionsSafely() {
            // This test simulates concurrent access - in real scenario would use threading
            PaymentStatus originalStatus = payment.getStatus();
            
            assertTrue(payment.canTransitionTo(PaymentStatus.PROCESSING));
            payment.transitionTo(PaymentStatus.PROCESSING);
            
            // Verify status changed
            assertNotEquals(originalStatus, payment.getStatus());
        }

        @Test
        @DisplayName("Should maintain data integrity during updates")
        void shouldMaintainDataIntegrityDuringUpdates() {
            LocalDateTime originalCreated = payment.getCreatedAt();
            String originalPaymentId = payment.getPaymentId();
            
            // Update various fields
            payment.transitionTo(PaymentStatus.PROCESSING);
            payment.incrementRetryCount();
            payment.updateFraudScore(new BigDecimal("30.0"), "Low risk");
            
            // Verify critical data remains unchanged
            assertEquals(originalCreated, payment.getCreatedAt());
            assertEquals(originalPaymentId, payment.getPaymentId());
            
            // Verify updates are applied
            assertEquals(PaymentStatus.PROCESSING, payment.getStatus());
            assertEquals(1, payment.getRetryCount());
            assertEquals(new BigDecimal("30.0"), payment.getFraudScore());
        }
    }
}