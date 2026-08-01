package com.evidencemed.agent.domain.knowledge;

public record RetrievedEvidence(
        String chunkId,
        String documentId,
        String source,
        int chunkIndex,
        String content,
        String modality,
        double score,
        String retrievalMethod
) {
    public RetrievedEvidence withScore(double newScore, String method) {
        return new RetrievedEvidence(chunkId, documentId, source, chunkIndex, content,
                modality, newScore, method);
    }
}
