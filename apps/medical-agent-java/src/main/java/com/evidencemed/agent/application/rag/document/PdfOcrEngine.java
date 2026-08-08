package com.evidencemed.agent.application.rag.document;

import java.awt.image.BufferedImage;
import java.util.List;

public interface PdfOcrEngine {
    boolean available();

    OcrResult recognize(BufferedImage image, int pageNumber);

    record OcrBlock(String text, BoundingBox boundingBox, double confidence) {
        public OcrBlock {
            text = text == null ? "" : text.strip();
            confidence = Math.max(0.0, Math.min(1.0, confidence));
        }
    }

    record OcrResult(List<OcrBlock> blocks, double qualityScore) {
        public OcrResult {
            blocks = blocks == null ? List.of() : List.copyOf(blocks);
            qualityScore = Math.max(0.0, Math.min(1.0, qualityScore));
        }
    }
}
