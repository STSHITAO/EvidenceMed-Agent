package com.evidencemed.agent.domain.casefile;

import com.evidencemed.agent.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "case_message", indexes = @Index(name = "idx_message_session_created", columnList = "sessionId,createdAt"))
public class CaseMessage extends BaseEntity {
    @Column(nullable = false, length = 36)
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MessageRole role;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private boolean imageAttached;

    protected CaseMessage() {}

    public CaseMessage(String sessionId, MessageRole role, String content, boolean imageAttached) {
        this.sessionId = sessionId;
        this.role = role;
        this.content = content;
        this.imageAttached = imageAttached;
    }

    public String getSessionId() { return sessionId; }
    public MessageRole getRole() { return role; }
    public String getContent() { return content; }
    public boolean isImageAttached() { return imageAttached; }
}
