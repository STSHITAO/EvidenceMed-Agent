package com.evidencemed.agent.application.rag;

public interface DocumentTextExtractor {
    boolean supports(String fileName, String mediaType);
    String extract(byte[] content);
}
