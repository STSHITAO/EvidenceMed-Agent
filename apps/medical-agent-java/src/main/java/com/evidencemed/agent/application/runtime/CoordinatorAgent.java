package com.evidencemed.agent.application.runtime;

import com.evidencemed.agent.application.memory.CaseMemoryService;
import com.evidencemed.agent.application.rag.JavaMedicalRagService;
import com.evidencemed.agent.application.rag.RagResult;
import com.evidencemed.agent.domain.report.RiskLevel;
import com.evidencemed.agent.domain.trace.AgentStep;
import com.evidencemed.agent.domain.trace.RunStatus;
import com.evidencemed.agent.infrastructure.persistence.AgentStepRepository;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class CoordinatorAgent {
    private static final int MAX_ROUNDS = 12;
    private static final int MAX_REVISIONS = 2;
    private static final String MEMORY_TASK = "load-case-memory";
    private static final String RETRIEVAL_TASK = "retrieve-evidence";
    private static final String EVIDENCE_PLAN_TASK = "plan-evidence-retry";
    private static final String RETRY_TASK = "retry-evidence";
    private static final String RESPONSE_TASK = "generate-response";
    private static final String REVIEW_HOLD_TASK = "apply-review-hold";
    private static final String DISCLAIMER = "本回答仅供医疗信息参考，不能替代医生面诊、正式影像报告或急诊评估。";

    private final List<MedicalAgent> agents;
    private final AgentStepRepository steps;
    private final CaseMemoryService memory;
    private final JavaMedicalRagService rag;
    private final AgentRuntimePreprocessor preprocessor;
    private final ExecutorService executor = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "medical-agent-runtime");
        thread.setDaemon(true);
        return thread;
    });

    public CoordinatorAgent(List<MedicalAgent> agents, AgentStepRepository steps,
                            CaseMemoryService memory, JavaMedicalRagService rag,
                            AgentRuntimePreprocessor preprocessor) {
        this.agents = List.copyOf(agents);
        this.steps = steps;
        this.memory = memory;
        this.rag = rag;
        this.preprocessor = preprocessor;
    }

    public void run(AgentContext context) {
        initialize(context);
        for (int round = 0; round < MAX_ROUNDS; round++) {
            if (context.isSafetyApproved()) return;
            deriveTasks(context, round);
            List<AgentTask> ready = context.getBlackboard().readyTasks();
            if (ready.isEmpty()) break;

            AgentRuntimeView view = context.snapshot();
            List<CompletableFuture<TaskExecution>> futures = ready.stream()
                    .map(task -> CompletableFuture.supplyAsync(() -> execute(task, view, context.getBlackboard()), executor))
                    .toList();
            RuntimeException failure = null;
            for (CompletableFuture<TaskExecution> future : futures) {
                try {
                    TaskExecution execution = join(future);
                    merge(context, execution);
                } catch (RuntimeException exception) {
                    if (failure == null) failure = exception;
                }
            }
            if (failure != null) throw failure;
        }
        if (!context.isSafetyApproved()) applyConvergenceFallback(context);
    }

    private void initialize(AgentContext context) {
        boolean hasImage = context.getImage() != null;
        ClinicalRoute route = preprocessor.route(context.getQuestion(), hasImage);
        context.setRoute(route);
        context.setEvidenceRequired(preprocessor.requiresEvidence(context.getQuestion(), hasImage));
        context.getBlackboard().publish("AgentRuntimePreprocessor", "clinicalRoute", route, route.name());

        if (preprocessor.isEmergency(context.getQuestion())) {
            context.setRiskLevel(RiskLevel.EMERGENCY);
            context.setHumanReviewRequired(true);
            context.setSafetyReasons(List.of("检测到可能的急症红旗症状"));
            context.getBlackboard().createTask(AgentTask.system(REVIEW_HOLD_TASK,
                    AgentTaskType.APPLY_REVIEW_HOLD, Set.of(), 1000, 0));
            return;
        }

        context.getBlackboard().createTask(AgentTask.system(MEMORY_TASK,
                AgentTaskType.LOAD_CASE_MEMORY, Set.of(), 100, 0));
        if (context.isEvidenceRequired()) {
            context.getBlackboard().createTask(AgentTask.system(RETRIEVAL_TASK,
                    AgentTaskType.RETRIEVE_EVIDENCE, Set.of(), 90, 0));
        }
    }

    private void deriveTasks(AgentContext context, int round) {
        CollaborationBlackboard board = context.getBlackboard();
        if (board.hasTask(REVIEW_HOLD_TASK)) return;
        if (!board.isCompleted(MEMORY_TASK)) return;

        String evidenceDependency = null;
        if (context.isEvidenceRequired()) {
            if (!board.isCompleted(RETRIEVAL_TASK)) return;
            if (!context.getRag().evidence().isEmpty()) {
                evidenceDependency = RETRIEVAL_TASK;
            } else if (!board.hasTask(EVIDENCE_PLAN_TASK)) {
                board.createTask(AgentTask.agent(EVIDENCE_PLAN_TASK,
                        AgentTaskType.PLAN_EVIDENCE_RETRY, AgentCapability.EVIDENCE_PLANNING,
                        Set.of(RETRIEVAL_TASK), 80, round));
                return;
            } else if (!board.isCompleted(EVIDENCE_PLAN_TASK)) {
                return;
            } else if (context.getPlannedRetrievalQuery() != null
                    && !context.getPlannedRetrievalQuery().isBlank()) {
                if (!board.hasTask(RETRY_TASK)) {
                    board.createTask(AgentTask.system(RETRY_TASK, AgentTaskType.RETRY_EVIDENCE,
                            Set.of(EVIDENCE_PLAN_TASK), 75, round));
                    return;
                }
                if (!board.isCompleted(RETRY_TASK)) return;
                evidenceDependency = RETRY_TASK;
            } else {
                evidenceDependency = EVIDENCE_PLAN_TASK;
            }
        }

        if (!board.hasTask(RESPONSE_TASK)) {
            Set<String> dependencies = evidenceDependency == null
                    ? Set.of(MEMORY_TASK) : Set.of(MEMORY_TASK, evidenceDependency);
            board.createTask(AgentTask.agent(RESPONSE_TASK, AgentTaskType.GENERATE_RESPONSE,
                    AgentCapability.MEDICAL_REASONING, dependencies, 60, round));
            return;
        }

        int version = context.getRevisionCount();
        String responseId = version == 0 ? RESPONSE_TASK : revisionTask(version);
        if (!board.isCompleted(responseId)) return;
        String reviewId = reviewTask(version);
        if (!board.hasTask(reviewId)) {
            board.createTask(AgentTask.agent(reviewId, AgentTaskType.REVIEW_SAFETY,
                    AgentCapability.SAFETY_REVIEW, Set.of(responseId), 100, round));
            return;
        }
        if (!board.isCompleted(reviewId) || !context.isRevisionRequired()) return;

        if (version < MAX_REVISIONS) {
            String revisionId = revisionTask(version + 1);
            if (!board.hasTask(revisionId)) {
                board.createTask(AgentTask.agent(revisionId, AgentTaskType.REVISE_RESPONSE,
                        AgentCapability.MEDICAL_REASONING, Set.of(reviewId), 90, round));
            }
        } else if (!board.hasTask(REVIEW_HOLD_TASK)) {
            board.createTask(AgentTask.system(REVIEW_HOLD_TASK, AgentTaskType.APPLY_REVIEW_HOLD,
                    Set.of(reviewId), 1000, round));
        }
    }

    private TaskExecution execute(AgentTask task, AgentRuntimeView view, CollaborationBlackboard board) {
        MedicalAgent agent = task.executorKind() == TaskExecutorKind.AGENT ? selectAgent(task, view) : null;
        String actor = agent == null ? systemActor(task.type()) : agent.name();
        board.claimTask(task.id(), actor);
        AgentStep step = steps.save(new AgentStep(view.traceId(), actor));
        try {
            AgentResult result = agent == null ? executeSystemTask(task, view) : agent.execute(task, view);
            step.finish(RunStatus.SUCCEEDED, task.id() + ": " + result.summary());
            steps.save(step);
            return new TaskExecution(task, actor, result);
        } catch (RuntimeException exception) {
            step.finish(RunStatus.FAILED, task.id() + ": " + exception.getClass().getSimpleName());
            steps.save(step);
            board.failTask(task.id(), actor, exception.getClass().getSimpleName());
            throw exception;
        }
    }

    private AgentResult executeSystemTask(AgentTask task, AgentRuntimeView view) {
        return switch (task.type()) {
            case LOAD_CASE_MEMORY -> AgentResult.builder("case memory loaded")
                    .memory(memory.load(view.ownerId(), view.sessionId()))
                    .auditArtifact("caseMemory", "loaded")
                    .build();
            case RETRIEVE_EVIDENCE -> retrievalResult(
                    rag.retrieve(view.question(), view.image(), view.imageMediaType()), "initial evidence retrieved");
            case RETRY_EVIDENCE -> retrievalResult(
                    rag.retrieve(view.plannedRetrievalQuery(), view.image(), view.imageMediaType()),
                    "evidence retry completed");
            case APPLY_REVIEW_HOLD -> reviewHold(view);
            default -> throw new IllegalArgumentException("不支持的系统任务: " + task.type());
        };
    }

    private AgentResult retrievalResult(RagResult result, String summary) {
        return AgentResult.builder(summary)
                .rag(result)
                .auditArtifact("evidence", "evidence=" + result.evidence().size()
                        + ", degradations=" + result.degradations())
                .build();
    }

    private AgentResult reviewHold(AgentRuntimeView view) {
        RiskLevel risk = view.riskLevel() == RiskLevel.EMERGENCY ? RiskLevel.EMERGENCY : RiskLevel.HIGH;
        List<String> reasons = view.safetyReasons().isEmpty()
                ? List.of("多轮安全修订未能通过") : view.safetyReasons();
        String action = risk == RiskLevel.EMERGENCY
                ? "请立即联系当地急救服务或前往最近的急诊，不要等待在线答复或人工队列。"
                : "系统不会展示未经临床人员确认的模型建议。请由具备资质的医生结合原始资料完成人工复核。";
        String answer = action + "\n\n当前状态：" + risk + "，已进入人工复核。\n复核原因："
                + String.join("；", reasons) + "\n\n" + DISCLAIMER;
        return AgentResult.builder("controlled review hold applied")
                .answer(answer)
                .riskLevel(risk)
                .humanReviewRequired(true)
                .safetyReasons(reasons)
                .safetyApproved(true)
                .auditArtifact("safetyDecision", "controlled-review-hold")
                .build();
    }

    private MedicalAgent selectAgent(AgentTask task, AgentRuntimeView view) {
        return agents.stream()
                .filter(agent -> agent.capabilities().contains(task.requiredCapability()))
                .max(Comparator.comparingInt(agent -> agent.score(task, view)))
                .orElseThrow(() -> new IllegalStateException("没有 Agent 能够认领任务: " + task.id()));
    }

    private void merge(AgentContext context, TaskExecution execution) {
        AgentResult result = execution.result();
        if (result.memory() != null) context.setMemory(result.memory());
        if (result.rag() != null) context.setRag(result.rag());
        if (result.retrievalQuery() != null) context.setPlannedRetrievalQuery(result.retrievalQuery());
        if (result.answer() != null) context.setAnswer(result.answer());
        if (result.riskLevel() != null) context.setRiskLevel(result.riskLevel());
        if (result.humanReviewRequired() != null) {
            context.setHumanReviewRequired(result.humanReviewRequired());
        }
        if (result.safetyReasons() != null) context.setSafetyReasons(result.safetyReasons());

        if (execution.task().type() == AgentTaskType.GENERATE_RESPONSE
                || execution.task().type() == AgentTaskType.REVISE_RESPONSE) {
            context.setSafetyApproved(false);
            context.setRevisionRequired(false);
        }
        if (execution.task().type() == AgentTaskType.REVISE_RESPONSE) context.incrementRevisionCount();
        context.setRevisionRequired(result.revisionRequired());
        if (result.safetyApproved()) context.setSafetyApproved(true);

        result.auditArtifacts().forEach((name, value) -> context.getBlackboard()
                .publish(execution.actor(), name, value, result.summary()));
        context.getBlackboard().completeTask(execution.task().id(), execution.actor(), result.summary());
    }

    private void applyConvergenceFallback(AgentContext context) {
        List<String> reasons = new ArrayList<>(context.getSafetyReasons());
        reasons.add("Agent 编排未在限定轮数内收敛");
        context.setRiskLevel(RiskLevel.HIGH);
        context.setHumanReviewRequired(true);
        context.setSafetyReasons(reasons);
        context.setAnswer("系统未能在安全轮次限制内形成可发布结论，请由医生结合原始资料人工复核。\n\n" + DISCLAIMER);
        context.setSafetyApproved(true);
        context.getBlackboard().publish("Coordinator", "safetyDecision", "convergence-fallback",
                "max rounds reached; human review required");
    }

    private TaskExecution join(CompletableFuture<TaskExecution> future) {
        try {
            return future.join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException runtime) throw runtime;
            throw exception;
        }
    }

    private String systemActor(AgentTaskType type) {
        return switch (type) {
            case LOAD_CASE_MEMORY -> "CaseMemoryService";
            case RETRIEVE_EVIDENCE, RETRY_EVIDENCE -> "JavaMedicalRagService";
            case APPLY_REVIEW_HOLD -> "MedicalSafetyPolicy";
            default -> "Coordinator";
        };
    }

    private String revisionTask(int version) { return "revise-response-" + version; }
    private String reviewTask(int version) { return "review-safety-" + version; }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    private record TaskExecution(AgentTask task, String actor, AgentResult result) {}
}
