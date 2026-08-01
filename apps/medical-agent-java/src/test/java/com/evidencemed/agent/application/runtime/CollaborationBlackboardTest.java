package com.evidencemed.agent.application.runtime;

import com.evidencemed.agent.infrastructure.persistence.CollaborationEventRepository;
import org.junit.jupiter.api.Test;

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
}
