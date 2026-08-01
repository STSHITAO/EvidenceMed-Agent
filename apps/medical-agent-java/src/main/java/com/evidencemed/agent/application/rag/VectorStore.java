package com.evidencemed.agent.application.rag;

import java.util.List;

public interface VectorStore {
    void upsert(List<VectorRecord> records);
    List<VectorHit> search(List<Float> vector, int topK);

    record VectorRecord(String chunkId, String documentId, List<Float> vector) {
        public VectorRecord { vector = List.copyOf(vector); }
    }

    record VectorHit(String chunkId, String documentId, double score) {}
}
