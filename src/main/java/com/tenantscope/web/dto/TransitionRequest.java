package com.tenantscope.web.dto;

import com.tenantscope.domain.IncidentState;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransitionRequest(
        @NotNull IncidentState state,
        @Size(max = 4000) String comment,
        @Size(max = 80) String actor) {
}
