package com.evidencemed.agent.application.rag;

import com.evidencemed.agent.domain.knowledge.RetrievedEvidence;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RrfFusionTest {
    @Test
    void rewardsEvidenceFoundByBothRetrievers() {
        RrfFusion fusion = new RrfFusion();
        RetrievedEvidence common = evidence("common");
        RetrievedEvidence denseOnly = evidence("dense");
        RetrievedEvidence sparseOnly = evidence("sparse");

        List<RetrievedEvidence> result = fusion.fuse(
                List.of(List.of(denseOnly, common), List.of(sparseOnly, common)), 60, 3);

        assertThat(result.get(0).chunkId()).isEqualTo("common");
    }

    private RetrievedEvidence evidence(String id) {
        return new RetrievedEvidence(id, "doc", "source", 0, id, "text", 1.0, "test");
    }
}
