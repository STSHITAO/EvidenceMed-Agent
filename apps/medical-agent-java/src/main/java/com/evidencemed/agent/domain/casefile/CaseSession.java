package com.evidencemed.agent.domain.casefile;

import com.evidencemed.agent.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "case_session", indexes = @Index(name = "idx_case_owner", columnList = "ownerId"))
public class CaseSession extends BaseEntity {
    @Column(nullable = false, length = 36)
    private String ownerId;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 32)
    private String status = "ACTIVE";

    protected CaseSession() {}

    public CaseSession(String ownerId, String title) {
        this.ownerId = ownerId;
        this.title = title;
    }

    public String getOwnerId() { return ownerId; }
    public String getTitle() { return title; }
    public String getStatus() { return status; }
}
