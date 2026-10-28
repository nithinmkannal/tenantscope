package com.triagelens.web.dto;

import com.triagelens.domain.IncidentNote;
import com.triagelens.domain.NoteKind;
import java.time.Instant;

public record NoteView(String author, NoteKind kind, String body, Instant createdAt) {
    public static NoteView of(IncidentNote n) {
        return new NoteView(n.getAuthor(), n.getKind(), n.getBody(), n.getCreatedAt());
    }
}
