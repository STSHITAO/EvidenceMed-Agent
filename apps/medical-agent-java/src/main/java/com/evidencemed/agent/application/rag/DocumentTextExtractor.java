package com.evidencemed.agent.application.rag;

import com.evidencemed.agent.application.rag.document.ParsedDocument;

public interface DocumentTextExtractor {
    boolean supports(String fileName, String mediaType);
    ParsedDocument extract(byte[] content);
}
