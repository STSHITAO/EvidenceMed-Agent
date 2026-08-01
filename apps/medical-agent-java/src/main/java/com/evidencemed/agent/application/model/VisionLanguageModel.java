package com.evidencemed.agent.application.model;

public interface VisionLanguageModel {
    String generate(String systemPrompt, String userPrompt, byte[] image, String mediaType,
                    int maxTokens, double temperature);
}
