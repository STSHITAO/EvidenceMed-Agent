package com.evidencemed.agent.infrastructure.milvus;

import com.evidencemed.agent.application.rag.VectorStore;
import com.evidencemed.agent.config.MedicalAgentProperties;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MilvusVectorStore implements VectorStore {
    private static final Logger log = LoggerFactory.getLogger(MilvusVectorStore.class);
    private final MedicalAgentProperties properties;
    private volatile MilvusClientV2 client;

    public MilvusVectorStore(MedicalAgentProperties properties) { this.properties = properties; }

    @Override
    public void upsert(List<VectorRecord> records) {
        if (!properties.getMilvus().isEnabled() || records.isEmpty()) return;
        try {
            validateDimension(records.get(0).vector());
            MilvusClientV2 milvus = client();
            ensureCollection(milvus);
            List<JsonObject> rows = new ArrayList<>();
            for (VectorRecord record : records) {
                JsonObject row = new JsonObject();
                row.addProperty("id", record.chunkId());
                row.addProperty("document_id", record.documentId());
                JsonArray vector = new JsonArray();
                record.vector().forEach(vector::add);
                row.add("vector", vector);
                rows.add(row);
            }
            milvus.upsert(UpsertReq.builder().collectionName(collection()).data(rows).build());
        } catch (Exception exception) {
            log.warn("Milvus upsert unavailable; knowledge remains searchable through BM25");
        }
    }

    @Override
    public List<VectorHit> search(List<Float> vector, int topK) {
        if (!properties.getMilvus().isEnabled()) return List.of();
        try {
            validateDimension(vector);
            MilvusClientV2 milvus = client();
            ensureCollection(milvus);
            SearchResp response = milvus.search(SearchReq.builder().collectionName(collection())
                    .annsField("vector").metricType(IndexParam.MetricType.COSINE).topK(topK)
                    .outputFields(List.of("document_id")).data(List.of(new FloatVec(vector))).build());
            if (response.getSearchResults().isEmpty()) return List.of();
            return response.getSearchResults().get(0).stream()
                    .map(hit -> new VectorHit(String.valueOf(hit.getId()),
                            String.valueOf(hit.getEntity().getOrDefault("document_id", "")), hit.getScore()))
                    .toList();
        } catch (Exception exception) {
            log.warn("Milvus search unavailable; Java RAG will continue with BM25");
            return List.of();
        }
    }

    private synchronized MilvusClientV2 client() {
        if (client == null) {
            ConnectConfig.ConnectConfigBuilder<?, ?> builder = ConnectConfig.builder()
                    .uri(properties.getMilvus().getUri()).connectTimeoutMs(3000).rpcDeadlineMs(10000);
            if (!properties.getMilvus().getToken().isBlank()) builder.token(properties.getMilvus().getToken());
            client = new MilvusClientV2(builder.build());
        }
        return client;
    }

    private void ensureCollection(MilvusClientV2 milvus) {
        if (!milvus.hasCollection(HasCollectionReq.builder().collectionName(collection()).build())) {
            milvus.createCollection(CreateCollectionReq.builder().collectionName(collection())
                    .dimension(properties.getMilvus().getDimension()).primaryFieldName("id")
                    .idType(DataType.VarChar).maxLength(36).vectorFieldName("vector")
                    .metricType("COSINE").autoID(false).enableDynamicField(true).build());
        }
    }

    private String collection() { return properties.getMilvus().getCollection(); }

    private void validateDimension(List<Float> vector) {
        int expected = properties.getMilvus().getDimension();
        if (vector.size() != expected) {
            throw new IllegalArgumentException("Embedding 维度为 " + vector.size() + "，配置值为 " + expected);
        }
    }
}
