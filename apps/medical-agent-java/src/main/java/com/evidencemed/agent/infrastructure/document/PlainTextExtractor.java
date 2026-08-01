package com.evidencemed.agent.infrastructure.document;

import com.evidencemed.agent.application.rag.DocumentTextExtractor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class PlainTextExtractor implements DocumentTextExtractor {
    @Override
    public boolean supports(String fileName, String mediaType) {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        return lower.endsWith(".md") || lower.endsWith(".txt")
                || "text/markdown".equalsIgnoreCase(mediaType)
                || "text/plain".equalsIgnoreCase(mediaType);
    }

    @Override
    public String extract(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8).replace("\u0000", "").strip();
        if (text.isBlank()) {
            throw new IllegalArgumentException("知识文件内容为空");
        }
        return text;
    }
}
