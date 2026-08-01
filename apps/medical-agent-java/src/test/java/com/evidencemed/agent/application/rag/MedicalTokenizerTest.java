package com.evidencemed.agent.application.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MedicalTokenizerTest {
    private final MedicalTokenizer tokenizer = new MedicalTokenizer();

    @Test
    void tokenizesChineseBigramsAndLatinMedicalTerms() {
        assertThat(tokenizer.tokenize("肺部 CT nodule"))
                .contains("肺部", "ct", "nodule");
    }
}
