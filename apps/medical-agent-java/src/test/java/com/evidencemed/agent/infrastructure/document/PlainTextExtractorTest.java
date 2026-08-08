package com.evidencemed.agent.infrastructure.document;

import com.evidencemed.agent.application.rag.document.DocumentElementType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PlainTextExtractorTest {
    @Test
    void keepsMarkdownAndTextInStructuredContract() {
        PlainTextExtractor extractor = new PlainTextExtractor();

        var parsed = extractor.extract("# Guideline\nClinical evidence.".getBytes(StandardCharsets.UTF_8));

        assertThat(parsed.parserVersion()).isEqualTo("plain-text-v1");
        assertThat(parsed.pageCount()).isEqualTo(1);
        assertThat(parsed.elements()).singleElement().satisfies(element -> {
            assertThat(element.type()).isEqualTo(DocumentElementType.PARAGRAPH);
            assertThat(element.content()).contains("Guideline", "Clinical evidence");
        });
    }
}
