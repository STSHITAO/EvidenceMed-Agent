package com.evidencemed.agent.application.rag;

import com.evidencemed.agent.application.model.EmbeddingModel;
import com.evidencemed.agent.config.MedicalAgentProperties;
import com.evidencemed.agent.domain.knowledge.KnowledgeChunk;
import com.evidencemed.agent.domain.knowledge.KnowledgeDocument;
import com.evidencemed.agent.infrastructure.model.ModelServiceException;
import com.evidencemed.agent.infrastructure.persistence.KnowledgeChunkRepository;
import com.evidencemed.agent.infrastructure.persistence.KnowledgeDocumentRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
public class KnowledgeIngestionService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestionService.class);
    private static final int BATCH_SIZE = 8;
    private final List<DocumentTextExtractor> extractors;
    private final TextChunker chunker;
    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;
    private final KnowledgeDocumentRepository documents;
    private final KnowledgeChunkRepository chunks;
    private final Bm25Index bm25;
    private final MedicalAgentProperties properties;

    public KnowledgeIngestionService(List<DocumentTextExtractor> extractors, TextChunker chunker,
            EmbeddingModel embeddingModel, VectorStore vectorStore,
            KnowledgeDocumentRepository documents, KnowledgeChunkRepository chunks,
            Bm25Index bm25, MedicalAgentProperties properties) {
        this.extractors = List.copyOf(extractors);
        this.chunker = chunker;
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
        this.documents = documents;
        this.chunks = chunks;
        this.bm25 = bm25;
        this.properties = properties;
    }

    @Transactional
    public KnowledgeIngestionResult ingest(String fileName, String mediaType, byte[] content) {
        validate(fileName, content);
        String hash = sha256(content);
        KnowledgeDocument existing = documents.findBySha256(hash).orElse(null);
        if (existing != null) {
            int count = chunks.findByDocumentIdOrderByChunkIndex(existing.getId()).size();
            return new KnowledgeIngestionResult(existing.getId(), existing.getStatus().name(), count, true);
        }
        DocumentTextExtractor extractor = extractors.stream()
                .filter(item -> item.supports(fileName, mediaType)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("仅支持 PDF、Markdown 和 TXT 知识文件"));
        KnowledgeDocument document = documents.save(new KnowledgeDocument(fileName,
                mediaType == null ? "application/octet-stream" : mediaType, hash, content.length));
        try {
            List<TextChunker.ChunkDraft> drafts = chunker.chunk(extractor.extract(content));
            if (drafts.isEmpty()) throw new IllegalArgumentException("知识文件没有可索引文本");
            List<KnowledgeChunk> saved = chunks.saveAll(drafts.stream()
                    .map(item -> new KnowledgeChunk(document.getId(), item.ordinal(), fileName,
                            item.sectionTitle(), item.content())).toList());
            indexVectors(saved);
            document.indexed();
            documents.save(document);
            bm25.rebuild(chunks.findAll());
            return new KnowledgeIngestionResult(document.getId(), document.getStatus().name(), saved.size(), false);
        } catch (RuntimeException exception) {
            document.failed(safeMessage(exception));
            documents.save(document);
            throw exception;
        }
    }

    public void rebuildSparseIndex() { bm25.rebuild(chunks.findAll()); }

    private void indexVectors(List<KnowledgeChunk> saved) {
        for (int start = 0; start < saved.size(); start += BATCH_SIZE) {
            List<KnowledgeChunk> batch = saved.subList(start, Math.min(saved.size(), start + BATCH_SIZE));
            try {
                List<List<Float>> values = embeddingModel.embedTexts(
                        batch.stream().map(KnowledgeChunk::getContent).toList());
                if (values.size() != batch.size()) throw new IllegalStateException("Embedding 返回数量不一致");
                List<VectorStore.VectorRecord> records = new ArrayList<>();
                for (int i = 0; i < batch.size(); i++) {
                    KnowledgeChunk chunk = batch.get(i);
                    records.add(new VectorStore.VectorRecord(chunk.getId(), chunk.getDocumentId(), values.get(i)));
                }
                vectorStore.upsert(records);
            } catch (ModelServiceException exception) {
                log.warn("Embedding unavailable; knowledge remains searchable through BM25");
                return;
            }
        }
    }

    private void validate(String name, byte[] value) {
        if (name == null || name.isBlank() || value == null || value.length == 0) {
            throw new IllegalArgumentException("知识文件不能为空");
        }
        if (value.length > properties.getUpload().getMaxBytes()) {
            throw new IllegalArgumentException("知识文件超过大小限制");
        }
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private String safeMessage(RuntimeException exception) {
        String value = exception.getMessage() == null ? "知识入库失败" : exception.getMessage();
        return value.substring(0, Math.min(500, value.length()));
    }
}
