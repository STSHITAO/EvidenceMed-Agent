package com.evidencemed.agent.infrastructure.persistence;

import com.evidencemed.agent.domain.trace.CollaborationEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CollaborationEventRepository extends JpaRepository<CollaborationEvent, String> {
    List<CollaborationEvent> findByTraceIdOrderBySequenceNumber(String traceId);
}
