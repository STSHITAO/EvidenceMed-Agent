package com.evidencemed.agent.application.runtime.agents;

import com.evidencemed.agent.application.runtime.AgentContext;
import com.evidencemed.agent.application.runtime.MedicalAgent;
import com.evidencemed.agent.application.skill.MedicalSkillRegistry;
import com.evidencemed.agent.domain.report.RiskLevel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SafetyReviewAgent implements MedicalAgent {
    private static final String DISCLAIMER = "本回答仅供医疗信息参考，不能替代医生面诊、正式影像报告或急诊评估。";
    private static final String[] EMERGENCY = {
            "呼吸困难", "呼吸急促", "气短", "喘不过气", "意识不清", "意识丧失", "昏迷", "晕倒",
            "剧烈胸痛", "胸口剧痛", "大出血", "吐血", "咯血", "抽搐", "惊厥", "自杀", "轻生", "自伤"
    };
    private final MedicalSkillRegistry skills;
    public SafetyReviewAgent(MedicalSkillRegistry skills) { this.skills = skills; }
    @Override public String name() { return "SafetyReviewAgent"; }
    @Override public void execute(AgentContext context) {
        List<String> reasons = new ArrayList<>();
        RiskLevel risk;
        if (redFlag(context.getQuestion())) {
            risk = RiskLevel.EMERGENCY;
            reasons.add("检测到可能的急症红旗症状");
        } else if (context.getRag().evidence().isEmpty()) {
            risk = RiskLevel.HIGH;
            reasons.add("未检索到足够的可引用医学证据");
        } else if (!context.getRag().degradations().isEmpty()) {
            risk = RiskLevel.MEDIUM;
            reasons.add("检索或模型服务发生降级：" + String.join("、", context.getRag().degradations()));
        } else {
            risk = RiskLevel.LOW;
        }
        boolean review = context.isHumanReviewRequired() || risk == RiskLevel.EMERGENCY || risk == RiskLevel.HIGH
                || !context.getRag().degradations().isEmpty();
        if (context.isHumanReviewRequired() && reasons.isEmpty()) {
            reasons.add("生成链路要求人工复核");
        }
        context.setRiskLevel(risk);
        context.setHumanReviewRequired(review);
        context.setSafetyReasons(reasons);
        String answer = context.getAnswer() == null ? "" : context.getAnswer().strip();
        if (risk == RiskLevel.EMERGENCY || risk == RiskLevel.HIGH) {
            answer = controlledReviewHold(risk, reasons);
        }
        if (context.getRag().evidence().isEmpty() && risk != RiskLevel.HIGH) {
            answer += "\n\n当前知识库未检索到足够证据，本次回答必须由医生人工复核。";
        }
        if (answer.isBlank()) {
            answer = "当前无法生成可靠回答，请由医生结合原始影像和临床资料人工复核。";
        }
        if (!answer.contains(DISCLAIMER)) answer += "\n\n" + DISCLAIMER;
        context.setAnswer(answer);
        var selectedSkills = skills.select(context.getQuestion());
        context.getBlackboard().publish(name(), "selectedSkills", selectedSkills.stream()
                .map(skill -> skill.id()).toList(), "skills=" + selectedSkills.size());
        context.getBlackboard().publish(name(), "safetyDecision", risk,
                "risk=" + risk + ", humanReview=" + review);
    }
    private boolean redFlag(String question) {
        for (String keyword : EMERGENCY) if (question.contains(keyword)) return true;
        return false;
    }

    private String controlledReviewHold(RiskLevel risk, List<String> reasons) {
        String action = risk == RiskLevel.EMERGENCY
                ? "请立即联系当地急救服务或前往最近的急诊，不要等待在线答复或人工队列。"
                : "系统不会展示未经临床人员确认的模型建议。请由具备资质的医生结合原始资料完成复核。";
        return action + "\n\n当前状态：" + risk + "，已进入人工复核。\n复核原因："
                + String.join("；", reasons) + "\n\n" + DISCLAIMER;
    }
}
