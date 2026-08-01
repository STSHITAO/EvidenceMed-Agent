package com.evidencemed.agent.infrastructure.persistence;

import com.evidencemed.agent.domain.knowledge.KnowledgeChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, String> {
    List<KnowledgeChunk> findByDocumentIdOrderByChunkIndex(String documentId);
    void deleteByDocumentId(String documentId);
}
