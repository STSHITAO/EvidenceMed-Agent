package com.evidencemed.agent.application.runtime.agents;

import com.evidencemed.agent.application.rag.JavaMedicalRagService;
import com.evidencemed.agent.application.runtime.AgentContext;
import com.evidencemed.agent.application.runtime.MedicalAgent;
import org.springframework.stereotype.Component;

@Component
public class EvidenceRetrieverAgent implements MedicalAgent {
    private final JavaMedicalRagService rag;
    public EvidenceRetrieverAgent(JavaMedicalRagService rag) { this.rag = rag; }
    @Override public String name() { return "EvidenceRetrieverAgent"; }
    @Override public void execute(AgentContext context) {
        context.setRag(rag.retrieve(context.getQuestion(), context.getImage(), context.getImageMediaType()));
        context.getBlackboard().publish(name(), "evidence", context.getRag().evidence(),
                "evidence=" + context.getRag().evidence().size() + ", degradations=" + context.getRag().degradations());
    }
}
