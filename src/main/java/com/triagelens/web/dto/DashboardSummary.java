package com.triagelens.web.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record DashboardSummary(
        long openIncidents,
        long urgentOpenIncidents,
        long breachedOpenIncidents,
        long agingOpenIncidents,
        Double meanTimeToAcknowledgeMinutes,
        Double meanTimeToResolveHours,
        long configurationIssues,
        long apiIssues,
        long processingIssues,
        Map<String, Long> openByPriority,
        Map<String, Long> openByService,
        Map<String, Long> openByAssignmentGroup,
        Map<String, Long> createdByCategory,
        List<TrendPoint> trend,
        List<CriticalIncident> recentCritical) {

    public record TrendPoint(LocalDate day, long created, long resolved) {
    }

    public record CriticalIncident(String number, String priority, String service, String title, String state) {
    }
}
