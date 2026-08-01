package com.evidencemed.agent.infrastructure.persistence;

import com.evidencemed.agent.domain.trace.AgentRunTrace;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRunTraceRepository extends JpaRepository<AgentRunTrace, String> {
}
