package com.evidencemed.agent.domain.knowledge;

import com.evidencemed.agent.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "knowledge_document", uniqueConstraints = @UniqueConstraint(columnNames = "sha256"))
public class KnowledgeDocument extends BaseEntity {
    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(nullable = false, length = 100)
    private String mediaType;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(nullable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private KnowledgeStatus status = KnowledgeStatus.PENDING;

    @Column(length = 500)
    private String failureReason;

    @Column(nullable = false)
    private int pageCount = 1;

    @Column(nullable = false, length = 64)
    private String parserVersion = "unknown";

    @Column(nullable = false)
    private double qualityScore = 1.0;

    @Column(length = 1000)
    private String parseWarnings;

    protected KnowledgeDocument() {}

    public KnowledgeDocument(String fileName, String mediaType, String sha256, long sizeBytes) {
        this.fileName = fileName;
        this.mediaType = mediaType;
        this.sha256 = sha256;
        this.sizeBytes = sizeBytes;
    }

    public void indexed() { indexed(1, "legacy", 1.0, ""); }
    public void indexed(int pageCount, String parserVersion, double qualityScore, String warnings) {
        this.status = KnowledgeStatus.INDEXED;
        this.failureReason = null;
        this.pageCount = Math.max(1, pageCount);
        this.parserVersion = parserVersion == null ? "unknown" : parserVersion;
        this.qualityScore = Math.max(0.0, Math.min(1.0, qualityScore));
        this.parseWarnings = warnings == null || warnings.isBlank()
                ? null : warnings.substring(0, Math.min(1000, warnings.length()));
    }
    public void failed(String reason) { this.status = KnowledgeStatus.FAILED; this.failureReason = reason; }
    public String getFileName() { return fileName; }
    public String getMediaType() { return mediaType; }
    public String getSha256() { return sha256; }
    public long getSizeBytes() { return sizeBytes; }
    public KnowledgeStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public int getPageCount() { return pageCount; }
    public String getParserVersion() { return parserVersion; }
    public double getQualityScore() { return qualityScore; }
    public String getParseWarnings() { return parseWarnings; }
}
