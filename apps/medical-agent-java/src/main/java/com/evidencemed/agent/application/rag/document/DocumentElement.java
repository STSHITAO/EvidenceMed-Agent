package com.evidencemed.agent.application.rag.document;

import java.util.ArrayList;
import java.util.List;

public record DocumentElement(
        String id,
        DocumentElementType type,
        int pageFrom,
        int pageTo,
        List<BoundingBox> boundingBoxes,
        String content,
        String sectionPath,
        String relatedElementId,
        String extractionMethod,
        double qualityScore,
        int headingLevel
) {
    public DocumentElement {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("文档对象 ID 不能为空");
        if (type == null) throw new IllegalArgumentException("文档对象类型不能为空");
        if (pageFrom < 1 || pageTo < pageFrom) throw new IllegalArgumentException("文档对象页码无效");
        boundingBoxes = boundingBoxes == null ? List.of() : List.copyOf(boundingBoxes);
        content = content == null ? "" : content.strip();
        sectionPath = sectionPath == null ? "" : sectionPath.strip();
        relatedElementId = relatedElementId == null ? "" : relatedElementId;
        extractionMethod = extractionMethod == null ? "text-layer" : extractionMethod;
        qualityScore = Math.max(0.0, Math.min(1.0, qualityScore));
        headingLevel = Math.max(0, Math.min(6, headingLevel));
    }

    public DocumentElement withSectionPath(String value) {
        return new DocumentElement(id, type, pageFrom, pageTo, boundingBoxes, content, value,
                relatedElementId, extractionMethod, qualityScore, headingLevel);
    }

    public DocumentElement withRelation(String value) {
        return new DocumentElement(id, type, pageFrom, pageTo, boundingBoxes, content, sectionPath,
                value, extractionMethod, qualityScore, headingLevel);
    }

    public DocumentElement merge(DocumentElement other, String mergedContent) {
        List<BoundingBox> boxes = new ArrayList<>(boundingBoxes);
        boxes.addAll(other.boundingBoxes);
        return new DocumentElement(id, type, Math.min(pageFrom, other.pageFrom),
                Math.max(pageTo, other.pageTo), boxes, mergedContent, sectionPath,
                relatedElementId, extractionMethod.equals(other.extractionMethod)
                        ? extractionMethod : "mixed",
                Math.min(qualityScore, other.qualityScore), headingLevel);
    }
}
