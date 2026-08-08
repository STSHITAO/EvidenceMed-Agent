package com.evidencemed.agent.application.rag;

import com.evidencemed.agent.application.model.EmbeddingModel;
import com.evidencemed.agent.application.model.RerankerModel;
import com.evidencemed.agent.application.model.VisionLanguageModel;
import com.evidencemed.agent.config.MedicalAgentProperties;
import com.evidencemed.agent.domain.knowledge.KnowledgeChunk;
import com.evidencemed.agent.domain.knowledge.RetrievedEvidence;
import com.evidencemed.agent.infrastructure.model.ModelServiceException;
import com.evidencemed.agent.infrastructure.persistence.KnowledgeChunkRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class JavaMedicalRagService {
    private static final String HYDE_SYSTEM = "你是医学证据检索助手，只生成假设性证据文本，不诊断、不解释。";
    private final Bm25Index bm25;
    private final VectorStore vectors;
    private final EmbeddingModel embeddingModel;
    private final RerankerModel rerankerModel;
    private final VisionLanguageModel languageModel;
    private final RrfFusion fusion;
    private final KnowledgeChunkRepository chunks;
    private final MedicalAgentProperties properties;

    public JavaMedicalRagService(Bm25Index bm25, VectorStore vectors, EmbeddingModel embeddingModel,
            RerankerModel rerankerModel, VisionLanguageModel languageModel, RrfFusion fusion,
            KnowledgeChunkRepository chunks, MedicalAgentProperties properties) {
        this.bm25 = bm25;
        this.vectors = vectors;
        this.embeddingModel = embeddingModel;
        this.rerankerModel = rerankerModel;
        this.languageModel = languageModel;
        this.fusion = fusion;
        this.chunks = chunks;
        this.properties = properties;
    }

    public RagResult retrieve(String question, byte[] image, String mediaType) {
        List<String> degradations = new ArrayList<>();
        String hyde = createHyde(question, degradations);
        String expanded = hyde.isBlank() ? question : question + "\n" + hyde;
        List<RetrievedEvidence> sparse = bm25.search(expanded, properties.getRag().getSparseTopK());
        List<RetrievedEvidence> dense = denseSearch(expanded, image, mediaType, degradations);
        List<RetrievedEvidence> fused = fusion.fuse(List.of(dense, sparse), properties.getRag().getRrfK(),
                properties.getRag().getRecallTopK());
        List<RetrievedEvidence> evidence = rerank(question, hyde, fused, degradations);
        if (evidence.isEmpty()) degradations.add("NO_EVIDENCE");
        return new RagResult(hyde, evidence, List.copyOf(degradations));
    }

    private String createHyde(String question, List<String> degradations) {
        if (!properties.getRag().isHydeEnabled()) return "";
        try {
            return languageModel.generate(HYDE_SYSTEM, question, null, null, 180, 0.1);
        } catch (ModelServiceException exception) {
            degradations.add("HYDE_UNAVAILABLE");
            return "";
        }
    }

    private List<RetrievedEvidence> denseSearch(String query, byte[] image, String mediaType,
            List<String> degradations) {
        try {
            List<Float> vector = image == null || image.length == 0
                    ? embeddingModel.embedTexts(List.of(query)).get(0)
                    : embeddingModel.embedMultimodal(query, image, mediaType);
            List<VectorStore.VectorHit> hits = vectors.search(vector, properties.getRag().getRecallTopK());
            Map<String, KnowledgeChunk> found = new HashMap<>();
            chunks.findAllById(hits.stream().map(VectorStore.VectorHit::chunkId).toList())
                    .forEach(item -> found.put(item.getId(), item));
            return hits.stream().filter(hit -> found.containsKey(hit.chunkId())).map(hit -> {
                KnowledgeChunk chunk = found.get(hit.chunkId());
                return new RetrievedEvidence(chunk.getId(), chunk.getDocumentId(), chunk.getSource(),
                        chunk.getChunkIndex(), chunk.getContent(), chunk.getModality(), hit.score(), "milvus",
                        chunk.getSectionPath(), chunk.getPageFrom(), chunk.getPageTo(), chunk.getObjectType());
            }).toList();
        } catch (RuntimeException exception) {
            degradations.add("DENSE_RETRIEVAL_UNAVAILABLE");
            return List.of();
        }
    }

    private List<RetrievedEvidence> rerank(String question, String hyde,
            List<RetrievedEvidence> candidates, List<String> degradations) {
        if (candidates.isEmpty()) return List.of();
        try {
            String query = hyde.isBlank() ? question : question + "\n" + hyde;
            return rerankerModel.rerank(query, candidates, properties.getRag().getRerankTopK());
        } catch (ModelServiceException exception) {
            degradations.add("RERANKER_UNAVAILABLE");
            return candidates.stream().limit(properties.getRag().getRerankTopK()).toList();
        }
    }
}
