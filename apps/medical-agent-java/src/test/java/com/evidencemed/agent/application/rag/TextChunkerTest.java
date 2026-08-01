package com.evidencemed.agent.application.rag;

import com.evidencemed.agent.config.MedicalAgentProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextChunkerTest {
    @Test
    void chunksLongTextWithBoundedOverlap() {
        MedicalAgentProperties properties = new MedicalAgentProperties();
        properties.getKnowledge().setChunkSize(120);
        properties.getKnowledge().setChunkOverlap(20);
        TextChunker chunker = new TextChunker(properties);

        String text = "# 胸部影像指南\n" + "肺部结节需要结合大小、密度与随访变化判断。".repeat(30);

        var chunks = chunker.chunk(text);

        assertThat(chunks).hasSizeGreaterThan(2);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.content()).isNotBlank();
            assertThat(chunk.content().length()).isLessThanOrEqualTo(120);
        });
    }
}
