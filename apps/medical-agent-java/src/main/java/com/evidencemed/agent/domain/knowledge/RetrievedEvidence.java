package com.evidencemed.agent.domain.knowledge;

public record RetrievedEvidence(
        String chunkId,
        String documentId,
        String source,
        int chunkIndex,
        String content,
        String modality,
        double score,
        String retrievalMethod,
        String sectionPath,
        int pageFrom,
        int pageTo,
        String objectType
) {
    public RetrievedEvidence(String chunkId, String documentId, String source, int chunkIndex,
            String content, String modality, double score, String retrievalMethod) {
        this(chunkId, documentId, source, chunkIndex, content, modality, score, retrievalMethod,
                "", 1, 1, "PARAGRAPH");
    }

    public RetrievedEvidence withScore(double newScore, String method) {
        return new RetrievedEvidence(chunkId, documentId, source, chunkIndex, content,
                modality, newScore, method, sectionPath, pageFrom, pageTo, objectType);
    }
}
