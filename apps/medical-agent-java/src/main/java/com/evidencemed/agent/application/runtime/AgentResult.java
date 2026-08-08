package com.evidencemed.agent.application.runtime;

import com.evidencemed.agent.application.memory.CaseMemorySnapshot;
import com.evidencemed.agent.application.rag.RagResult;
import com.evidencemed.agent.domain.report.RiskLevel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AgentResult {
    private final CaseMemorySnapshot memory;
    private final RagResult rag;
    private final String answer;
    private final String retrievalQuery;
    private final RiskLevel riskLevel;
    private final Boolean humanReviewRequired;
    private final List<String> safetyReasons;
    private final boolean safetyApproved;
    private final boolean revisionRequired;
    private final Map<String, Object> auditArtifacts;
    private final String summary;

    private AgentResult(Builder builder) {
        memory = builder.memory;
        rag = builder.rag;
        answer = builder.answer;
        retrievalQuery = builder.retrievalQuery;
        riskLevel = builder.riskLevel;
        humanReviewRequired = builder.humanReviewRequired;
        safetyReasons = builder.safetyReasons == null ? null : List.copyOf(builder.safetyReasons);
        safetyApproved = builder.safetyApproved;
        revisionRequired = builder.revisionRequired;
        auditArtifacts = Map.copyOf(builder.auditArtifacts);
        summary = builder.summary;
    }

    public static Builder builder(String summary) { return new Builder(summary); }
    public CaseMemorySnapshot memory() { return memory; }
    public RagResult rag() { return rag; }
    public String answer() { return answer; }
    public String retrievalQuery() { return retrievalQuery; }
    public RiskLevel riskLevel() { return riskLevel; }
    public Boolean humanReviewRequired() { return humanReviewRequired; }
    public List<String> safetyReasons() { return safetyReasons; }
    public boolean safetyApproved() { return safetyApproved; }
    public boolean revisionRequired() { return revisionRequired; }
    public Map<String, Object> auditArtifacts() { return auditArtifacts; }
    public String summary() { return summary; }

    public static final class Builder {
        private CaseMemorySnapshot memory;
        private RagResult rag;
        private String answer;
        private String retrievalQuery;
        private RiskLevel riskLevel;
        private Boolean humanReviewRequired;
        private List<String> safetyReasons;
        private boolean safetyApproved;
        private boolean revisionRequired;
        private final Map<String, Object> auditArtifacts = new LinkedHashMap<>();
        private final String summary;

        private Builder(String summary) { this.summary = summary; }
        public Builder memory(CaseMemorySnapshot value) { memory = value; return this; }
        public Builder rag(RagResult value) { rag = value; return this; }
        public Builder answer(String value) { answer = value; return this; }
        public Builder retrievalQuery(String value) { retrievalQuery = value; return this; }
        public Builder riskLevel(RiskLevel value) { riskLevel = value; return this; }
        public Builder humanReviewRequired(boolean value) { humanReviewRequired = value; return this; }
        public Builder safetyReasons(List<String> value) { safetyReasons = value; return this; }
        public Builder safetyApproved(boolean value) { safetyApproved = value; return this; }
        public Builder revisionRequired(boolean value) { revisionRequired = value; return this; }
        public Builder auditArtifact(String name, Object value) {
            if (value != null) auditArtifacts.put(name, value);
            return this;
        }
        public AgentResult build() { return new AgentResult(this); }
    }
}
