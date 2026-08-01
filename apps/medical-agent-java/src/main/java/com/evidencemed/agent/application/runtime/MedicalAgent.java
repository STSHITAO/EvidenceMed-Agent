package com.evidencemed.agent.application.runtime;

public interface MedicalAgent {
    String name();
    void execute(AgentContext context);
}
