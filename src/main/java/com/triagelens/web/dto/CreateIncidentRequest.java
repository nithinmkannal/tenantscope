package com.triagelens.web.dto;

import com.triagelens.domain.IssueCategory;
import com.triagelens.domain.Priority;
import com.triagelens.domain.ServiceArea;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateIncidentRequest(
        @NotBlank String tenantCode,
        @NotNull ServiceArea service,
        @NotNull IssueCategory category,
        @NotNull Priority priority,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 4000) String description) {
}
