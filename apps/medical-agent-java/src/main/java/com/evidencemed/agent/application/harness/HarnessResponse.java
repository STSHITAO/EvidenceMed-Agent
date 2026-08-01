package com.evidencemed.agent.application.harness;

import com.evidencemed.agent.domain.knowledge.RetrievedEvidence;
import com.evidencemed.agent.domain.report.RiskLevel;

import java.util.List;

public record HarnessResponse(String sessionId, String reportId, String traceId, String answer,
                              RiskLevel riskLevel, boolean humanReviewRequired,
                              List<String> safetyReasons, List<RetrievedEvidence> evidence,
                              List<String> degradations) {
}
