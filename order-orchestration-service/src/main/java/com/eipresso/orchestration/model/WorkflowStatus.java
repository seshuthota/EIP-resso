package com.eipresso.orchestration.model;

/**
 * Workflow execution states following finite state machine pattern
 */
public enum WorkflowStatus {
    CREATED,      // Workflow instance created but not started
    STARTED,      // Workflow execution has begun
    PROCESSING,   // Currently executing workflow steps
    WAITING,      // Waiting for external service response
    COMPLETED,    // Successfully completed all steps
    FAILED,       // Failed during execution
    COMPENSATING, // Running compensation actions
    COMPENSATED,  // Compensation completed
    TIMEOUT       // Workflow timed out
} 