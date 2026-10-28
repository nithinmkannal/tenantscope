package com.triagelens.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "tenant_document")
public class TenantDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @Column(name = "doc_type", nullable = false)
    private String docType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;
    @Column(name = "error_message")
    private String errorMessage;
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected TenantDocument() {
    }

    public TenantDocument(Long tenantId, String docType, DocumentStatus status, String errorMessage, Instant receivedAt) {
        this.tenantId = tenantId;
        this.docType = docType;
        this.status = status;
        this.errorMessage = errorMessage;
        this.receivedAt = receivedAt;
    }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public String getDocType() { return docType; }
    public DocumentStatus getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getReceivedAt() { return receivedAt; }
}
