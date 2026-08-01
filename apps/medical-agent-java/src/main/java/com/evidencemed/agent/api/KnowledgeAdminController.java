package com.evidencemed.agent.api;

import com.evidencemed.agent.application.rag.KnowledgeIngestionResult;
import com.evidencemed.agent.application.rag.KnowledgeIngestionService;
import com.evidencemed.agent.domain.knowledge.KnowledgeDocument;
import com.evidencemed.agent.infrastructure.persistence.KnowledgeDocumentRepository;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@RestController
@RequestMapping("/api/admin/v1/knowledge")
public class KnowledgeAdminController {
    private final KnowledgeIngestionService ingestion;
    private final FilePartReader files;
    private final KnowledgeDocumentRepository documents;

    public KnowledgeAdminController(KnowledgeIngestionService ingestion, FilePartReader files,
                                    KnowledgeDocumentRepository documents) {
        this.ingestion = ingestion;
        this.files = files;
        this.documents = documents;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<KnowledgeIngestionResult> ingest(@RequestPart("file") FilePart file) {
        String type = file.headers().getContentType() == null
                ? "application/octet-stream" : file.headers().getContentType().toString();
        return files.read(file).flatMap(bytes -> Mono.fromCallable(() -> ingestion.ingest(file.filename(), type, bytes))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    @GetMapping
    public Mono<List<KnowledgeDocument>> list() {
        return Mono.fromCallable(documents::findAll).subscribeOn(Schedulers.boundedElastic());
    }
}
