package com.evidencemed.agent.application.rag;

import com.evidencemed.agent.config.MedicalAgentProperties;
import com.evidencemed.agent.domain.knowledge.KnowledgeChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Bm25IndexTest {
    @Test
    void ranksChineseMedicalEvidence() {
        Bm25Index index = new Bm25Index(new MedicalAgentProperties(), new MedicalTokenizer());
        index.rebuild(List.of(
                new KnowledgeChunk("doc-1", 0, "胸痛指南", "急性胸痛伴呼吸困难应立即评估心肺急症"),
                new KnowledgeChunk("doc-2", 0, "皮肤指南", "轻度皮疹应避免抓挠并观察变化")
        ));

        assertThat(index.search("胸痛 呼吸困难", 2))
                .extracting(item -> item.source())
                .containsExactly("胸痛指南");
    }
}
