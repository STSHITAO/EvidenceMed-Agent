package com.evidencemed.agent.application.runtime;

import com.evidencemed.agent.application.memory.CaseMemorySnapshot;
import com.evidencemed.agent.application.rag.RagResult;
import com.evidencemed.agent.domain.report.RiskLevel;

import java.util.List;

public record AgentRuntimeView(
        String traceId,
        String ownerId,
        String sessionId,
        String question,
        byte[] image,
        String imageMediaType,
        CaseMemorySnapshot memory,
        ClinicalRoute route,
        RagResult rag,
        boolean evidenceRequired,
        String plannedRetrievalQuery,
        RiskLevel riskLevel,
        boolean humanReviewRequired,
        List<String> safetyReasons,
        String answer,
        int revisionCount
) {
    public AgentRuntimeView {
        image = image == null ? null : image.clone();
        safetyReasons = safetyReasons == null ? List.of() : List.copyOf(safetyReasons);
    }

    @Override
    public byte[] image() {
        return image == null ? null : image.clone();
    }
}
