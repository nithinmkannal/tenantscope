package com.triagelens.web.dto;

import com.triagelens.domain.NoteKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record NoteRequest(
        @NotBlank @Size(max = 80) String author,
        @NotNull NoteKind kind,
        @NotBlank @Size(max = 4000) String body) {
}
