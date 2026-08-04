package com.evidencemed.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "medical-agent")
public class MedicalAgentProperties {
    private final ModelEndpoint embedding = new ModelEndpoint(
            "http://127.0.0.1:8001", "/v1/embeddings", "Qwen/Qwen3-VL-Embedding-2B", 60);
    private final ModelEndpoint reranker = new ModelEndpoint(
            "http://127.0.0.1:8002", "/v1/rerank", "Qwen/Qwen3-VL-Reranker-2B", 60);
    private final ModelEndpoint vlm = new ModelEndpoint(
            "http://127.0.0.1:8003", "/v1/chat/completions", "qwen3-vl-medical", 120);
    private final Milvus milvus = new Milvus();
    private final Knowledge knowledge = new Knowledge();
    private final Rag rag = new Rag();
    private final Upload upload = new Upload();
    private final Memory memory = new Memory();
    private final Bootstrap bootstrap = new Bootstrap();

    public ModelEndpoint getEmbedding() { return embedding; }
    public ModelEndpoint getReranker() { return reranker; }
    public ModelEndpoint getVlm() { return vlm; }
    public Milvus getMilvus() { return milvus; }
    public Knowledge getKnowledge() { return knowledge; }
    public Rag getRag() { return rag; }
    public Upload getUpload() { return upload; }
    public Memory getMemory() { return memory; }
    public Bootstrap getBootstrap() { return bootstrap; }

    public static class ModelEndpoint {
        private String baseUrl;
        private String path;
        private String model;
        private String apiKey = "";
        private int timeoutSeconds;

        public ModelEndpoint(String baseUrl, String path, String model, int timeoutSeconds) {
            this.baseUrl = baseUrl;
            this.path = path;
            this.model = model;
            this.timeoutSeconds = timeoutSeconds;
        }

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }

    public static class Milvus {
        private boolean enabled = true;
        private String uri = "http://127.0.0.1:19530";
        private String token = "";
        private String collection = "medical_knowledge_chunks";
        private int dimension = 2048;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getUri() { return uri; }
        public void setUri(String uri) { this.uri = uri; }
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
        public String getCollection() { return collection; }
        public void setCollection(String collection) { this.collection = collection; }
        public int getDimension() { return dimension; }
        public void setDimension(int dimension) { this.dimension = dimension; }
    }

    public static class Knowledge {
        private int chunkSize = 500;
        private int chunkOverlap = 80;

        public int getChunkSize() { return chunkSize; }
        public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }
        public int getChunkOverlap() { return chunkOverlap; }
        public void setChunkOverlap(int chunkOverlap) { this.chunkOverlap = chunkOverlap; }
    }

    public static class Rag {
        private int recallTopK = 12;
        private int sparseTopK = 8;
        private int rerankTopK = 4;
        private int rrfK = 60;
        private double bm25K1 = 1.5;
        private double bm25B = 0.75;
        private boolean hydeEnabled = true;

        public int getRecallTopK() { return recallTopK; }
        public void setRecallTopK(int recallTopK) { this.recallTopK = recallTopK; }
        public int getSparseTopK() { return sparseTopK; }
        public void setSparseTopK(int sparseTopK) { this.sparseTopK = sparseTopK; }
        public int getRerankTopK() { return rerankTopK; }
        public void setRerankTopK(int rerankTopK) { this.rerankTopK = rerankTopK; }
        public int getRrfK() { return rrfK; }
        public void setRrfK(int rrfK) { this.rrfK = rrfK; }
        public double getBm25K1() { return bm25K1; }
        public void setBm25K1(double bm25K1) { this.bm25K1 = bm25K1; }
        public double getBm25B() { return bm25B; }
        public void setBm25B(double bm25B) { this.bm25B = bm25B; }
        public boolean isHydeEnabled() { return hydeEnabled; }
        public void setHydeEnabled(boolean hydeEnabled) { this.hydeEnabled = hydeEnabled; }
    }

    public static class Upload {
        private long maxBytes = 20L * 1024 * 1024;
        public long getMaxBytes() { return maxBytes; }
        public void setMaxBytes(long maxBytes) { this.maxBytes = maxBytes; }
    }

    public static class Memory {
        private int historyLimit = 12;
        private long ttlHours = 12;
        private int briefMaxChars = 800;
        public int getHistoryLimit() { return historyLimit; }
        public void setHistoryLimit(int historyLimit) { this.historyLimit = historyLimit; }
        public long getTtlHours() { return ttlHours; }
        public void setTtlHours(long ttlHours) { this.ttlHours = ttlHours; }
        public int getBriefMaxChars() { return briefMaxChars; }
        public void setBriefMaxChars(int briefMaxChars) { this.briefMaxChars = briefMaxChars; }
    }

    public static class Bootstrap {
        private boolean demoUsersEnabled = false;
        private String userPassword = "local-user-change-me";
        private String adminPassword = "local-admin-change-me";
        public boolean isDemoUsersEnabled() { return demoUsersEnabled; }
        public void setDemoUsersEnabled(boolean demoUsersEnabled) { this.demoUsersEnabled = demoUsersEnabled; }
        public String getUserPassword() { return userPassword; }
        public void setUserPassword(String userPassword) { this.userPassword = userPassword; }
        public String getAdminPassword() { return adminPassword; }
        public void setAdminPassword(String adminPassword) { this.adminPassword = adminPassword; }
    }
}
