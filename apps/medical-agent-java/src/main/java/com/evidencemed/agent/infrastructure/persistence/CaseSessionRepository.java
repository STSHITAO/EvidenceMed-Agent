package com.evidencemed.agent.infrastructure.persistence;

import com.evidencemed.agent.domain.casefile.CaseSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CaseSessionRepository extends JpaRepository<CaseSession, String> {
    Optional<CaseSession> findByIdAndOwnerId(String id, String ownerId);
}
