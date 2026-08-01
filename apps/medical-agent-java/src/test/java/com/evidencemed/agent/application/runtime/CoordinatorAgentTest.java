package com.evidencemed.agent.application.runtime;

import com.evidencemed.agent.domain.trace.AgentStep;
import com.evidencemed.agent.infrastructure.persistence.AgentStepRepository;
import com.evidencemed.agent.infrastructure.persistence.CollaborationEventRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CoordinatorAgentTest {
    @Test
    void executesResponseBeforeFinalSafetyReview() {
        List<String> execution = new ArrayList<>();
        AgentStepRepository steps = mock(AgentStepRepository.class);
        when(steps.save(any(AgentStep.class))).thenAnswer(call -> call.getArgument(0));
        List<MedicalAgent> agents = List.of(
                agent("SafetyReviewAgent", execution), agent("MedicalResponseAgent", execution),
                agent("EvidenceRetrieverAgent", execution), agent("ClinicalRouterAgent", execution),
                agent("CaseMemoryAgent", execution));
        AgentContext context = new AgentContext("trace", "owner", "session", "问题",
                null, null, new CollaborationBlackboard("trace", mock(CollaborationEventRepository.class)));

        new CoordinatorAgent(agents, steps).run(context);

        assertThat(execution).containsExactly("CaseMemoryAgent", "ClinicalRouterAgent",
                "EvidenceRetrieverAgent", "MedicalResponseAgent", "SafetyReviewAgent");
    }

    private MedicalAgent agent(String name, List<String> execution) {
        return new MedicalAgent() {
            public String name() { return name; }
            public void execute(AgentContext context) { execution.add(name); }
        };
    }
}
