package com.evidencemed.agent.application.runtime.agents;

import com.evidencemed.agent.application.runtime.AgentContext;
import com.evidencemed.agent.application.runtime.MedicalAgent;
import com.evidencemed.agent.application.skill.MedicalSkillRegistry;
import com.evidencemed.agent.domain.report.RiskLevel;
import org.springframework.stereotype.Component;

@Component
public class SafetyReviewAgent implements MedicalAgent {
    private static final String DISCLAIMER = "本回答仅供医疗信息参考，不能替代医生面诊、正式影像报告或急诊评估。";
    private static final String[] EMERGENCY = {
            "呼吸困难", "意识不清", "昏迷", "剧烈胸痛", "大出血", "抽搐", "自杀", "轻生"
    };
    private final MedicalSkillRegistry skills;
    public SafetyReviewAgent(MedicalSkillRegistry skills) { this.skills = skills; }
    @Override public String name() { return "SafetyReviewAgent"; }
    @Override public void execute(AgentContext context) {
        RiskLevel risk = redFlag(context.getQuestion()) ? RiskLevel.EMERGENCY
                : context.getRag().evidence().isEmpty() ? RiskLevel.HIGH
                : context.getRag().degradations().isEmpty() ? RiskLevel.LOW : RiskLevel.MEDIUM;
        boolean review = risk == RiskLevel.EMERGENCY || risk == RiskLevel.HIGH
                || !context.getRag().degradations().isEmpty();
        context.setRiskLevel(risk);
        context.setHumanReviewRequired(review);
        String answer = context.getAnswer() == null ? "" : context.getAnswer().strip();
        if (risk == RiskLevel.EMERGENCY) {
            answer = "请立即联系当地急救服务或前往最近的急诊，不要仅依赖在线回答。\n\n" + answer;
        }
        if (context.getRag().evidence().isEmpty()) {
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
}
