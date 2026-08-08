package com.evidencemed.agent.application.runtime;

import java.util.Objects;
import java.util.Set;

public record AgentTask(
        String id,
        AgentTaskType type,
        TaskExecutorKind executorKind,
        AgentCapability requiredCapability,
        Set<String> dependencies,
        int priority,
        int round
) {
    public AgentTask {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(executorKind, "executorKind");
        dependencies = dependencies == null ? Set.of() : Set.copyOf(dependencies);
        if (executorKind == TaskExecutorKind.AGENT && requiredCapability == null) {
            throw new IllegalArgumentException("Agent task requires a capability");
        }
    }

    public static AgentTask system(String id, AgentTaskType type, Set<String> dependencies,
                                   int priority, int round) {
        return new AgentTask(id, type, TaskExecutorKind.SYSTEM, null, dependencies, priority, round);
    }

    public static AgentTask agent(String id, AgentTaskType type, AgentCapability capability,
                                  Set<String> dependencies, int priority, int round) {
        return new AgentTask(id, type, TaskExecutorKind.AGENT, capability, dependencies, priority, round);
    }
}
