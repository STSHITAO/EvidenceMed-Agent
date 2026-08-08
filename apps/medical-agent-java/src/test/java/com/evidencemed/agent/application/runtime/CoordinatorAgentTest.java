package com.evidencemed.agent.application.runtime;

import com.evidencemed.agent.application.memory.CaseMemoryService;
import com.evidencemed.agent.application.memory.CaseMemorySnapshot;
import com.evidencemed.agent.application.rag.JavaMedicalRagService;
import com.evidencemed.agent.application.rag.RagResult;
import com.evidencemed.agent.domain.knowledge.RetrievedEvidence;
import com.evidencemed.agent.domain.report.RiskLevel;
import com.evidencemed.agent.domain.trace.AgentStep;
import com.evidencemed.agent.infrastructure.persistence.AgentStepRepository;
import com.evidencemed.agent.infrastructure.persistence.CollaborationEventRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoordinatorAgentTest {
    @Test
    void runsIndependentSystemTasksConcurrentlyAndSkipsEvidencePlannerWhenEvidenceExists() {
        List<AgentTaskType> execution = new ArrayList<>();
        CaseMemoryService memory = mock(CaseMemoryService.class);
        JavaMedicalRagService rag = mock(JavaMedicalRagService.class);
        CountDownLatch started = new CountDownLatch(2);
        when(memory.load(anyString(), anyString())).thenAnswer(call -> {
            awaitPeer(started);
            return CaseMemorySnapshot.empty();
        });
        when(rag.retrieve(anyString(), any(), any())).thenAnswer(call -> {
            awaitPeer(started);
            return evidenceResult();
        });

        MedicalAgent reasoning = agent("reasoning", AgentCapability.MEDICAL_REASONING, execution,
                (task, view) -> AgentResult.builder("generated").answer("辅助回答").build());
        MedicalAgent safety = agent("safety", AgentCapability.SAFETY_REVIEW, execution,
                (task, view) -> AgentResult.builder("approved").answer(view.answer())
                        .riskLevel(RiskLevel.LOW).safetyApproved(true).build());
        MedicalAgent planner = agent("planner", AgentCapability.EVIDENCE_PLANNING, execution,
                (task, view) -> AgentResult.builder("planned").retrievalQuery("不应执行").build());

        AgentContext context = context("请评估这个症状");
        coordinator(List.of(reasoning, safety, planner), memory, rag).run(context);

        assertThat(execution).containsExactly(AgentTaskType.GENERATE_RESPONSE, AgentTaskType.REVIEW_SAFETY);
        assertThat(context.isSafetyApproved()).isTrue();
        assertThat(context.getBlackboard().isCompleted("load-case-memory")).isTrue();
        assertThat(context.getBlackboard().isCompleted("retrieve-evidence")).isTrue();
    }

    @Test
    void createsEvidencePlanningAndRetryTasksOnlyWhenInitialEvidenceIsEmpty() {
        List<AgentTaskType> execution = new ArrayList<>();
        CaseMemoryService memory = mock(CaseMemoryService.class);
        when(memory.load(anyString(), anyString())).thenReturn(CaseMemorySnapshot.empty());
        JavaMedicalRagService rag = mock(JavaMedicalRagService.class);
        when(rag.retrieve(anyString(), any(), any()))
                .thenReturn(new RagResult("", List.of(), List.of("NO_EVIDENCE")))
                .thenReturn(evidenceResult());

        MedicalAgent planner = agent("planner", AgentCapability.EVIDENCE_PLANNING, execution,
                (task, view) -> AgentResult.builder("planned").retrievalQuery("改写后的医学查询").build());
        MedicalAgent reasoning = agent("reasoning", AgentCapability.MEDICAL_REASONING, execution,
                (task, view) -> AgentResult.builder("generated").answer("辅助回答").build());
        MedicalAgent safety = agent("safety", AgentCapability.SAFETY_REVIEW, execution,
                (task, view) -> AgentResult.builder("approved").answer(view.answer())
                        .riskLevel(RiskLevel.LOW).safetyApproved(true).build());

        AgentContext context = context("这个症状如何处理");
        coordinator(List.of(planner, reasoning, safety), memory, rag).run(context);

        assertThat(execution).containsExactly(AgentTaskType.PLAN_EVIDENCE_RETRY,
                AgentTaskType.GENERATE_RESPONSE, AgentTaskType.REVIEW_SAFETY);
        verify(rag).retrieve("改写后的医学查询", null, null);
        assertThat(context.getRag().evidence()).hasSize(1);
    }

    @Test
    void skipsRagForSimpleInteraction() {
        List<AgentTaskType> execution = new ArrayList<>();
        CaseMemoryService memory = mock(CaseMemoryService.class);
        when(memory.load(anyString(), anyString())).thenReturn(CaseMemorySnapshot.empty());
        JavaMedicalRagService rag = mock(JavaMedicalRagService.class);
        MedicalAgent reasoning = agent("reasoning", AgentCapability.MEDICAL_REASONING, execution,
                (task, view) -> AgentResult.builder("generated").answer("你好，我可以提供医学信息辅助。 ").build());
        MedicalAgent safety = agent("safety", AgentCapability.SAFETY_REVIEW, execution,
                (task, view) -> AgentResult.builder("approved").answer(view.answer())
                        .riskLevel(RiskLevel.LOW).safetyApproved(true).build());

        AgentContext context = context("你好");
        coordinator(List.of(reasoning, safety), memory, rag).run(context);

        verify(rag, never()).retrieve(anyString(), any(), any());
        assertThat(context.isEvidenceRequired()).isFalse();
        assertThat(context.isSafetyApproved()).isTrue();
    }

    @Test
    void dynamicallyRevisesRejectedDraftBeforePublishing() {
        List<AgentTaskType> execution = new ArrayList<>();
        CaseMemoryService memory = mock(CaseMemoryService.class);
        when(memory.load(anyString(), anyString())).thenReturn(CaseMemorySnapshot.empty());
        JavaMedicalRagService rag = mock(JavaMedicalRagService.class);
        when(rag.retrieve(anyString(), any(), any())).thenReturn(evidenceResult());

        MedicalAgent reasoning = agent("reasoning", AgentCapability.MEDICAL_REASONING, execution,
                (task, view) -> AgentResult.builder("response")
                        .answer(task.type() == AgentTaskType.REVISE_RESPONSE ? "审慎修订回答" : "一定是某疾病")
                        .build());
        MedicalAgent safety = agent("safety", AgentCapability.SAFETY_REVIEW, execution,
                (task, view) -> view.answer().contains("一定是")
                        ? AgentResult.builder("rejected").riskLevel(RiskLevel.MEDIUM)
                                .safetyReasons(List.of("表述过度确定")).revisionRequired(true).build()
                        : AgentResult.builder("approved").answer(view.answer())
                                .riskLevel(RiskLevel.LOW).safetyApproved(true).build());

        AgentContext context = context("请判断这个症状");
        coordinator(List.of(reasoning, safety), memory, rag).run(context);

        assertThat(execution).containsExactly(AgentTaskType.GENERATE_RESPONSE,
                AgentTaskType.REVIEW_SAFETY, AgentTaskType.REVISE_RESPONSE, AgentTaskType.REVIEW_SAFETY);
        assertThat(context.getAnswer()).isEqualTo("审慎修订回答");
        assertThat(context.getRevisionCount()).isEqualTo(1);
        assertThat(context.isSafetyApproved()).isTrue();
    }

    @Test
    void emergencyRuleShortCircuitsModelsAndReturnsControlledHold() {
        CaseMemoryService memory = mock(CaseMemoryService.class);
        JavaMedicalRagService rag = mock(JavaMedicalRagService.class);
        AgentContext context = context("现在呼吸困难并且意识不清");

        coordinator(List.of(), memory, rag).run(context);

        verify(memory, never()).load(anyString(), anyString());
        verify(rag, never()).retrieve(anyString(), any(), any());
        assertThat(context.getRiskLevel()).isEqualTo(RiskLevel.EMERGENCY);
        assertThat(context.isHumanReviewRequired()).isTrue();
        assertThat(context.isSafetyApproved()).isTrue();
        assertThat(context.getAnswer()).startsWith("请立即联系当地急救服务");
    }

    @Test
    void appliesControlledHoldAfterTwoRejectedRevisions() {
        List<AgentTaskType> execution = new ArrayList<>();
        CaseMemoryService memory = mock(CaseMemoryService.class);
        when(memory.load(anyString(), anyString())).thenReturn(CaseMemorySnapshot.empty());
        JavaMedicalRagService rag = mock(JavaMedicalRagService.class);
        when(rag.retrieve(anyString(), any(), any())).thenReturn(evidenceResult());
        MedicalAgent reasoning = agent("reasoning", AgentCapability.MEDICAL_REASONING, execution,
                (task, view) -> AgentResult.builder("unsafe response").answer("一定是某疾病").build());
        MedicalAgent safety = agent("safety", AgentCapability.SAFETY_REVIEW, execution,
                (task, view) -> AgentResult.builder("rejected").riskLevel(RiskLevel.MEDIUM)
                        .safetyReasons(List.of("表述过度确定")).revisionRequired(true).build());
        AgentContext context = context("请判断这个症状");

        coordinator(List.of(reasoning, safety), memory, rag).run(context);

        assertThat(context.getRevisionCount()).isEqualTo(2);
        assertThat(context.isSafetyApproved()).isTrue();
        assertThat(context.isHumanReviewRequired()).isTrue();
        assertThat(context.getAnswer()).contains("已进入人工复核").doesNotContain("一定是某疾病");
    }

    private CoordinatorAgent coordinator(List<MedicalAgent> agents, CaseMemoryService memory,
                                         JavaMedicalRagService rag) {
        AgentStepRepository steps = mock(AgentStepRepository.class);
        when(steps.save(any(AgentStep.class))).thenAnswer(call -> call.getArgument(0));
        return new CoordinatorAgent(agents, steps, memory, rag, new AgentRuntimePreprocessor());
    }

    private AgentContext context(String question) {
        return new AgentContext("trace", "owner", "session", question, null, null,
                new CollaborationBlackboard("trace", mock(CollaborationEventRepository.class)));
    }

    private MedicalAgent agent(String name, AgentCapability capability, List<AgentTaskType> execution,
                               BiFunction<AgentTask, AgentRuntimeView, AgentResult> behavior) {
        return new MedicalAgent() {
            @Override public String name() { return name; }
            @Override public Set<AgentCapability> capabilities() { return Set.of(capability); }
            @Override public AgentResult execute(AgentTask task, AgentRuntimeView context) {
                execution.add(task.type());
                return behavior.apply(task, context);
            }
        };
    }

    private RagResult evidenceResult() {
        RetrievedEvidence evidence = new RetrievedEvidence("chunk", "document", "guide.md", 0,
                "医学证据", "text", 0.9, "bm25");
        return new RagResult("", List.of(evidence), List.of());
    }

    private void awaitPeer(CountDownLatch started) throws InterruptedException {
        started.countDown();
        if (!started.await(2, TimeUnit.SECONDS)) throw new AssertionError("system tasks were not concurrent");
    }
}
