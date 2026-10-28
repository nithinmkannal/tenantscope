package com.tenantscope.web.dto;

import com.tenantscope.domain.*;
import java.time.Instant;

public record IncidentView(
        String number,
        String tenantCode,
        ServiceArea service,
        IssueCategory category,
        Priority priority,
        IncidentState state,
        String title,
        String description,
        String assignmentGroup,
        Instant createdAt,
        Instant acknowledgedAt,
        Instant resolvedAt,
        Instant resolveDueAt,
        boolean slaBreached) {

    public static IncidentView of(Incident i, String tenantCode, Instant now) {
        return new IncidentView(i.getNumber(), tenantCode, i.getService(), i.getCategory(), i.getPriority(),
                i.getState(), i.getTitle(), i.getDescription(), i.getAssignmentGroup(), i.getCreatedAt(),
                i.getAcknowledgedAt(), i.getResolvedAt(), i.getResolveDueAt(), i.isBreached(now));
    }
}
