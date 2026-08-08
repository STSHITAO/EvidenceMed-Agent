package com.evidencemed.agent.application.rag.document;

import java.util.List;

public record ParsedDocument(
        String parserVersion,
        List<PageProfile> pages,
        List<DocumentElement> elements,
        List<String> warnings
) {
    public ParsedDocument {
        parserVersion = parserVersion == null || parserVersion.isBlank() ? "unknown" : parserVersion;
        pages = pages == null ? List.of() : List.copyOf(pages);
        elements = elements == null ? List.of() : List.copyOf(elements);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public int pageCount() {
        return pages.size();
    }

    public double qualityScore() {
        return pages.stream().mapToDouble(PageProfile::qualityScore).average().orElse(1.0);
    }

    public static ParsedDocument plainText(String text) {
        PageProfile page = new PageProfile(1, 0, 0, text.length(), 0, 0.0,
                true, false, 1.0, List.of());
        DocumentElement element = new DocumentElement("text-1", DocumentElementType.PARAGRAPH,
                1, 1, List.of(), text, "", "", "text", 1.0, 0);
        return new ParsedDocument("plain-text-v1", List.of(page), List.of(element), List.of());
    }
}
