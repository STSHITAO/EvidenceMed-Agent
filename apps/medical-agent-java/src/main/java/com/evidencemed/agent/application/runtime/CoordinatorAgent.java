package com.evidencemed.agent.application.runtime;

import com.evidencemed.agent.domain.trace.AgentStep;
import com.evidencemed.agent.domain.trace.RunStatus;
import com.evidencemed.agent.infrastructure.persistence.AgentStepRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CoordinatorAgent {
    private final List<MedicalAgent> agents;
    private final AgentStepRepository steps;

    public CoordinatorAgent(List<MedicalAgent> agents, AgentStepRepository steps) {
        this.agents = agents.stream().sorted(java.util.Comparator.comparingInt(this::order)).toList();
        this.steps = steps;
    }

    public void run(AgentContext context) {
        for (MedicalAgent agent : agents) {
            context.getBlackboard().claim("execute:" + agent.name(), agent.name());
            AgentStep step = steps.save(new AgentStep(context.getTraceId(), agent.name()));
            try {
                agent.execute(context);
                step.finish(RunStatus.SUCCEEDED, "completed");
            } catch (RuntimeException exception) {
                step.finish(RunStatus.FAILED, exception.getClass().getSimpleName());
                steps.save(step);
                throw exception;
            }
            steps.save(step);
        }
    }

    private int order(MedicalAgent agent) {
        return switch (agent.name()) {
            case "CaseMemoryAgent" -> 10;
            case "ClinicalRouterAgent" -> 20;
            case "EvidenceRetrieverAgent" -> 30;
            case "MedicalResponseAgent" -> 40;
            case "SafetyReviewAgent" -> 50;
            default -> 100;
        };
    }
}
