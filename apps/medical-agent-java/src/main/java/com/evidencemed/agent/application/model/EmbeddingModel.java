package com.evidencemed.agent.application.model;

import java.util.List;

public interface EmbeddingModel {
    List<List<Float>> embedTexts(List<String> texts);
    List<Float> embedMultimodal(String text, byte[] image, String mediaType);
}
