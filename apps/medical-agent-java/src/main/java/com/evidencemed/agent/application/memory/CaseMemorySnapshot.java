package com.evidencemed.agent.application.memory;

import java.util.List;

public record CaseMemorySnapshot(String caseBrief, List<MemoryMessage> recentMessages) {
    public static CaseMemorySnapshot empty() {
        return new CaseMemorySnapshot("", List.of());
    }
}
