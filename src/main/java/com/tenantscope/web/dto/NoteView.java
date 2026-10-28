package com.tenantscope.web.dto;

import com.tenantscope.domain.IncidentNote;
import com.tenantscope.domain.NoteKind;
import java.time.Instant;

public record NoteView(String author, NoteKind kind, String body, Instant createdAt) {
    public static NoteView of(IncidentNote n) {
        return new NoteView(n.getAuthor(), n.getKind(), n.getBody(), n.getCreatedAt());
    }
}
