package com.evidencemed.agent.application.runtime.agents;

import com.evidencemed.agent.application.rag.RagResult;
import com.evidencemed.agent.application.runtime.AgentCapability;
import com.evidencemed.agent.application.runtime.AgentContext;
import com.evidencemed.agent.application.runtime.AgentTask;
import com.evidencemed.agent.application.runtime.AgentTaskType;
import com.evidencemed.agent.application.runtime.CollaborationBlackboard;
import com.evidencemed.agent.application.skill.MedicalSkillRegistry;
import com.evidencemed.agent.domain.report.RiskLevel;
import com.evidencemed.agent.infrastructure.persistence.CollaborationEventRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SafetyCriticAgentTest {
    @Test
    void highRiskWithoutEvidenceSuppressesModelDraftUntilHumanReview() {
        MedicalSkillRegistry skills = mock(MedicalSkillRegistry.class);
        when(skills.select(anyString())).thenReturn(List.of());
        AgentContext context = new AgentContext("trace", "owner", "session", "这是否需要处理？",
                null, null, new CollaborationBlackboard("trace", mock(CollaborationEventRepository.class)));
        context.setAnswer("这是未经核准的模型草稿，不应返回给用户。");
        context.setRag(new RagResult("", List.of(), List.of()));
        context.setEvidenceRequired(true);
        AgentTask task = AgentTask.agent("review", AgentTaskType.REVIEW_SAFETY,
                AgentCapability.SAFETY_REVIEW, Set.of(), 1, 0);

        var result = new SafetyCriticAgent(skills).execute(task, context.snapshot());

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(result.humanReviewRequired()).isTrue();
        assertThat(result.answer()).contains("已进入人工复核").doesNotContain("未经核准的模型草稿");
        assertThat(result.safetyReasons()).contains("未检索到足够的可引用医学证据");
        assertThat(result.safetyApproved()).isTrue();
    }

    @Test
    void unsafeCertaintyRequestsRevisionInsteadOfPublishingDraft() {
        MedicalSkillRegistry skills = mock(MedicalSkillRegistry.class);
        AgentContext context = new AgentContext("trace", "owner", "session", "请评估",
                null, null, new CollaborationBlackboard("trace", mock(CollaborationEventRepository.class)));
        context.setEvidenceRequired(false);
        context.setAnswer("这一定是某种疾病，无需就医。");
        AgentTask task = AgentTask.agent("review", AgentTaskType.REVIEW_SAFETY,
                AgentCapability.SAFETY_REVIEW, Set.of(), 1, 0);

        var result = new SafetyCriticAgent(skills).execute(task, context.snapshot());

        assertThat(result.revisionRequired()).isTrue();
        assertThat(result.safetyApproved()).isFalse();
        assertThat(result.answer()).isNull();
    }
}
