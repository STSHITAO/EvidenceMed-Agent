package com.evidencemed.agent.infrastructure.model;

import com.evidencemed.agent.application.model.VisionLanguageModel;
import com.evidencemed.agent.config.MedicalAgentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class VllmVisionLanguageClient extends VllmClientSupport implements VisionLanguageModel {
    public VllmVisionLanguageClient(MedicalAgentProperties properties, WebClient.Builder builder) {
        super(properties.getVlm(), builder);
    }

    @Override
    public String generate(String systemPrompt, String userPrompt, byte[] image, String mediaType,
                           int maxTokens, double temperature) {
        List<Map<String, Object>> content = new ArrayList<>();
        if (image != null && image.length > 0) {
            content.add(Map.of("type", "image_url", "image_url", Map.of("url", dataUrl(image, mediaType))));
        }
        content.add(Map.of("type", "text", "text", userPrompt));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", endpoint.getModel());
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", content)));
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);
        body.put("stream", false);
        try {
            JsonNode response = client.post().uri(endpoint.getPath()).bodyValue(body).retrieve()
                    .bodyToMono(JsonNode.class).block(timeout());
            return extractText(response);
        } catch (ModelServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ModelServiceException("vlm", "视觉语言模型调用失败", exception);
        }
    }

    private String extractText(JsonNode body) {
        JsonNode content = body == null ? null : body.path("choices").path(0).path("message").path("content");
        if (content == null || content.isMissingNode()) {
            throw new ModelServiceException("vlm", "视觉语言模型响应格式不受支持", null);
        }
        if (content.isTextual()) {
            return content.asText().strip();
        }
        if (content.isArray()) {
            List<String> parts = new ArrayList<>();
            content.forEach(item -> {
                if (item.path("text").isTextual()) {
                    parts.add(item.path("text").asText());
                }
            });
            return String.join("\n", parts).strip();
        }
        throw new ModelServiceException("vlm", "视觉语言模型未返回文本", null);
    }
}
