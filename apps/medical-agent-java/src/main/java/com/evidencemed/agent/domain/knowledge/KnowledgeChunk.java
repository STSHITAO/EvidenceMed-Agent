package com.evidencemed.agent.domain.knowledge;

import com.evidencemed.agent.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "knowledge_chunk",
        uniqueConstraints = @UniqueConstraint(columnNames = {"documentId", "chunkIndex"}),
        indexes = @Index(name = "idx_chunk_document", columnList = "documentId"))
public class KnowledgeChunk extends BaseEntity {
    @Column(nullable = false, length = 36)
    private String documentId;

    @Column(nullable = false)
    private int chunkIndex;

    @Column(nullable = false, length = 255)
    private String source;

    @Column(length = 120)
    private String sectionTitle;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(nullable = false, length = 16)
    private String modality = "text";

    protected KnowledgeChunk() {}

    public KnowledgeChunk(String documentId, int chunkIndex, String source, String content) {
        this(documentId, chunkIndex, source, null, content);
    }

    public KnowledgeChunk(String documentId, int chunkIndex, String source, String sectionTitle, String content) {
        this.documentId = documentId;
        this.chunkIndex = chunkIndex;
        this.source = source;
        this.sectionTitle = sectionTitle;
        this.content = content;
    }

    public String getDocumentId() { return documentId; }
    public int getChunkIndex() { return chunkIndex; }
    public String getSource() { return source; }
    public String getSectionTitle() { return sectionTitle; }
    public String getContent() { return content; }
    public String getModality() { return modality; }
}
