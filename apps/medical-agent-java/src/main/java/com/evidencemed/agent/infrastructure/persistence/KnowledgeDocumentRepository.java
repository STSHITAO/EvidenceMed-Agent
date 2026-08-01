package com.evidencemed.agent.infrastructure.persistence;

import com.evidencemed.agent.domain.knowledge.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, String> {
    Optional<KnowledgeDocument> findBySha256(String sha256);
}
