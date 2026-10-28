package com.triagelens.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "incident_note")
public class IncidentNote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "incident_id", nullable = false)
    private Long incidentId;
    @Column(nullable = false)
    private String author;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NoteKind kind;
    @Column(nullable = false, length = 4000)
    private String body;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected IncidentNote() {
    }

    public IncidentNote(Long incidentId, String author, NoteKind kind, String body) {
        this.incidentId = incidentId;
        this.author = author;
        this.kind = kind;
        this.body = body;
    }

    public Long getId() { return id; }
    public Long getIncidentId() { return incidentId; }
    public String getAuthor() { return author; }
    public NoteKind getKind() { return kind; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
}
