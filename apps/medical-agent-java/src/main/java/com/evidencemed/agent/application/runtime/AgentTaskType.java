package com.evidencemed.agent.application.runtime;

public enum AgentTaskType {
    LOAD_CASE_MEMORY,
    RETRIEVE_EVIDENCE,
    PLAN_EVIDENCE_RETRY,
    RETRY_EVIDENCE,
    GENERATE_RESPONSE,
    REVISE_RESPONSE,
    REVIEW_SAFETY,
    APPLY_REVIEW_HOLD
}
