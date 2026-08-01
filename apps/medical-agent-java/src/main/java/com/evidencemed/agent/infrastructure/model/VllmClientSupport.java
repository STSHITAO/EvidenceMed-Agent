package com.evidencemed.agent.infrastructure.model;

import com.evidencemed.agent.config.MedicalAgentProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Base64;

abstract class VllmClientSupport {
    protected final MedicalAgentProperties.ModelEndpoint endpoint;
    protected final WebClient client;

    protected VllmClientSupport(MedicalAgentProperties.ModelEndpoint endpoint, WebClient.Builder builder) {
        this.endpoint = endpoint;
        WebClient.Builder configured = builder.clone().baseUrl(endpoint.getBaseUrl());
        if (endpoint.getApiKey() != null && !endpoint.getApiKey().isBlank()) {
            configured.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + endpoint.getApiKey());
        }
        this.client = configured.build();
    }

    protected Duration timeout() {
        return Duration.ofSeconds(endpoint.getTimeoutSeconds());
    }

    protected String dataUrl(byte[] image, String mediaType) {
        String safeType = mediaType == null || mediaType.isBlank() ? "image/png" : mediaType;
        return "data:" + safeType + ";base64," + Base64.getEncoder().encodeToString(image);
    }
}
