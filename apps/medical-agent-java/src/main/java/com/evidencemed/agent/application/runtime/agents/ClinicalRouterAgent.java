package com.evidencemed.agent.application.runtime.agents;

import com.evidencemed.agent.application.runtime.AgentContext;
import com.evidencemed.agent.application.runtime.ClinicalRoute;
import com.evidencemed.agent.application.runtime.MedicalAgent;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class ClinicalRouterAgent implements MedicalAgent {
    @Override public String name() { return "ClinicalRouterAgent"; }
    @Override public void execute(AgentContext context) {
        String text = context.getQuestion().toLowerCase(Locale.ROOT);
        ClinicalRoute route = context.getImage() != null || contains(text, "影像", "ct", "mri", "x光", "片子")
                ? ClinicalRoute.IMAGING
                : contains(text, "药", "用量", "剂量", "副作用") ? ClinicalRoute.MEDICATION
                : contains(text, "疼", "发热", "咳", "胸闷", "头晕") ? ClinicalRoute.SYMPTOM
                : ClinicalRoute.GENERAL;
        context.setRoute(route);
        context.getBlackboard().publish(name(), "clinicalRoute", route, route.name());
    }
    private boolean contains(String value, String... words) {
        for (String word : words) if (value.contains(word)) return true;
        return false;
    }
}
