package com.eipresso.orchestration.repository;

import com.eipresso.orchestration.model.WorkflowInstance;
import com.eipresso.orchestration.model.WorkflowStatus;
import com.eipresso.orchestration.model.WorkflowType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for WorkflowInstance entities with advanced queries
 * Supports Process Manager pattern with workflow state tracking
 */
@Repository
public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, Long> {
    
    // Basic workflow queries
    Optional<WorkflowInstance> findByWorkflowId(String workflowId);
    Optional<WorkflowInstance> findByCorrelationId(String correlationId);
    List<WorkflowInstance> findByStatus(WorkflowStatus status);
    List<WorkflowInstance> findByWorkflowType(WorkflowType workflowType);
    
    // Status-based queries for workflow monitoring
    List<WorkflowInstance> findByStatusIn(List<WorkflowStatus> statuses);
    
    @Query("SELECT w FROM WorkflowInstance w WHERE w.status = 'PROCESSING' AND w.updatedAt < :threshold")
    List<WorkflowInstance> findStaleProcessingWorkflows(@Param("threshold") LocalDateTime threshold);
    
    @Query("SELECT w FROM WorkflowInstance w WHERE w.timeoutAt IS NOT NULL AND w.timeoutAt < :now AND w.status IN ('PROCESSING', 'WAITING')")
    List<WorkflowInstance> findTimedOutWorkflows(@Param("now") LocalDateTime now);
    
    // Workflow analytics queries
    @Query("SELECT COUNT(w) FROM WorkflowInstance w WHERE w.workflowType = :type AND w.status = 'COMPLETED' AND w.completedAt >= :startDate")
    Long countCompletedWorkflowsByType(@Param("type") WorkflowType type, @Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT COUNT(w) FROM WorkflowInstance w WHERE w.workflowType = :type AND w.status = 'FAILED' AND w.completedAt >= :startDate")
    Long countFailedWorkflowsByType(@Param("type") WorkflowType type, @Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT AVG(TIMESTAMPDIFF(SECOND, w.startedAt, w.completedAt)) FROM WorkflowInstance w WHERE w.workflowType = :type AND w.status = 'COMPLETED' AND w.completedAt >= :startDate")
    Double getAverageExecutionTimeByType(@Param("type") WorkflowType type, @Param("startDate") LocalDateTime startDate);
    
    // Compensation and error handling queries
    @Query("SELECT w FROM WorkflowInstance w WHERE w.status = 'FAILED' AND SIZE(w.compensationActions) > 0 AND w.completedAt >= :since")
    List<WorkflowInstance> findFailedWorkflowsRequiringCompensation(@Param("since") LocalDateTime since);
    
    @Query("SELECT w FROM WorkflowInstance w WHERE w.status = 'COMPENSATING' ORDER BY w.updatedAt ASC")
    List<WorkflowInstance> findWorkflowsCurrentlyCompensating();
    
    // Workflow correlation and dependency tracking
    @Query("SELECT w FROM WorkflowInstance w WHERE w.correlationId = :correlationId ORDER BY w.startedAt")
    List<WorkflowInstance> findWorkflowsByCorrelationIdOrdered(@Param("correlationId") String correlationId);
    
    // Operational monitoring queries
    @Query("SELECT w FROM WorkflowInstance w WHERE w.status IN ('PROCESSING', 'WAITING') ORDER BY w.startedAt")
    List<WorkflowInstance> findActiveWorkflows();
    
    @Query("SELECT COUNT(w) FROM WorkflowInstance w WHERE w.status IN ('PROCESSING', 'WAITING')")
    Long countActiveWorkflows();
    
    @Query("SELECT w.workflowType, COUNT(w) FROM WorkflowInstance w WHERE w.status IN ('PROCESSING', 'WAITING') GROUP BY w.workflowType")
    List<Object[]> countActiveWorkflowsByType();
    
    // Business metrics queries
    @Query("SELECT DATE(w.startedAt), COUNT(w) FROM WorkflowInstance w WHERE w.startedAt >= :startDate GROUP BY DATE(w.startedAt) ORDER BY DATE(w.startedAt)")
    List<Object[]> getDailyWorkflowCounts(@Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT w.workflowType, w.status, COUNT(w) FROM WorkflowInstance w WHERE w.startedAt >= :startDate GROUP BY w.workflowType, w.status")
    List<Object[]> getWorkflowStatusDistribution(@Param("startDate") LocalDateTime startDate);
    
    // Custom variable queries
    @Query("SELECT w FROM WorkflowInstance w JOIN w.variables v WHERE KEY(v) = :variableName AND VALUE(v) = :variableValue")
    List<WorkflowInstance> findByVariable(@Param("variableName") String variableName, @Param("variableValue") String variableValue);
    
    @Query("SELECT w FROM WorkflowInstance w JOIN w.variables v WHERE KEY(v) = :variableName")
    List<WorkflowInstance> findByVariableName(@Param("variableName") String variableName);
} 