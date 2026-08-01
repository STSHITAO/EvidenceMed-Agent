package com.evidencemed.agent.domain.report;

import com.evidencemed.agent.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "medical_report", indexes = @Index(name = "idx_report_session", columnList = "sessionId"))
public class MedicalReport extends BaseEntity {
    @Column(nullable = false, length = 36)
    private String sessionId;

    @Column(nullable = false, length = 36)
    private String runId;

    @Lob
    @Column(nullable = false)
    private String answer;

    @Lob
    private String evidenceJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RiskLevel riskLevel;

    @Column(nullable = false)
    private boolean humanReviewRequired;

    protected MedicalReport() {}

    public MedicalReport(String sessionId, String runId, String answer, String evidenceJson,
                         RiskLevel riskLevel, boolean humanReviewRequired) {
        this.sessionId = sessionId;
        this.runId = runId;
        this.answer = answer;
        this.evidenceJson = evidenceJson;
        this.riskLevel = riskLevel;
        this.humanReviewRequired = humanReviewRequired;
    }

    public String getSessionId() { return sessionId; }
    public String getRunId() { return runId; }
    public String getAnswer() { return answer; }
    public String getEvidenceJson() { return evidenceJson; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public boolean isHumanReviewRequired() { return humanReviewRequired; }
}
