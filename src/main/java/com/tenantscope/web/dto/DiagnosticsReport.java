package com.tenantscope.web.dto;

import com.tenantscope.domain.DocumentStatus;
import com.tenantscope.domain.ServiceArea;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record DiagnosticsReport(
        String tenantCode,
        String tenantName,
        String tier,
        boolean active,
        Instant generatedAt,
        int windowDays,
        Map<ServiceArea, List<ConfigEntry>> configuration,
        List<DocumentStat> documentStats,
        List<FailedDocument> recentFailures,
        List<Finding> findings) {

    public record ConfigEntry(String key, String value, boolean masked, Instant updatedAt) {
    }

    public record DocumentStat(String docType, long total, long processed, long failed, long rejected,
                               double failureRate) {
    }

    public record FailedDocument(String docType, DocumentStatus status, String error, Instant receivedAt) {
    }

    public enum Severity { INFO, WARNING, CRITICAL }

    public record Finding(Severity severity, String code, String message) {
    }
}
