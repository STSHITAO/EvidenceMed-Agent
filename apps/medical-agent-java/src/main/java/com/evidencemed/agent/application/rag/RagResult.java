package com.evidencemed.agent.application.rag;

import com.evidencemed.agent.domain.knowledge.RetrievedEvidence;

import java.util.List;

public record RagResult(String hydeText, List<RetrievedEvidence> evidence, List<String> degradations) {}
