package com.eipresso.orchestration.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * CompensationAction represents a compensating transaction for Saga pattern
 * Used to rollback changes when workflow fails after partial completion
 */
@Entity
@Table(name = "compensation_actions")
public class CompensationAction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotNull
    @Size(max = 100)
    @Column(nullable = false)
    private String compensationId;
    
    @NotNull
    @Size(max = 200)
    @Column(nullable = false)
    private String compensationName;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CompensationType compensationType;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CompensationStatus status;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_instance_id", nullable = false)
    private WorkflowInstance workflowInstance;
    
    @NotNull
    @Size(max = 100)
    @Column(nullable = false)
    private String originalStepId;
    
    @NotNull
    @Column(nullable = false)
    private Integer executionOrder;
    
    @Column(length = 500)
    private String serviceEndpoint;
    
    @Column(length = 2000)
    private String compensationPayload;
    
    @Column(length = 2000)
    private String responsePayload;
    
    @Column(length = 1000)
    private String errorMessage;
    
    @Column(nullable = false)
    private Integer retryCount = 0;
    
    @Column(nullable = false)
    private Integer maxRetries = 3;
    
    @Column(nullable = false)
    private Boolean mandatory = true;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    private LocalDateTime executedAt;
    
    private LocalDateTime completedAt;
    
    @Column(nullable = false)
    private Long timeoutSeconds = 60L;
    
    // Constructors
    public CompensationAction() {}
    
    public CompensationAction(String compensationId, String compensationName, 
                            CompensationType compensationType, String originalStepId, Integer executionOrder) {
        this.compensationId = compensationId;
        this.compensationName = compensationName;
        this.compensationType = compensationType;
        this.originalStepId = originalStepId;
        this.executionOrder = executionOrder;
        this.status = CompensationStatus.PENDING;
    }
    
    // Business Logic Methods
    public void startExecution() {
        if (this.status != CompensationStatus.PENDING) {
            throw new IllegalStateException("Compensation can only be started from PENDING state");
        }
        this.status = CompensationStatus.EXECUTING;
        this.executedAt = LocalDateTime.now();
    }
    
    public void completeExecution(String responsePayload) {
        if (this.status != CompensationStatus.EXECUTING) {
            throw new IllegalStateException("Compensation can only be completed from EXECUTING state");
        }
        this.status = CompensationStatus.COMPLETED;
        this.responsePayload = responsePayload;
        this.completedAt = LocalDateTime.now();
    }
    
    public void failExecution(String errorMessage) {
        this.status = CompensationStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = LocalDateTime.now();
    }
    
    public void skipExecution(String reason) {
        this.status = CompensationStatus.SKIPPED;
        this.errorMessage = reason;
        this.completedAt = LocalDateTime.now();
    }
    
    public boolean canRetry() {
        return retryCount < maxRetries && status == CompensationStatus.FAILED;
    }
    
    public void incrementRetryCount() {
        this.retryCount++;
        this.status = CompensationStatus.PENDING;
        this.errorMessage = null;
        this.responsePayload = null;
    }
    
    public boolean isCompleted() {
        return status == CompensationStatus.COMPLETED;
    }
    
    public boolean isFailed() {
        return status == CompensationStatus.FAILED;
    }
    
    public boolean isSkipped() {
        return status == CompensationStatus.SKIPPED;
    }
    
    public boolean isMandatory() {
        return mandatory;
    }
    
    public boolean canBeSkipped() {
        return !mandatory && compensationType.isSkippable();
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getCompensationId() { return compensationId; }
    public void setCompensationId(String compensationId) { this.compensationId = compensationId; }
    
    public String getCompensationName() { return compensationName; }
    public void setCompensationName(String compensationName) { this.compensationName = compensationName; }
    
    public CompensationType getCompensationType() { return compensationType; }
    public void setCompensationType(CompensationType compensationType) { this.compensationType = compensationType; }
    
    public CompensationStatus getStatus() { return status; }
    public void setStatus(CompensationStatus status) { this.status = status; }
    
    public WorkflowInstance getWorkflowInstance() { return workflowInstance; }
    public void setWorkflowInstance(WorkflowInstance workflowInstance) { this.workflowInstance = workflowInstance; }
    
    public String getOriginalStepId() { return originalStepId; }
    public void setOriginalStepId(String originalStepId) { this.originalStepId = originalStepId; }
    
    public Integer getExecutionOrder() { return executionOrder; }
    public void setExecutionOrder(Integer executionOrder) { this.executionOrder = executionOrder; }
    
    public String getServiceEndpoint() { return serviceEndpoint; }
    public void setServiceEndpoint(String serviceEndpoint) { this.serviceEndpoint = serviceEndpoint; }
    
    public String getCompensationPayload() { return compensationPayload; }
    public void setCompensationPayload(String compensationPayload) { this.compensationPayload = compensationPayload; }
    
    public String getResponsePayload() { return responsePayload; }
    public void setResponsePayload(String responsePayload) { this.responsePayload = responsePayload; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    
    public Integer getMaxRetries() { return maxRetries; }
    public void setMaxRetries(Integer maxRetries) { this.maxRetries = maxRetries; }
    
    public Boolean getMandatory() { return mandatory; }
    public void setMandatory(Boolean mandatory) { this.mandatory = mandatory; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public LocalDateTime getExecutedAt() { return executedAt; }
    public void setExecutedAt(LocalDateTime executedAt) { this.executedAt = executedAt; }
    
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    
    public Long getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(Long timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}

/**
 * Types of compensation actions with their characteristics
 */
enum CompensationType {
    PAYMENT_REFUND("Refund payment transaction", false, true),
    INVENTORY_RELEASE("Release reserved inventory", true, false),
    ORDER_CANCELLATION("Cancel order status", false, false),
    NOTIFICATION_CANCELLATION("Send cancellation notification", true, false),
    ACCOUNT_ROLLBACK("Rollback account changes", false, true),
    EXTERNAL_SERVICE_ROLLBACK("Rollback external service call", false, true),
    DATA_CLEANUP("Clean up created data", true, false),
    SAGA_COORDINATION("Coordinate sub-saga rollback", false, true);
    
    private final String description;
    private final boolean skippable;
    private final boolean criticalForConsistency;
    
    CompensationType(String description, boolean skippable, boolean criticalForConsistency) {
        this.description = description;
        this.skippable = skippable;
        this.criticalForConsistency = criticalForConsistency;
    }
    
    public String getDescription() { return description; }
    public boolean isSkippable() { return skippable; }
    public boolean isCriticalForConsistency() { return criticalForConsistency; }
}

/**
 * Compensation action execution states
 */
enum CompensationStatus {
    PENDING,      // Compensation is ready to execute
    EXECUTING,    // Compensation is currently executing
    COMPLETED,    // Compensation completed successfully
    FAILED,       // Compensation failed during execution
    SKIPPED       // Compensation was skipped (for non-mandatory actions)
} 