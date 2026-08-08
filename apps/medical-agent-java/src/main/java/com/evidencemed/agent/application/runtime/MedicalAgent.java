package com.evidencemed.agent.application.runtime;

import java.util.Set;

public interface MedicalAgent {
    String name();
    Set<AgentCapability> capabilities();

    default int score(AgentTask task, AgentRuntimeView context) {
        return capabilities().contains(task.requiredCapability()) ? 100 : -1;
    }

    AgentResult execute(AgentTask task, AgentRuntimeView context);
}
