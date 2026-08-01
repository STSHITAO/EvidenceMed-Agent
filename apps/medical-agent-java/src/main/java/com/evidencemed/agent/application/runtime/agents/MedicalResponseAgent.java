package com.evidencemed.agent.application.runtime.agents;

import com.evidencemed.agent.application.model.VisionLanguageModel;
import com.evidencemed.agent.application.runtime.AgentContext;
import com.evidencemed.agent.application.runtime.MedicalAgent;
import com.evidencemed.agent.domain.knowledge.RetrievedEvidence;
import com.evidencemed.agent.domain.report.RiskLevel;
import com.evidencemed.agent.application.skill.MedicalSkillRegistry;
import com.evidencemed.agent.infrastructure.model.ModelServiceException;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class MedicalResponseAgent implements MedicalAgent {
    private static final String SYSTEM_PROMPT = """
            你是医学咨询辅助 Agent。必须依据编号证据回答，区分事实与推测，不做确定性诊断，不开处方。
            若证据不足应明确说明；若存在急症风险，首句建议立即联系急救或就近急诊。
            输出包含：观察与分析、证据依据、不确定性、下一步建议、安全提示。
            """;
    private final VisionLanguageModel model;
    private final MedicalSkillRegistry skills;
    public MedicalResponseAgent(VisionLanguageModel model, MedicalSkillRegistry skills) {
        this.model = model;
        this.skills = skills;
    }
    @Override public String name() { return "MedicalResponseAgent"; }
    @Override public void execute(AgentContext context) {
        String evidence = evidenceText(context);
        String prompt = "病例摘要：\n" + context.getMemory().caseBrief()
                + "\n\n用户问题：\n" + context.getQuestion()
                + "\n\n检索证据：\n" + evidence
                + "\n\n适用业务 Skills：\n" + selectedSkillInstructions(context.getQuestion());
        try {
            String answer = model.generate(SYSTEM_PROMPT, prompt, context.getImage(),
                    context.getImageMediaType(), 700, 0.2);
            context.setAnswer(answer);
        } catch (ModelServiceException exception) {
            context.setHumanReviewRequired(true);
            context.setAnswer("当前医学推理服务暂不可用。已保留本次运行记录，请由医生人工复核；如症状严重或持续加重，请及时急诊就医。");
        }
        context.getBlackboard().publish(name(), "draftAnswer", "generated", "answer generated");
    }
    private String selectedSkillInstructions(String question) {
        var selected = skills.select(question);
        if (selected.isEmpty()) return "无额外 Skill；仍执行系统安全规则。";
        return selected.stream().map(skill -> skill.instructions()).collect(Collectors.joining("\n\n"));
    }
    private String evidenceText(AgentContext context) {
        if (context.getRag().evidence().isEmpty()) return "未检索到足够证据。";
        return java.util.stream.IntStream.range(0, context.getRag().evidence().size())
                .mapToObj(index -> format(index + 1, context.getRag().evidence().get(index)))
                .collect(Collectors.joining("\n"));
    }
    private String format(int index, RetrievedEvidence item) {
        return "[" + index + "] 来源=" + item.source() + "，内容=" + item.content();
    }
}
