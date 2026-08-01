package com.evidencemed.agent.domain.trace;

import com.evidencemed.agent.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "agent_step", indexes = @Index(name = "idx_step_trace", columnList = "traceId"))
public class AgentStep extends BaseEntity {
    @Column(nullable = false, length = 36) private String traceId;
    @Column(nullable = false, length = 80) private String agentName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private RunStatus status;
    @Column(length = 300) private String detail;
    @Column(nullable = false, updatable = false) private Instant startedAt;
    private Instant finishedAt;
    private Long durationMillis;

    protected AgentStep() {}
    public AgentStep(String traceId, String agentName) {
        this.traceId = traceId;
        this.agentName = agentName;
        this.status = RunStatus.RUNNING;
        this.startedAt = Instant.now();
    }
    public void finish(RunStatus status, String detail) {
        this.status = status;
        this.detail = detail == null ? null : detail.substring(0, Math.min(300, detail.length()));
        this.finishedAt = Instant.now();
        this.durationMillis = Duration.between(startedAt, finishedAt).toMillis();
    }
    public String getTraceId() { return traceId; }
    public String getAgentName() { return agentName; }
    public RunStatus getStatus() { return status; }
    public String getDetail() { return detail; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Long getDurationMillis() { return durationMillis; }
}
