package com.evidencemed.agent.application.rag;

public record KnowledgeIngestionResult(String documentId, String status, int chunks, boolean duplicate) {}
