package com.eipresso.orchestration.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WorkflowInstance represents a running business process workflow
 * Implements Process Manager pattern for stateful workflow coordination
 */
@Entity
@Table(name = "workflow_instances")
public class WorkflowInstance {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotNull
    @Size(max = 100)
    @Column(unique = true, nullable = false)
    private String workflowId;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkflowType workflowType;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkflowStatus status;
    
    @NotNull
    @Column(nullable = false)
    private String correlationId;
    
    @Column(length = 2000)
    private String businessContext;
    
    @ElementCollection
    @CollectionTable(
        name = "workflow_variables",
        joinColumns = @JoinColumn(name = "workflow_instance_id")
    )
    @MapKeyColumn(name = "variable_name")
    @Column(name = "variable_value", length = 1000)
    private Map<String, String> variables = new HashMap<>();
    
    @OneToMany(mappedBy = "workflowInstance", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<WorkflowStep> steps = new ArrayList<>();
    
    @OneToMany(mappedBy = "workflowInstance", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<CompensationAction> compensationActions = new ArrayList<>();
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime startedAt;
    
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    private LocalDateTime completedAt;
    
    private LocalDateTime timeoutAt;
    
    @Column(length = 1000)
    private String errorMessage;
    
    @Column(length = 500)
    private String currentStepId;
    
    // Constructors
    public WorkflowInstance() {}
    
    public WorkflowInstance(String workflowId, WorkflowType workflowType, String correlationId) {
        this.workflowId = workflowId;
        this.workflowType = workflowType;
        this.correlationId = correlationId;
        this.status = WorkflowStatus.STARTED;
        this.businessContext = workflowType.getDescription();
    }
    
    // Business Logic Methods
    public void startWorkflow() {
        if (this.status != WorkflowStatus.CREATED) {
            throw new IllegalStateException("Workflow can only be started from CREATED state");
        }
        this.status = WorkflowStatus.STARTED;
        this.startedAt = LocalDateTime.now();
    }
    
    public void completeWorkflow() {
        if (this.status != WorkflowStatus.PROCESSING) {
            throw new IllegalStateException("Workflow can only be completed from PROCESSING state");
        }
        this.status = WorkflowStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }
    
    public void failWorkflow(String errorMessage) {
        this.status = WorkflowStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = LocalDateTime.now();
    }
    
    public void compensateWorkflow() {
        if (this.status != WorkflowStatus.FAILED) {
            throw new IllegalStateException("Workflow can only be compensated from FAILED state");
        }
        this.status = WorkflowStatus.COMPENSATING;
    }
    
    public void addStep(WorkflowStep step) {
        steps.add(step);
        step.setWorkflowInstance(this);
    }
    
    public void addCompensationAction(CompensationAction action) {
        compensationActions.add(action);
        action.setWorkflowInstance(this);
    }
    
    public void setVariable(String key, String value) {
        variables.put(key, value);
    }
    
    public String getVariable(String key) {
        return variables.get(key);
    }
    
    public boolean isCompleted() {
        return status == WorkflowStatus.COMPLETED;
    }
    
    public boolean isFailed() {
        return status == WorkflowStatus.FAILED;
    }
    
    public boolean requiresCompensation() {
        return status == WorkflowStatus.COMPENSATING;
    }
    
    public boolean isTimeout() {
        return timeoutAt != null && LocalDateTime.now().isAfter(timeoutAt);
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }
    
    public WorkflowType getWorkflowType() { return workflowType; }
    public void setWorkflowType(WorkflowType workflowType) { this.workflowType = workflowType; }
    
    public WorkflowStatus getStatus() { return status; }
    public void setStatus(WorkflowStatus status) { this.status = status; }
    
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    
    public String getBusinessContext() { return businessContext; }
    public void setBusinessContext(String businessContext) { this.businessContext = businessContext; }
    
    public Map<String, String> getVariables() { return variables; }
    public void setVariables(Map<String, String> variables) { this.variables = variables; }
    
    public List<WorkflowStep> getSteps() { return steps; }
    public void setSteps(List<WorkflowStep> steps) { this.steps = steps; }
    
    public List<CompensationAction> getCompensationActions() { return compensationActions; }
    public void setCompensationActions(List<CompensationAction> compensationActions) { this.compensationActions = compensationActions; }
    
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    
    public LocalDateTime getTimeoutAt() { return timeoutAt; }
    public void setTimeoutAt(LocalDateTime timeoutAt) { this.timeoutAt = timeoutAt; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public String getCurrentStepId() { return currentStepId; }
    public void setCurrentStepId(String currentStepId) { this.currentStepId = currentStepId; }
}

 