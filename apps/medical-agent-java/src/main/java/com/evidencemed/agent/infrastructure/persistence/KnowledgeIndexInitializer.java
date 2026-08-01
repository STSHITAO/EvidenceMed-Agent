package com.evidencemed.agent.infrastructure.persistence;

import com.evidencemed.agent.application.rag.Bm25Index;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeIndexInitializer {
    private final KnowledgeChunkRepository chunks;
    private final Bm25Index bm25;

    public KnowledgeIndexInitializer(KnowledgeChunkRepository chunks, Bm25Index bm25) {
        this.chunks = chunks;
        this.bm25 = bm25;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void rebuild() {
        bm25.rebuild(chunks.findAll());
    }
}
