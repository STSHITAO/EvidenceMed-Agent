package com.evidencemed.agent.domain.trace;

import com.evidencemed.agent.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "agent_run_trace", indexes = @Index(name = "idx_trace_session", columnList = "sessionId"))
public class AgentRunTrace extends BaseEntity {
    @Column(nullable = false, length = 36) private String sessionId;
    @Column(nullable = false, length = 36) private String userId;
    @Column(nullable = false, length = 64) private String inputHash;
    @Column(nullable = false) private boolean imagePresent;
    @Column(length = 100) private String imageMediaType;
    private Long imageSizeBytes;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private RunStatus status;
    @Column(length = 80) private String errorCode;
    private Instant finishedAt;

    protected AgentRunTrace() {}

    public AgentRunTrace(String sessionId, String userId, String inputHash,
                         String imageMediaType, Long imageSizeBytes) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.inputHash = inputHash;
        this.imagePresent = imageSizeBytes != null && imageSizeBytes > 0;
        this.imageMediaType = imageMediaType;
        this.imageSizeBytes = imageSizeBytes;
        this.status = RunStatus.RUNNING;
    }

    public void finish(RunStatus status, String errorCode) {
        this.status = status;
        this.errorCode = errorCode;
        this.finishedAt = Instant.now();
    }

    public String getSessionId() { return sessionId; }
    public String getUserId() { return userId; }
    public String getInputHash() { return inputHash; }
    public boolean isImagePresent() { return imagePresent; }
    public String getImageMediaType() { return imageMediaType; }
    public Long getImageSizeBytes() { return imageSizeBytes; }
    public RunStatus getStatus() { return status; }
    public String getErrorCode() { return errorCode; }
    public Instant getFinishedAt() { return finishedAt; }
}
