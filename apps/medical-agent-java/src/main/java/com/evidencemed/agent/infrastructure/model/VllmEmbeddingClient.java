package com.evidencemed.agent.infrastructure.model;

import com.evidencemed.agent.application.model.EmbeddingModel;
import com.evidencemed.agent.config.MedicalAgentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class VllmEmbeddingClient extends VllmClientSupport implements EmbeddingModel {
    private static final String MULTIMODAL_INSTRUCTION = "Represent this medical image and question for evidence retrieval.";

    public VllmEmbeddingClient(MedicalAgentProperties properties, WebClient.Builder builder) {
        super(properties.getEmbedding(), builder);
    }

    @Override
    public List<List<Float>> embedTexts(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        Map<String, Object> body = Map.of("model", endpoint.getModel(), "input", texts);
        return parse(post(body));
    }

    @Override
    public List<Float> embedMultimodal(String text, byte[] image, String mediaType) {
        List<Map<String, Object>> userContent = new ArrayList<>();
        if (image != null && image.length > 0) {
            userContent.add(Map.of("type", "image_url", "image_url", Map.of("url", dataUrl(image, mediaType))));
        }
        userContent.add(Map.of("type", "text", "text", text == null ? "" : text));
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content", List.of(Map.of("type", "text", "text", MULTIMODAL_INSTRUCTION))),
                Map.of("role", "user", "content", userContent),
                Map.of("role", "assistant", "content", List.of(Map.of("type", "text", "text", "")))
        );
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", endpoint.getModel());
        body.put("messages", messages);
        body.put("encoding_format", "float");
        body.put("continue_final_message", true);
        body.put("add_special_tokens", true);
        List<List<Float>> vectors = parse(post(body));
        if (vectors.isEmpty()) {
            throw new ModelServiceException("embedding", "Embedding 服务未返回向量", null);
        }
        return vectors.get(0);
    }

    private JsonNode post(Object body) {
        try {
            return client.post().uri(endpoint.getPath()).bodyValue(body).retrieve()
                    .bodyToMono(JsonNode.class).block(timeout());
        } catch (RuntimeException exception) {
            throw new ModelServiceException("embedding", "Embedding 服务调用失败", exception);
        }
    }

    private List<List<Float>> parse(JsonNode body) {
        if (body == null) {
            return List.of();
        }
        JsonNode data = body.path("data");
        if (data.isArray()) {
            List<JsonNode> ordered = new ArrayList<>();
            data.forEach(ordered::add);
            ordered.sort(Comparator.comparingInt(node -> node.path("index").asInt(0)));
            return ordered.stream().map(node -> vector(node.path("embedding"))).toList();
        }
        JsonNode embeddings = body.path("embeddings");
        if (!embeddings.isArray()) {
            embeddings = body.path("output").path("embeddings");
        }
        if (embeddings.isArray()) {
            List<List<Float>> result = new ArrayList<>();
            embeddings.forEach(node -> result.add(vector(node)));
            return result;
        }
        throw new ModelServiceException("embedding", "Embedding 响应格式不受支持", null);
    }

    private List<Float> vector(JsonNode node) {
        List<Float> values = new ArrayList<>();
        node.forEach(value -> values.add((float) value.asDouble()));
        return List.copyOf(values);
    }
}
