package com.evidencemed.agent.domain.trace;

import com.evidencemed.agent.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "collaboration_event", indexes = @Index(name = "idx_event_trace", columnList = "traceId"))
public class CollaborationEvent extends BaseEntity {
    @Column(nullable = false, length = 36) private String traceId;
    @Column(nullable = false) private long sequenceNumber;
    @Column(nullable = false, length = 80) private String agentName;
    @Column(nullable = false, length = 40) private String eventType;
    @Column(nullable = false, length = 120) private String artifactName;
    @Column(length = 300) private String summary;

    protected CollaborationEvent() {}
    public CollaborationEvent(String traceId, long sequenceNumber, String agentName,
                              String eventType, String artifactName, String summary) {
        this.traceId = traceId;
        this.sequenceNumber = sequenceNumber;
        this.agentName = agentName;
        this.eventType = eventType;
        this.artifactName = artifactName;
        this.summary = summary == null ? null : summary.substring(0, Math.min(300, summary.length()));
    }
    public String getTraceId() { return traceId; }
    public long getSequenceNumber() { return sequenceNumber; }
    public String getAgentName() { return agentName; }
    public String getEventType() { return eventType; }
    public String getArtifactName() { return artifactName; }
    public String getSummary() { return summary; }
}
