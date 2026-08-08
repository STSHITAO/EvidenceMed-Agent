package com.evidencemed.agent.application.rag;

import com.evidencemed.agent.application.rag.document.BoundingBox;
import com.evidencemed.agent.application.rag.document.DocumentElement;
import com.evidencemed.agent.application.rag.document.DocumentElementType;
import com.evidencemed.agent.application.rag.document.ParsedDocument;
import com.evidencemed.agent.config.MedicalAgentProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class TextChunker {
    private final MedicalAgentProperties properties;

    public TextChunker(MedicalAgentProperties properties) {
        this.properties = properties;
    }

    public List<ChunkDraft> chunk(String rawText) {
        if (rawText == null || rawText.isBlank()) return List.of();
        return chunk(ParsedDocument.plainText(rawText));
    }

    public List<ChunkDraft> chunk(ParsedDocument document) {
        validateParameters();
        List<ChunkDraft> chunks = new ArrayList<>();
        List<DocumentElement> buffer = new ArrayList<>();
        for (DocumentElement element : document.elements()) {
            if (element.content().isBlank() || element.type() == DocumentElementType.FIGURE) continue;
            if (isAtomic(element)) {
                flush(buffer, document.parserVersion(), chunks);
                addElement(element, document.parserVersion(), chunks, true);
                continue;
            }
            if (!buffer.isEmpty() && (!sameSection(buffer.get(0), element)
                    || joinedLength(buffer, element) > properties.getKnowledge().getChunkSize())) {
                flush(buffer, document.parserVersion(), chunks);
            }
            buffer.add(element);
        }
        flush(buffer, document.parserVersion(), chunks);
        List<ChunkDraft> numbered = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            ChunkDraft value = chunks.get(i);
            numbered.add(value.withOrdinal(i));
        }
        return List.copyOf(numbered);
    }

    private boolean isAtomic(DocumentElement element) {
        return element.type() == DocumentElementType.TABLE || element.type() == DocumentElementType.CAPTION;
    }

    private void flush(List<DocumentElement> buffer, String parserVersion, List<ChunkDraft> target) {
        if (buffer.isEmpty()) return;
        StringBuilder content = new StringBuilder();
        for (DocumentElement element : buffer) {
            if (!content.isEmpty()) content.append("\n");
            content.append(element.content());
        }
        DocumentElement first = buffer.get(0);
        DocumentElement last = buffer.get(buffer.size() - 1);
        List<BoundingBox> boxes = buffer.stream().flatMap(item -> item.boundingBoxes().stream()).toList();
        double quality = buffer.stream().mapToDouble(DocumentElement::qualityScore).min().orElse(1.0);
        String type = buffer.stream().map(DocumentElement::type).distinct().count() == 1
                ? first.type().name() : "SECTION";
        addSplitDrafts(content.toString(), first.sectionPath(), first.pageFrom(), last.pageTo(), type,
                boxes, parserVersion, quality, target, false);
        buffer.clear();
    }

    private void addElement(DocumentElement element, String parserVersion, List<ChunkDraft> target,
            boolean atomic) {
        addSplitDrafts(element.content(), element.sectionPath(), element.pageFrom(), element.pageTo(),
                element.type().name(), element.boundingBoxes(), parserVersion, element.qualityScore(),
                target, atomic);
    }

    private void addSplitDrafts(String raw, String sectionPath, int pageFrom, int pageTo,
            String objectType, List<BoundingBox> boxes, String parserVersion, double quality,
            List<ChunkDraft> target, boolean atomic) {
        String normalized = normalize(raw);
        int chunkSize = properties.getKnowledge().getChunkSize();
        if (atomic && objectType.equals(DocumentElementType.TABLE.name())) {
            for (String value : splitTable(normalized, chunkSize)) {
                target.add(draft(value, sectionPath, pageFrom, pageTo, objectType, boxes,
                        parserVersion, quality));
            }
            return;
        }
        for (String value : splitText(normalized, chunkSize,
                properties.getKnowledge().getChunkOverlap())) {
            target.add(draft(value, sectionPath, pageFrom, pageTo, objectType, boxes,
                    parserVersion, quality));
        }
    }

    private ChunkDraft draft(String content, String sectionPath, int pageFrom, int pageTo,
            String objectType, List<BoundingBox> boxes, String parserVersion, double quality) {
        String title = leafSection(sectionPath);
        if (title.isBlank()) title = sectionTitle(content);
        return new ChunkDraft(-1, title, content, pageFrom, pageTo, objectType,
                sectionPath, boxesJson(boxes), parserVersion, quality);
    }

    private List<String> splitTable(String markdown, int chunkSize) {
        List<String> rows = markdown.lines().filter(line -> !line.isBlank()).toList();
        if (markdown.length() <= chunkSize || rows.size() <= 2) return List.of(markdown);
        String header = rows.get(0) + "\n" + rows.get(1);
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder(header);
        for (int i = 2; i < rows.size(); i++) {
            if (current.length() + rows.get(i).length() + 1 > chunkSize && current.length() > header.length()) {
                values.add(current.toString());
                current = new StringBuilder(header);
            }
            current.append('\n').append(rows.get(i));
        }
        if (current.length() > header.length()) values.add(current.toString());
        return values.isEmpty() ? List.of(markdown) : values;
    }

    private List<String> splitText(String text, int chunkSize, int overlap) {
        if (text.length() <= chunkSize) return List.of(text);
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int desiredEnd = Math.min(text.length(), start + chunkSize);
            int end = findBoundary(text, start, desiredEnd);
            String value = text.substring(start, end).strip();
            if (!value.isBlank()) result.add(value);
            if (end >= text.length()) break;
            start = Math.max(start + 1, end - overlap);
        }
        return result;
    }

    private int findBoundary(String text, int start, int desiredEnd) {
        if (desiredEnd >= text.length()) return text.length();
        int minimum = start + Math.max(50, (desiredEnd - start) * 2 / 3);
        for (int i = desiredEnd; i >= minimum; i--) {
            char value = text.charAt(i - 1);
            if (value == '\n' || value == '。' || value == '！' || value == '？'
                    || value == '.' || value == ';') return i;
        }
        return desiredEnd;
    }

    private boolean sameSection(DocumentElement first, DocumentElement next) {
        return first.sectionPath().equals(next.sectionPath());
    }

    private int joinedLength(List<DocumentElement> elements, DocumentElement next) {
        return elements.stream().mapToInt(item -> item.content().length() + 1).sum() + next.content().length();
    }

    private void validateParameters() {
        int chunkSize = properties.getKnowledge().getChunkSize();
        int overlap = properties.getKnowledge().getChunkOverlap();
        if (chunkSize < 100 || overlap < 0 || overlap >= chunkSize) {
            throw new IllegalStateException("知识切块参数无效");
        }
    }

    private String normalize(String value) {
        return value.replace("\r\n", "\n").replaceAll("[\\t ]+", " ")
                .replaceAll("\n{3,}", "\n\n").strip();
    }

    private String leafSection(String path) {
        if (path == null || path.isBlank()) return "";
        String[] values = path.split(" / ");
        String value = values[values.length - 1].strip();
        return value.length() <= 120 ? value : value.substring(0, 120);
    }

    private String sectionTitle(String content) {
        String first = content.lines().findFirst().orElse("").replaceFirst("^#{1,6}\\s*", "").strip();
        return first.length() <= 120 ? first : first.substring(0, 120);
    }

    private String boxesJson(List<BoundingBox> boxes) {
        return boxes.stream().map(box -> String.format(Locale.ROOT,
                "{\"x\":%.2f,\"y\":%.2f,\"width\":%.2f,\"height\":%.2f}",
                box.x(), box.y(), box.width(), box.height())).reduce((a, b) -> a + "," + b)
                .map(value -> "[" + value + "]").orElse("[]");
    }

    public record ChunkDraft(int ordinal, String sectionTitle, String content,
                             int pageFrom, int pageTo, String objectType, String sectionPath,
                             String boundingBoxes, String parserVersion, double qualityScore) {
        public ChunkDraft withOrdinal(int value) {
            return new ChunkDraft(value, sectionTitle, content, pageFrom, pageTo, objectType,
                    sectionPath, boundingBoxes, parserVersion, qualityScore);
        }
    }
}
