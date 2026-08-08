package com.evidencemed.agent.infrastructure.document;

import com.evidencemed.agent.application.rag.document.BoundingBox;
import com.evidencemed.agent.application.rag.document.PdfOcrEngine;
import com.evidencemed.agent.config.MedicalAgentProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
public class PaddleOcrHttpClient implements PdfOcrEngine {
    private final WebClient webClient;
    private final MedicalAgentProperties.Pdf properties;

    public PaddleOcrHttpClient(WebClient.Builder builder, MedicalAgentProperties properties) {
        this.properties = properties.getKnowledge().getPdf();
        this.webClient = builder.baseUrl(this.properties.getOcrBaseUrl()).build();
    }

    @Override
    public boolean available() {
        return properties.isOcrEnabled() && properties.getOcrBaseUrl() != null
                && !properties.getOcrBaseUrl().isBlank();
    }

    @Override
    public OcrResult recognize(BufferedImage image, int pageNumber) {
        if (!available()) throw new IllegalStateException("PaddleOCR 未启用");
        OcrResponse response = webClient.post()
                .uri(properties.getOcrPath())
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    if (properties.getOcrApiKey() != null && !properties.getOcrApiKey().isBlank()) {
                        headers.setBearerAuth(properties.getOcrApiKey());
                    }
                })
                .bodyValue(Map.of("image_base64", encodePng(image), "page", pageNumber))
                .retrieve()
                .bodyToMono(OcrResponse.class)
                .block(Duration.ofSeconds(properties.getOcrTimeoutSeconds()));
        if (response == null) throw new IllegalStateException("PaddleOCR 返回空响应");
        List<OcrBlock> blocks = new ArrayList<>();
        for (OcrLine line : response.lines() == null ? List.<OcrLine>of() : response.lines()) {
            if (line == null || line.text() == null || line.text().isBlank()) continue;
            blocks.add(new OcrBlock(line.text(), box(line.bbox(), image),
                    line.confidence() == null ? 0.8 : line.confidence()));
        }
        if (blocks.isEmpty() && response.text() != null && !response.text().isBlank()) {
            blocks.add(new OcrBlock(response.text(), new BoundingBox(0, 0,
                    image.getWidth(), image.getHeight()), response.qualityScore() == null
                    ? 0.7 : response.qualityScore()));
        }
        double quality = response.qualityScore() == null
                ? blocks.stream().mapToDouble(OcrBlock::confidence).average().orElse(0.0)
                : response.qualityScore();
        return new OcrResult(blocks, quality);
    }

    private String encodePng(BufferedImage image) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("OCR 页面编码失败", exception);
        }
    }

    private BoundingBox box(List<Double> values, BufferedImage image) {
        if (values == null || values.size() < 4) {
            return new BoundingBox(0, 0, image.getWidth(), image.getHeight());
        }
        return new BoundingBox(nonNegative(values.get(0)), nonNegative(values.get(1)),
                nonNegative(values.get(2)), nonNegative(values.get(3)));
    }

    private double nonNegative(Double value) {
        return value == null ? 0.0 : Math.max(0.0, value);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OcrResponse(String text, List<OcrLine> lines, Double qualityScore) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OcrLine(String text, List<Double> bbox, Double confidence) {}
}
