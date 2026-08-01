package com.evidencemed.agent.application.rag;

import com.evidencemed.agent.domain.knowledge.RetrievedEvidence;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class RrfFusion {
    public List<RetrievedEvidence> fuse(List<List<RetrievedEvidence>> rankings, int rrfK, int limit) {
        Map<String, Double> scores = new HashMap<>();
        Map<String, RetrievedEvidence> evidence = new HashMap<>();
        for (List<RetrievedEvidence> ranking : rankings) {
            Set<String> seen = new HashSet<>();
            for (int index = 0; index < ranking.size(); index++) {
                RetrievedEvidence item = ranking.get(index);
                if (!seen.add(item.chunkId())) continue;
                scores.merge(item.chunkId(), 1.0 / (rrfK + index + 1.0), Double::sum);
                evidence.putIfAbsent(item.chunkId(), item);
            }
        }
        List<RetrievedEvidence> fused = new ArrayList<>();
        scores.forEach((id, score) -> fused.add(evidence.get(id).withScore(score, "rrf")));
        return fused.stream()
                .sorted(Comparator.comparingDouble(RetrievedEvidence::score).reversed())
                .limit(Math.max(0, limit))
                .toList();
    }
}
