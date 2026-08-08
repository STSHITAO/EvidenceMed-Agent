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

    @Column(nullable = false)
    private int pageFrom = 1;

    @Column(nullable = false)
    private int pageTo = 1;

    @Column(nullable = false, length = 32)
    private String objectType = "PARAGRAPH";

    @Column(length = 500)
    private String sectionPath;

    @Lob
    private String boundingBoxes;

    @Column(nullable = false, length = 64)
    private String parserVersion = "unknown";

    @Column(nullable = false)
    private double qualityScore = 1.0;

    protected KnowledgeChunk() {}

    public KnowledgeChunk(String documentId, int chunkIndex, String source, String content) {
        this(documentId, chunkIndex, source, null, content);
    }

    public KnowledgeChunk(String documentId, int chunkIndex, String source, String sectionTitle, String content) {
        this(documentId, chunkIndex, source, sectionTitle, content, 1, 1, "PARAGRAPH",
                sectionTitle, "[]", "legacy", 1.0);
    }

    public KnowledgeChunk(String documentId, int chunkIndex, String source, String sectionTitle,
            String content, int pageFrom, int pageTo, String objectType, String sectionPath,
            String boundingBoxes, String parserVersion, double qualityScore) {
        this.documentId = documentId;
        this.chunkIndex = chunkIndex;
        this.source = source;
        this.sectionTitle = sectionTitle;
        this.content = content;
        this.pageFrom = Math.max(1, pageFrom);
        this.pageTo = Math.max(this.pageFrom, pageTo);
        this.objectType = objectType == null ? "PARAGRAPH" : objectType;
        this.sectionPath = sectionPath;
        this.boundingBoxes = boundingBoxes == null ? "[]" : boundingBoxes;
        this.parserVersion = parserVersion == null ? "unknown" : parserVersion;
        this.qualityScore = Math.max(0.0, Math.min(1.0, qualityScore));
    }

    public String getDocumentId() { return documentId; }
    public int getChunkIndex() { return chunkIndex; }
    public String getSource() { return source; }
    public String getSectionTitle() { return sectionTitle; }
    public String getContent() { return content; }
    public String getModality() { return modality; }
    public int getPageFrom() { return pageFrom; }
    public int getPageTo() { return pageTo; }
    public String getObjectType() { return objectType; }
    public String getSectionPath() { return sectionPath; }
    public String getBoundingBoxes() { return boundingBoxes; }
    public String getParserVersion() { return parserVersion; }
    public double getQualityScore() { return qualityScore; }

    public String embeddingContent() {
        String prefix = sectionPath == null || sectionPath.isBlank() ? "" : "章节：" + sectionPath + "\n";
        return prefix + "证据类型：" + objectType + "\n" + content;
    }
}
