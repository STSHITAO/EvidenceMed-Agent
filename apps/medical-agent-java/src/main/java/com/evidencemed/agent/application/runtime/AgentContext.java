package com.evidencemed.agent.application.runtime;

import com.evidencemed.agent.application.memory.CaseMemorySnapshot;
import com.evidencemed.agent.application.rag.RagResult;
import com.evidencemed.agent.domain.report.RiskLevel;

import java.util.List;

public class AgentContext {
    private final String traceId;
    private final String ownerId;
    private final String sessionId;
    private final String question;
    private final byte[] image;
    private final String imageMediaType;
    private final CollaborationBlackboard blackboard;
    private CaseMemorySnapshot memory = CaseMemorySnapshot.empty();
    private ClinicalRoute route = ClinicalRoute.GENERAL;
    private RagResult rag = new RagResult("", java.util.List.of(), java.util.List.of());
    private boolean evidenceRequired = true;
    private String plannedRetrievalQuery;
    private RiskLevel riskLevel = RiskLevel.LOW;
    private boolean humanReviewRequired;
    private List<String> safetyReasons = List.of();
    private String answer;
    private boolean safetyApproved;
    private boolean revisionRequired;
    private int revisionCount;

    public AgentContext(String traceId, String ownerId, String sessionId, String question,
                        byte[] image, String imageMediaType, CollaborationBlackboard blackboard) {
        this.traceId = traceId;
        this.ownerId = ownerId;
        this.sessionId = sessionId;
        this.question = question;
        this.image = image == null ? null : image.clone();
        this.imageMediaType = imageMediaType;
        this.blackboard = blackboard;
    }
    public String getTraceId() { return traceId; }
    public String getOwnerId() { return ownerId; }
    public String getSessionId() { return sessionId; }
    public String getQuestion() { return question; }
    public byte[] getImage() { return image == null ? null : image.clone(); }
    public String getImageMediaType() { return imageMediaType; }
    public CollaborationBlackboard getBlackboard() { return blackboard; }
    public CaseMemorySnapshot getMemory() { return memory; }
    public void setMemory(CaseMemorySnapshot memory) { this.memory = memory; }
    public ClinicalRoute getRoute() { return route; }
    public void setRoute(ClinicalRoute route) { this.route = route; }
    public RagResult getRag() { return rag; }
    public void setRag(RagResult rag) { this.rag = rag; }
    public boolean isEvidenceRequired() { return evidenceRequired; }
    public void setEvidenceRequired(boolean value) { evidenceRequired = value; }
    public String getPlannedRetrievalQuery() { return plannedRetrievalQuery; }
    public void setPlannedRetrievalQuery(String value) { plannedRetrievalQuery = value; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public void setRiskLevel(RiskLevel riskLevel) { this.riskLevel = riskLevel; }
    public boolean isHumanReviewRequired() { return humanReviewRequired; }
    public void setHumanReviewRequired(boolean value) { this.humanReviewRequired = value; }
    public List<String> getSafetyReasons() { return safetyReasons; }
    public void setSafetyReasons(List<String> reasons) { this.safetyReasons = List.copyOf(reasons); }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public boolean isSafetyApproved() { return safetyApproved; }
    public void setSafetyApproved(boolean value) { safetyApproved = value; }
    public boolean isRevisionRequired() { return revisionRequired; }
    public void setRevisionRequired(boolean value) { revisionRequired = value; }
    public int getRevisionCount() { return revisionCount; }
    public void incrementRevisionCount() { revisionCount++; }

    public AgentRuntimeView snapshot() {
        return new AgentRuntimeView(traceId, ownerId, sessionId, question, image, imageMediaType,
                memory, route, rag, evidenceRequired, plannedRetrievalQuery, riskLevel,
                humanReviewRequired, safetyReasons, answer, revisionCount);
    }
}
