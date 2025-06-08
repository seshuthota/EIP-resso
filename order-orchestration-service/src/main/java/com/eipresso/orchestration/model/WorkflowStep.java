package com.eipresso.orchestration.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * WorkflowStep represents an individual step within a workflow execution
 * Tracks step execution status, timing, and results for Process Manager pattern
 */
@Entity
@Table(name = "workflow_steps")
public class WorkflowStep {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotNull
    @Size(max = 100)
    @Column(nullable = false)
    private String stepId;
    
    @NotNull
    @Size(max = 200)
    @Column(nullable = false)
    private String stepName;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StepType stepType;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StepStatus status;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_instance_id", nullable = false)
    private WorkflowInstance workflowInstance;
    
    @NotNull
    @Column(nullable = false)
    private Integer stepOrder;
    
    @Column(length = 500)
    private String serviceEndpoint;
    
    @Column(length = 2000)
    private String requestPayload;
    
    @Column(length = 2000)
    private String responsePayload;
    
    @Column(length = 1000)
    private String errorMessage;
    
    @Column(nullable = false)
    private Integer retryCount = 0;
    
    @Column(nullable = false)
    private Integer maxRetries = 3;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    private LocalDateTime startedAt;
    
    private LocalDateTime completedAt;
    
    private LocalDateTime timeoutAt;
    
    @Column(nullable = false)
    private Long timeoutSeconds = 30L;
    
    // Constructors
    public WorkflowStep() {}
    
    public WorkflowStep(String stepId, String stepName, StepType stepType, Integer stepOrder) {
        this.stepId = stepId;
        this.stepName = stepName;
        this.stepType = stepType;
        this.stepOrder = stepOrder;
        this.status = StepStatus.PENDING;
    }
    
    // Business Logic Methods
    public void startExecution() {
        if (this.status != StepStatus.PENDING) {
            throw new IllegalStateException("Step can only be started from PENDING state");
        }
        this.status = StepStatus.EXECUTING;
        this.startedAt = LocalDateTime.now();
        this.timeoutAt = LocalDateTime.now().plusSeconds(timeoutSeconds);
    }
    
    public void completeExecution(String responsePayload) {
        if (this.status != StepStatus.EXECUTING) {
            throw new IllegalStateException("Step can only be completed from EXECUTING state");
        }
        this.status = StepStatus.COMPLETED;
        this.responsePayload = responsePayload;
        this.completedAt = LocalDateTime.now();
    }
    
    public void failExecution(String errorMessage) {
        this.status = StepStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = LocalDateTime.now();
    }
    
    public void skipExecution(String reason) {
        this.status = StepStatus.SKIPPED;
        this.errorMessage = reason;
        this.completedAt = LocalDateTime.now();
    }
    
    public boolean canRetry() {
        return retryCount < maxRetries && (status == StepStatus.FAILED || status == StepStatus.TIMEOUT);
    }
    
    public void incrementRetryCount() {
        this.retryCount++;
        this.status = StepStatus.PENDING;
        this.errorMessage = null;
        this.responsePayload = null;
    }
    
    public boolean isTimeout() {
        return timeoutAt != null && LocalDateTime.now().isAfter(timeoutAt);
    }
    
    public void markTimeout() {
        this.status = StepStatus.TIMEOUT;
        this.completedAt = LocalDateTime.now();
    }
    
    public boolean isCompleted() {
        return status == StepStatus.COMPLETED;
    }
    
    public boolean isFailed() {
        return status == StepStatus.FAILED;
    }
    
    public boolean isSkipped() {
        return status == StepStatus.SKIPPED;
    }
    
    public boolean canCompensate() {
        return stepType.isCompensationSupported() && status == StepStatus.COMPLETED;
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getStepId() { return stepId; }
    public void setStepId(String stepId) { this.stepId = stepId; }
    
    public String getStepName() { return stepName; }
    public void setStepName(String stepName) { this.stepName = stepName; }
    
    public StepType getStepType() { return stepType; }
    public void setStepType(StepType stepType) { this.stepType = stepType; }
    
    public StepStatus getStatus() { return status; }
    public void setStatus(StepStatus status) { this.status = status; }
    
    public WorkflowInstance getWorkflowInstance() { return workflowInstance; }
    public void setWorkflowInstance(WorkflowInstance workflowInstance) { this.workflowInstance = workflowInstance; }
    
    public Integer getStepOrder() { return stepOrder; }
    public void setStepOrder(Integer stepOrder) { this.stepOrder = stepOrder; }
    
    public String getServiceEndpoint() { return serviceEndpoint; }
    public void setServiceEndpoint(String serviceEndpoint) { this.serviceEndpoint = serviceEndpoint; }
    
    public String getRequestPayload() { return requestPayload; }
    public void setRequestPayload(String requestPayload) { this.requestPayload = requestPayload; }
    
    public String getResponsePayload() { return responsePayload; }
    public void setResponsePayload(String responsePayload) { this.responsePayload = responsePayload; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    
    public Integer getMaxRetries() { return maxRetries; }
    public void setMaxRetries(Integer maxRetries) { this.maxRetries = maxRetries; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    
    public LocalDateTime getTimeoutAt() { return timeoutAt; }
    public void setTimeoutAt(LocalDateTime timeoutAt) { this.timeoutAt = timeoutAt; }
    
    public Long getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(Long timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}

/**
 * Types of workflow steps with their execution characteristics
 */
enum StepType {
    SERVICE_CALL("Call external service", true, true),
    NOTIFICATION("Send notification", true, false),
    DATA_TRANSFORMATION("Transform data", false, false),
    CONDITIONAL_BRANCH("Conditional logic", false, false),
    PARALLEL_GATEWAY("Parallel execution", false, false),
    TIMER_WAIT("Wait for duration", false, false),
    USER_TASK("Manual user task", true, false),
    SAGA_COMPENSATION("Compensation action", false, true);
    
    private final String description;
    private final boolean requiresExternalCall;
    private final boolean compensationSupported;
    
    StepType(String description, boolean requiresExternalCall, boolean compensationSupported) {
        this.description = description;
        this.requiresExternalCall = requiresExternalCall;
        this.compensationSupported = compensationSupported;
    }
    
    public String getDescription() { return description; }
    public boolean requiresExternalCall() { return requiresExternalCall; }
    public boolean isCompensationSupported() { return compensationSupported; }
}

/**
 * Workflow step execution states
 */
enum StepStatus {
    PENDING,      // Step is ready to execute
    EXECUTING,    // Step is currently executing
    COMPLETED,    // Step completed successfully
    FAILED,       // Step failed during execution
    SKIPPED,      // Step was skipped due to conditions
    TIMEOUT,      // Step timed out during execution
    COMPENSATED   // Step was compensated (rolled back)
} 