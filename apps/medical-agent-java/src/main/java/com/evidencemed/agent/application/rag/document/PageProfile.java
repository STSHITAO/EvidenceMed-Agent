package com.evidencemed.agent.application.rag.document;

import java.util.List;

public record PageProfile(
        int pageNumber,
        double width,
        double height,
        int textCharacters,
        int imageCount,
        double imageCoverage,
        boolean textLayerReliable,
        boolean ocrUsed,
        double qualityScore,
        List<String> warnings
) {
    public PageProfile {
        if (pageNumber < 1) throw new IllegalArgumentException("页码必须从 1 开始");
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        imageCoverage = Math.max(0.0, Math.min(1.0, imageCoverage));
        qualityScore = Math.max(0.0, Math.min(1.0, qualityScore));
    }
}
