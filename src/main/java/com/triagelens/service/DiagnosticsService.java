package com.triagelens.service;

import com.triagelens.domain.*;
import com.triagelens.repository.*;
import com.triagelens.web.dto.DiagnosticsReport;
import com.triagelens.web.dto.DiagnosticsReport.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds a one-shot diagnostics snapshot for a tenant: configuration (with secrets masked),
 * document processing statistics and automatically derived findings.
 */
@Service
public class DiagnosticsService {

    static final String MASK = "********";
    static final int MIN_SAMPLE = 5;
    static final double WARN_RATE = 0.10;
    static final double CRITICAL_RATE = 0.25;

    /** Config keys every tenant is expected to have, per service area. */
    static final Map<ServiceArea, List<String>> REQUIRED_KEYS = Map.of(
            ServiceArea.API_GATEWAY, List.of("rate.limit.per.minute", "auth.mode"),
            ServiceArea.EDI_PROCESSING, List.of("partner.id", "retry.max"),
            ServiceArea.TRANSACTION_PROCESSING, List.of("batch.size"));

    private final TenantRepository tenants;
    private final TenantConfigRepository configs;
    private final TenantDocumentRepository documents;
    private final IncidentRepository incidents;
    private final IncidentService incidentService;
    private final Clock clock;

    public DiagnosticsService(TenantRepository tenants, TenantConfigRepository configs,
                              TenantDocumentRepository documents, IncidentRepository incidents,
                              IncidentService incidentService, Clock clock) {
        this.tenants = tenants;
        this.configs = configs;
        this.documents = documents;
        this.incidents = incidents;
        this.incidentService = incidentService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DiagnosticsReport forTenant(String tenantCode, int windowDays) {
        Tenant tenant = tenants.findByCode(tenantCode)
                .orElseThrow(() -> new NotFoundException("Unknown tenant: " + tenantCode));
        return build(tenant, windowDays);
    }

    /** Builds the report for the tenant behind an incident and records a summary note on it. */
    @Transactional
    public DiagnosticsReport forIncident(String incidentNumber, int windowDays) {
        Incident incident = incidentService.find(incidentNumber);
        Tenant tenant = tenants.findById(incident.getTenantId())
                .orElseThrow(() -> new NotFoundException("Tenant missing for incident " + incidentNumber));
        DiagnosticsReport report = build(tenant, windowDays);
        incidentService.addDiagnosticNote(incident, summarize(report));
        return report;
    }

    DiagnosticsReport build(Tenant tenant, int windowDays) {
        Instant now = clock.instant();
        Instant since = now.minus(Duration.ofDays(windowDays));

        List<TenantConfig> rows = configs.findByTenantIdOrderByServiceAscConfigKeyAsc(tenant.getId());
        Map<ServiceArea, List<ConfigEntry>> config = new EnumMap<>(ServiceArea.class);
        for (TenantConfig c : rows) {
            config.computeIfAbsent(c.getService(), k -> new ArrayList<>()).add(new ConfigEntry(
                    c.getConfigKey(), c.isSensitive() ? MASK : c.getConfigValue(), c.isSensitive(), c.getUpdatedAt()));
        }

        List<DocumentStat> stats = toStats(documents.countByTypeAndStatus(tenant.getId(), since));
        List<FailedDocument> failures = documents.findByTenantIdAndStatusInOrderByReceivedAtDesc(
                        tenant.getId(), List.of(DocumentStatus.FAILED, DocumentStatus.REJECTED), PageRequest.of(0, 10))
                .stream()
                .map(d -> new FailedDocument(d.getDocType(), d.getStatus(), d.getErrorMessage(), d.getReceivedAt()))
                .toList();

        List<Finding> findings = findings(tenant, rows, stats);
        return new DiagnosticsReport(tenant.getCode(), tenant.getName(), tenant.getTier(), tenant.isActive(),
                now, windowDays, config, stats, failures, findings);
    }

    static List<DocumentStat> toStats(List<TenantDocumentRepository.StatusCount> counts) {
        Map<String, long[]> byType = new TreeMap<>(); // [processed, failed, rejected, other]
        for (var c : counts) {
            long[] a = byType.computeIfAbsent(c.getDocType(), k -> new long[4]);
            switch (c.getStatus()) {
                case PROCESSED -> a[0] += c.getTotal();
                case FAILED -> a[1] += c.getTotal();
                case REJECTED -> a[2] += c.getTotal();
                default -> a[3] += c.getTotal();
            }
        }
        return byType.entrySet().stream().map(e -> {
            long[] a = e.getValue();
            long total = a[0] + a[1] + a[2] + a[3];
            double rate = total == 0 ? 0 : (double) (a[1] + a[2]) / total;
            return new DocumentStat(e.getKey(), total, a[0], a[1], a[2], Math.round(rate * 1000) / 1000.0);
        }).toList();
    }

    static List<Finding> findings(Tenant tenant, List<TenantConfig> rows, List<DocumentStat> stats) {
        List<Finding> out = new ArrayList<>();
        if (!tenant.isActive()) {
            out.add(new Finding(Severity.CRITICAL, "TENANT_INACTIVE", "Tenant is deactivated; all traffic will be refused"));
        }
        Map<ServiceArea, Set<String>> present = rows.stream().collect(Collectors.groupingBy(
                TenantConfig::getService, Collectors.mapping(TenantConfig::getConfigKey, Collectors.toSet())));
        REQUIRED_KEYS.forEach((service, keys) -> {
            for (String key : keys) {
                if (!present.getOrDefault(service, Set.of()).contains(key)) {
                    out.add(new Finding(Severity.WARNING, "CONFIG_MISSING",
                            "Missing required setting " + service + "/" + key));
                }
            }
        });
        for (TenantConfig c : rows) {
            if (c.getConfigValue() == null || c.getConfigValue().isBlank()) {
                out.add(new Finding(Severity.WARNING, "CONFIG_EMPTY",
                        "Setting " + c.getService() + "/" + c.getConfigKey() + " has no value"));
            }
        }
        for (DocumentStat s : stats) {
            if (s.total() < MIN_SAMPLE) continue;
            if (s.failureRate() >= CRITICAL_RATE) {
                out.add(new Finding(Severity.CRITICAL, "HIGH_FAILURE_RATE", String.format(
                        "%s failure rate %.0f%% (%d of %d)", s.docType(), s.failureRate() * 100, s.failed() + s.rejected(), s.total())));
            } else if (s.failureRate() >= WARN_RATE) {
                out.add(new Finding(Severity.WARNING, "ELEVATED_FAILURE_RATE", String.format(
                        "%s failure rate %.0f%% (%d of %d)", s.docType(), s.failureRate() * 100, s.failed() + s.rejected(), s.total())));
            }
        }
        if (stats.isEmpty() && tenant.isActive()) {
            out.add(new Finding(Severity.INFO, "NO_TRAFFIC", "No documents received in the selected window"));
        }
        if (out.isEmpty()) {
            out.add(new Finding(Severity.INFO, "HEALTHY", "No configuration or processing anomalies detected"));
        }
        out.sort(Comparator.comparing(Finding::severity).reversed());
        return out;
    }

    static String summarize(DiagnosticsReport r) {
        String f = r.findings().stream()
                .map(x -> "[" + x.severity() + "] " + x.message())
                .collect(Collectors.joining("; "));
        return "Diagnostics for " + r.tenantCode() + " (last " + r.windowDays() + "d): " + f;
    }
}
