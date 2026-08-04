package com.evidencemed.agent.application.runtime.agents;

import com.evidencemed.agent.application.rag.RagResult;
import com.evidencemed.agent.application.runtime.AgentContext;
import com.evidencemed.agent.application.runtime.CollaborationBlackboard;
import com.evidencemed.agent.application.skill.MedicalSkillRegistry;
import com.evidencemed.agent.domain.report.RiskLevel;
import com.evidencemed.agent.infrastructure.persistence.CollaborationEventRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SafetyReviewAgentTest {
    @Test
    void emergencyRedFlagOverridesDraftAndRequiresReview() {
        MedicalSkillRegistry skills = mock(MedicalSkillRegistry.class);
        when(skills.select(anyString())).thenReturn(List.of());
        AgentContext context = new AgentContext("trace", "owner", "session", "现在呼吸困难",
                null, null, new CollaborationBlackboard("trace", mock(CollaborationEventRepository.class)));
        context.setAnswer("可以继续观察。 ");
        context.setRag(new RagResult("", List.of(), List.of()));

        new SafetyReviewAgent(skills).execute(context);

        assertThat(context.getRiskLevel()).isEqualTo(RiskLevel.EMERGENCY);
        assertThat(context.isHumanReviewRequired()).isTrue();
        assertThat(context.getAnswer()).startsWith("请立即联系当地急救服务").contains("不能替代医生面诊");
    }

    @Test
    void highRiskWithoutEvidenceSuppressesModelDraftUntilHumanReview() {
        MedicalSkillRegistry skills = mock(MedicalSkillRegistry.class);
        when(skills.select(anyString())).thenReturn(List.of());
        AgentContext context = new AgentContext("trace", "owner", "session", "这是否需要处理？",
                null, null, new CollaborationBlackboard("trace", mock(CollaborationEventRepository.class)));
        context.setAnswer("这是未经核准的模型草稿，不应返回给用户。");
        context.setRag(new RagResult("", List.of(), List.of()));

        new SafetyReviewAgent(skills).execute(context);

        assertThat(context.getRiskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(context.isHumanReviewRequired()).isTrue();
        assertThat(context.getAnswer()).contains("已进入人工复核").doesNotContain("未经核准的模型草稿");
        assertThat(context.getSafetyReasons()).contains("未检索到足够的可引用医学证据");
    }
}
