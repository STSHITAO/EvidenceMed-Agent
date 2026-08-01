package com.evidencemed.agent.application.rag;

import com.evidencemed.agent.config.MedicalAgentProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TextChunker {
    private final MedicalAgentProperties properties;

    public TextChunker(MedicalAgentProperties properties) {
        this.properties = properties;
    }

    public List<ChunkDraft> chunk(String rawText) {
        String normalized = rawText.replace("\r\n", "\n")
                .replaceAll("[\\t ]+", " ")
                .replaceAll("\n{3,}", "\n\n")
                .strip();
        int chunkSize = properties.getKnowledge().getChunkSize();
        int overlap = properties.getKnowledge().getChunkOverlap();
        if (chunkSize < 100 || overlap < 0 || overlap >= chunkSize) {
            throw new IllegalStateException("知识切块参数无效");
        }
        List<ChunkDraft> chunks = new ArrayList<>();
        int start = 0;
        int ordinal = 0;
        while (start < normalized.length()) {
            int desiredEnd = Math.min(normalized.length(), start + chunkSize);
            int end = findBoundary(normalized, start, desiredEnd);
            String content = normalized.substring(start, end).strip();
            if (!content.isBlank()) {
                chunks.add(new ChunkDraft(ordinal++, sectionTitle(content), content));
            }
            if (end >= normalized.length()) {
                break;
            }
            start = Math.max(start + 1, end - overlap);
        }
        return List.copyOf(chunks);
    }

    private int findBoundary(String text, int start, int desiredEnd) {
        if (desiredEnd >= text.length()) {
            return text.length();
        }
        int minimum = start + Math.max(50, (desiredEnd - start) * 2 / 3);
        for (int i = desiredEnd; i >= minimum; i--) {
            char value = text.charAt(i - 1);
            if (value == '\n' || value == '。' || value == '！' || value == '？' || value == '.' || value == ';') {
                return i;
            }
        }
        return desiredEnd;
    }

    private String sectionTitle(String content) {
        String first = content.lines().findFirst().orElse("").replaceFirst("^#{1,6}\\s*", "").strip();
        return first.length() <= 80 ? first : first.substring(0, 80);
    }

    public record ChunkDraft(int ordinal, String sectionTitle, String content) {
    }
}
