package com.evidencemed.agent.infrastructure.persistence;

import com.evidencemed.agent.domain.trace.AgentStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentStepRepository extends JpaRepository<AgentStep, String> {
    List<AgentStep> findByTraceIdOrderByStartedAt(String traceId);
}
