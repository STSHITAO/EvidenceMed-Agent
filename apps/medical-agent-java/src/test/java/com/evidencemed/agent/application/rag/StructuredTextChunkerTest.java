package com.evidencemed.agent.application.rag;

import com.evidencemed.agent.application.rag.document.BoundingBox;
import com.evidencemed.agent.application.rag.document.DocumentElement;
import com.evidencemed.agent.application.rag.document.DocumentElementType;
import com.evidencemed.agent.application.rag.document.PageProfile;
import com.evidencemed.agent.application.rag.document.ParsedDocument;
import com.evidencemed.agent.config.MedicalAgentProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredTextChunkerTest {
    @Test
    void preservesTableAndLocatorMetadata() {
        MedicalAgentProperties properties = new MedicalAgentProperties();
        properties.getKnowledge().setChunkSize(200);
        properties.getKnowledge().setChunkOverlap(20);
        TextChunker chunker = new TextChunker(properties);
        DocumentElement table = new DocumentElement("table-1", DocumentElementType.TABLE, 3, 4,
                List.of(new BoundingBox(10, 20, 300, 100)),
                "| Drug | Dose |\n| --- | --- |\n| Aspirin | 100 mg |",
                "Treatment / Medication", "", "text-table", 0.95, 0);
        ParsedDocument document = new ParsedDocument("test-parser-v2",
                List.of(new PageProfile(1, 600, 800, 100, 0, 0.0, true, false, 1, List.of())),
                List.of(table), List.of());

        List<TextChunker.ChunkDraft> chunks = chunker.chunk(document);

        assertThat(chunks).singleElement().satisfies(chunk -> {
            assertThat(chunk.objectType()).isEqualTo("TABLE");
            assertThat(chunk.sectionPath()).isEqualTo("Treatment / Medication");
            assertThat(chunk.pageFrom()).isEqualTo(3);
            assertThat(chunk.pageTo()).isEqualTo(4);
            assertThat(chunk.boundingBoxes()).contains("\"x\":10.00");
            assertThat(chunk.parserVersion()).isEqualTo("test-parser-v2");
            assertThat(chunk.content()).contains("Aspirin");
        });
    }
}
