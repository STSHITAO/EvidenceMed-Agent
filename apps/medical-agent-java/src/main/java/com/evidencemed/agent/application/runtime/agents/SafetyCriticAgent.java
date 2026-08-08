package com.evidencemed.agent.application.runtime.agents;

import com.evidencemed.agent.application.runtime.AgentCapability;
import com.evidencemed.agent.application.runtime.AgentResult;
import com.evidencemed.agent.application.runtime.AgentRuntimeView;
import com.evidencemed.agent.application.runtime.AgentTask;
import com.evidencemed.agent.application.runtime.MedicalAgent;
import com.evidencemed.agent.application.skill.MedicalSkillRegistry;
import com.evidencemed.agent.domain.report.RiskLevel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class SafetyCriticAgent implements MedicalAgent {
    private static final String DISCLAIMER = "本回答仅供医疗信息参考，不能替代医生面诊、正式影像报告或急诊评估。";
    private static final String[] UNSAFE_DRAFT_PATTERNS = {
            "确诊为", "一定是", "保证治愈", "无需就医", "停止服药", "自行加量"
    };

    private final MedicalSkillRegistry skills;

    public SafetyCriticAgent(MedicalSkillRegistry skills) {
        this.skills = skills;
    }

    @Override public String name() { return "SafetyCriticAgent"; }
    @Override public Set<AgentCapability> capabilities() { return Set.of(AgentCapability.SAFETY_REVIEW); }

    @Override
    public AgentResult execute(AgentTask task, AgentRuntimeView context) {
        List<String> reasons = new ArrayList<>();
        RiskLevel risk;
        if (context.evidenceRequired() && context.rag().evidence().isEmpty()) {
            risk = RiskLevel.HIGH;
            reasons.add("未检索到足够的可引用医学证据");
        } else if (!context.rag().degradations().isEmpty()) {
            risk = RiskLevel.MEDIUM;
            reasons.add("检索或模型服务发生降级：" + String.join("、", context.rag().degradations()));
        } else {
            risk = RiskLevel.LOW;
        }

        boolean review = context.humanReviewRequired() || risk == RiskLevel.HIGH
                || !context.rag().degradations().isEmpty();
        if (context.humanReviewRequired() && reasons.isEmpty()) reasons.add("生成链路要求人工复核");

        String answer = context.answer() == null ? "" : context.answer().strip();
        if (risk != RiskLevel.HIGH && unsafeDraft(answer)) {
            reasons.add("回答包含可能越权或过度确定的医学表述");
            return baseResult("safety review requested revision", risk, true, reasons)
                    .revisionRequired(true)
                    .auditArtifact("safetyDecision", "revision-required")
                    .build();
        }

        if (risk == RiskLevel.HIGH) answer = controlledReviewHold(risk, reasons);
        if (answer.isBlank()) {
            reasons.add("回答为空或不可用");
            return baseResult("empty response requires revision", RiskLevel.HIGH, true, reasons)
                    .revisionRequired(true)
                    .auditArtifact("safetyDecision", "revision-required")
                    .build();
        }
        if (!answer.contains(DISCLAIMER)) answer += "\n\n" + DISCLAIMER;

        return baseResult("safety review approved", risk, review, reasons)
                .answer(answer)
                .safetyApproved(true)
                .auditArtifact("selectedSkills", skills.select(context.question()).stream()
                        .map(skill -> skill.id()).toList())
                .auditArtifact("safetyDecision", "risk=" + risk + ", humanReview=" + review)
                .build();
    }

    private AgentResult.Builder baseResult(String summary, RiskLevel risk, boolean review, List<String> reasons) {
        return AgentResult.builder(summary)
                .riskLevel(risk)
                .humanReviewRequired(review)
                .safetyReasons(reasons);
    }

    private boolean unsafeDraft(String answer) {
        for (String pattern : UNSAFE_DRAFT_PATTERNS) if (answer.contains(pattern)) return true;
        return false;
    }

    private String controlledReviewHold(RiskLevel risk, List<String> reasons) {
        return "系统不会展示未经临床人员确认的模型建议。请由具备资质的医生结合原始资料完成人工复核。"
                + "\n\n当前状态：" + risk + "，已进入人工复核。\n复核原因："
                + String.join("；", reasons) + "\n\n" + DISCLAIMER;
    }
}
