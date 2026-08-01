package com.evidencemed.agent.application.rag;

import com.evidencemed.agent.config.MedicalAgentProperties;
import com.evidencemed.agent.domain.knowledge.KnowledgeChunk;
import com.evidencemed.agent.domain.knowledge.RetrievedEvidence;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class Bm25Index {
    private static final Pattern LATIN_OR_NUMBER = Pattern.compile("[a-z0-9]+(?:[._/-][a-z0-9]+)*");
    private volatile Snapshot snapshot = Snapshot.empty();
    private final MedicalAgentProperties properties;
    private final MedicalTokenizer tokenizer;

    public Bm25Index(MedicalAgentProperties properties, MedicalTokenizer tokenizer) {
        this.properties = properties;
        this.tokenizer = tokenizer;
    }

    public synchronized void rebuild(List<KnowledgeChunk> chunks) {
        List<DocumentTerms> documents = new ArrayList<>();
        Map<String, Integer> documentFrequency = new HashMap<>();
        long tokenTotal = 0;
        for (KnowledgeChunk chunk : chunks) {
            List<String> tokens = tokenizer.tokenize(chunk.getContent());
            Map<String, Integer> frequencies = new HashMap<>();
            tokens.forEach(token -> frequencies.merge(token, 1, Integer::sum));
            frequencies.keySet().forEach(token -> documentFrequency.merge(token, 1, Integer::sum));
            tokenTotal += tokens.size();
            documents.add(new DocumentTerms(chunk, frequencies, tokens.size()));
        }
        double averageLength = documents.isEmpty() ? 0.0 : (double) tokenTotal / documents.size();
        snapshot = new Snapshot(List.copyOf(documents), Map.copyOf(documentFrequency), averageLength);
    }

    public List<RetrievedEvidence> search(String query, int topK) {
        Snapshot current = snapshot;
        if (current.documents().isEmpty() || query == null || query.isBlank()) return List.of();
        Set<String> queryTerms = new HashSet<>(tokenizer.tokenize(query));
        double k1 = properties.getRag().getBm25K1();
        double b = properties.getRag().getBm25B();
        int corpusSize = current.documents().size();
        List<RetrievedEvidence> results = new ArrayList<>();
        for (DocumentTerms document : current.documents()) {
            double score = 0.0;
            for (String term : queryTerms) {
                int frequency = document.termFrequency().getOrDefault(term, 0);
                if (frequency == 0) continue;
                int documentFrequency = current.documentFrequency().getOrDefault(term, 0);
                double idf = Math.log(1.0 + (corpusSize - documentFrequency + 0.5) / (documentFrequency + 0.5));
                double norm = frequency + k1 * (1.0 - b + b * document.length() / current.averageLength());
                score += idf * frequency * (k1 + 1.0) / norm;
            }
            if (score > 0.0) {
                KnowledgeChunk chunk = document.chunk();
                results.add(new RetrievedEvidence(chunk.getId(), chunk.getDocumentId(), chunk.getSource(),
                        chunk.getChunkIndex(), chunk.getContent(), chunk.getModality(), score, "bm25"));
            }
        }
        return results.stream()
                .sorted(Comparator.comparingDouble(RetrievedEvidence::score).reversed())
                .limit(Math.max(0, topK))
                .toList();
    }

    private record DocumentTerms(KnowledgeChunk chunk, Map<String, Integer> termFrequency, int length) {}
    private record Snapshot(List<DocumentTerms> documents, Map<String, Integer> documentFrequency,
                            double averageLength) {
        private static Snapshot empty() { return new Snapshot(List.of(), Map.of(), 0.0); }
    }
}
