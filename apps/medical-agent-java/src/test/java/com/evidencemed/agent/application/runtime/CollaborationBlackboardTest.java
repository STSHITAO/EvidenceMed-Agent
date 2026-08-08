package com.evidencemed.agent.application.runtime;

import com.evidencemed.agent.infrastructure.persistence.CollaborationEventRepository;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class CollaborationBlackboardTest {
    @Test
    void rejectsRawImageBytes() {
        CollaborationBlackboard board = new CollaborationBlackboard("trace-1",
                mock(CollaborationEventRepository.class));

        assertThatThrownBy(() -> board.publish("agent", "image", new byte[]{1, 2}, "raw"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("原始影像");
    }

    @Test
    void releasesTaskOnlyAfterDependenciesComplete() {
        CollaborationBlackboard board = new CollaborationBlackboard("trace-1",
                mock(CollaborationEventRepository.class));
        board.createTask(AgentTask.system("memory", AgentTaskType.LOAD_CASE_MEMORY, Set.of(), 10, 0));
        board.createTask(AgentTask.agent("answer", AgentTaskType.GENERATE_RESPONSE,
                AgentCapability.MEDICAL_REASONING, Set.of("memory"), 5, 1));

        assertThat(board.readyTasks()).extracting(AgentTask::id).containsExactly("memory");
        board.claimTask("memory", "CaseMemoryService");
        board.completeTask("memory", "CaseMemoryService", "done");

        assertThat(board.readyTasks()).extracting(AgentTask::id).containsExactly("answer");
    }
}
