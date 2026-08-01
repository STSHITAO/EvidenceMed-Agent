package com.evidencemed.agent.infrastructure.model;

import com.evidencemed.agent.application.model.RerankerModel;
import com.evidencemed.agent.config.MedicalAgentProperties;
import com.evidencemed.agent.domain.knowledge.RetrievedEvidence;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class VllmRerankerClient extends VllmClientSupport implements RerankerModel {
    public VllmRerankerClient(MedicalAgentProperties properties, WebClient.Builder builder) {
        super(properties.getReranker(), builder);
    }

    @Override
    public List<RetrievedEvidence> rerank(String query, List<RetrievedEvidence> candidates, int topK) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        Map<String, Object> body = Map.of(
                "model", endpoint.getModel(),
                "query", query,
                "documents", candidates.stream().map(RetrievedEvidence::content).toList(),
                "top_n", candidates.size());
        try {
            JsonNode response = client.post().uri(endpoint.getPath()).bodyValue(body).retrieve()
                    .bodyToMono(JsonNode.class).block(timeout());
            double[] scores = scores(response, candidates.size());
            List<RetrievedEvidence> reranked = new ArrayList<>();
            for (int index = 0; index < candidates.size(); index++) {
                reranked.add(candidates.get(index).withScore(scores[index], "reranker"));
            }
            return reranked.stream().sorted(Comparator.comparingDouble(RetrievedEvidence::score).reversed())
                    .limit(Math.max(0, topK)).toList();
        } catch (ModelServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ModelServiceException("reranker", "Reranker 服务调用失败", exception);
        }
    }

    private double[] scores(JsonNode body, int count) {
        double[] scores = new double[count];
        if (body == null) {
            throw new ModelServiceException("reranker", "Reranker 返回空响应", null);
        }
        JsonNode items = body.has("results") ? body.path("results") : body.path("data");
        if (items.isArray()) {
            for (JsonNode item : items) {
                int index = item.path("index").asInt(-1);
                if (index >= 0 && index < count) {
                    scores[index] = item.has("relevance_score")
                            ? item.path("relevance_score").asDouble()
                            : item.path("score").asDouble();
                }
            }
            return scores;
        }
        JsonNode scoreArray = body.path("scores");
        if (scoreArray.isArray()) {
            for (int index = 0; index < Math.min(count, scoreArray.size()); index++) {
                scores[index] = scoreArray.get(index).asDouble();
            }
            return scores;
        }
        throw new ModelServiceException("reranker", "Reranker 响应格式不受支持", null);
    }
}
