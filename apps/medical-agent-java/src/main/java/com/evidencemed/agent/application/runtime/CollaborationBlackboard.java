package com.evidencemed.agent.application.runtime;

import com.evidencemed.agent.domain.trace.CollaborationEvent;
import com.evidencemed.agent.infrastructure.persistence.CollaborationEventRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class CollaborationBlackboard {
    private final String traceId;
    private final CollaborationEventRepository events;
    private final AtomicLong sequence = new AtomicLong();
    private final Map<String, Object> artifacts = new LinkedHashMap<>();
    private final Map<String, String> claims = new LinkedHashMap<>();

    public CollaborationBlackboard(String traceId, CollaborationEventRepository events) {
        this.traceId = traceId;
        this.events = events;
    }

    public synchronized void claim(String task, String agent) {
        String existing = claims.putIfAbsent(task, agent);
        if (existing != null && !existing.equals(agent)) {
            throw new IllegalStateException("协作任务已被其他 Agent claim: " + task);
        }
        events.save(new CollaborationEvent(traceId, sequence.incrementAndGet(), agent,
                "TASK_CLAIMED", task, "claimed"));
    }

    public synchronized void publish(String agent, String artifact, Object value, String summary) {
        if (value instanceof byte[]) {
            throw new IllegalArgumentException("协作黑板禁止保存原始影像字节");
        }
        artifacts.put(artifact, value);
        events.save(new CollaborationEvent(traceId, sequence.incrementAndGet(), agent,
                "ARTIFACT_PUBLISHED", artifact, summary));
    }

    public synchronized Map<String, Object> snapshot() { return Map.copyOf(artifacts); }
}
