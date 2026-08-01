package com.evidencemed.agent.application.model;

import com.evidencemed.agent.domain.knowledge.RetrievedEvidence;

import java.util.List;

public interface RerankerModel {
    List<RetrievedEvidence> rerank(String query, List<RetrievedEvidence> candidates, int topK);
}
