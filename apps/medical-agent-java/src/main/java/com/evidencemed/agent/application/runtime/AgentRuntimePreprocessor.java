package com.evidencemed.agent.application.runtime;

import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

@Service
public class AgentRuntimePreprocessor {
    private static final Set<String> NON_MEDICAL_INTERACTIONS = Set.of(
            "你好", "您好", "谢谢", "感谢", "你是谁", "帮助", "怎么使用", "如何使用"
    );
    private static final String[] EMERGENCY = {
            "呼吸困难", "呼吸急促", "喘不过气", "意识不清", "意识丧失", "昏迷", "晕厥",
            "剧烈胸痛", "胸口剧痛", "大出血", "吐血", "咯血", "抽搐", "惊厥", "自杀", "轻生", "自伤"
    };

    public ClinicalRoute route(String question, boolean hasImage) {
        String text = question.toLowerCase(Locale.ROOT);
        if (hasImage || contains(text, "影像", "ct", "mri", "x光", "片子")) return ClinicalRoute.IMAGING;
        if (contains(text, "药", "用量", "剂量", "副作用")) return ClinicalRoute.MEDICATION;
        if (contains(text, "痛", "发热", "咳", "胸闷", "头晕")) return ClinicalRoute.SYMPTOM;
        return ClinicalRoute.GENERAL;
    }

    public boolean requiresEvidence(String question, boolean hasImage) {
        if (hasImage) return true;
        String normalized = question.strip().replaceAll("[，。！？,.!?\\s]", "");
        return !NON_MEDICAL_INTERACTIONS.contains(normalized);
    }

    public boolean isEmergency(String question) {
        return contains(question, EMERGENCY);
    }

    private boolean contains(String value, String... words) {
        for (String word : words) if (value.contains(word)) return true;
        return false;
    }
}
