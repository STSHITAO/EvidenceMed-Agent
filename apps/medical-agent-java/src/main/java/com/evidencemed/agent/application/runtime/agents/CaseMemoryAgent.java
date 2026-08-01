package com.evidencemed.agent.application.runtime.agents;

import com.evidencemed.agent.application.memory.CaseMemoryService;
import com.evidencemed.agent.application.runtime.AgentContext;
import com.evidencemed.agent.application.runtime.MedicalAgent;
import org.springframework.stereotype.Component;

@Component
public class CaseMemoryAgent implements MedicalAgent {
    private final CaseMemoryService memory;
    public CaseMemoryAgent(CaseMemoryService memory) { this.memory = memory; }
    @Override public String name() { return "CaseMemoryAgent"; }
    @Override public void execute(AgentContext context) {
        context.setMemory(memory.load(context.getOwnerId(), context.getSessionId()));
        context.getBlackboard().publish(name(), "caseMemory", context.getMemory(),
                "loaded " + context.getMemory().recentMessages().size() + " messages");
    }
}
