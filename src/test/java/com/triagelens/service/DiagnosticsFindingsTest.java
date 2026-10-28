package com.triagelens.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.triagelens.domain.*;
import com.triagelens.repository.TenantDocumentRepository.StatusCount;
import com.triagelens.web.dto.DiagnosticsReport.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class DiagnosticsFindingsTest {

    private static StatusCount sc(String type, DocumentStatus s, long n) {
        return new StatusCount() {
            public String getDocType() { return type; }
            public DocumentStatus getStatus() { return s; }
            public long getTotal() { return n; }
        };
    }

    private static List<TenantConfig> fullConfig() {
        return List.of(
                new TenantConfig(1L, ServiceArea.API_GATEWAY, "rate.limit.per.minute", "600", false),
                new TenantConfig(1L, ServiceArea.API_GATEWAY, "auth.mode", "OAUTH2", false),
                new TenantConfig(1L, ServiceArea.EDI_PROCESSING, "partner.id", "P", false),
                new TenantConfig(1L, ServiceArea.EDI_PROCESSING, "retry.max", "5", false),
                new TenantConfig(1L, ServiceArea.TRANSACTION_PROCESSING, "batch.size", "100", false));
    }

    @Test
    void statsComputeFailureRate() {
        var stats = DiagnosticsService.toStats(List.of(
                sc("ORDER", DocumentStatus.PROCESSED, 6), sc("ORDER", DocumentStatus.FAILED, 3),
                sc("ORDER", DocumentStatus.REJECTED, 1)));
        assertThat(stats).hasSize(1);
        assertThat(stats.get(0).total()).isEqualTo(10);
        assertThat(stats.get(0).failureRate()).isEqualTo(0.4);
    }

    @Test
    void healthyTenantYieldsSingleInfo() {
        var stats = DiagnosticsService.toStats(List.of(sc("ORDER", DocumentStatus.PROCESSED, 50)));
        var f = DiagnosticsService.findings(new Tenant("A", "A", "GOLD"), fullConfig(), stats);
        assertThat(f).extracting(Finding::code).containsExactly("HEALTHY");
    }

    @Test
    void missingAndEmptyConfigAreFlagged() {
        var rows = List.of(
                new TenantConfig(1L, ServiceArea.API_GATEWAY, "rate.limit.per.minute", "", false));
        var f = DiagnosticsService.findings(new Tenant("A", "A", "GOLD"), rows,
                DiagnosticsService.toStats(List.of(sc("ORDER", DocumentStatus.PROCESSED, 50))));
        assertThat(f).extracting(Finding::code).contains("CONFIG_MISSING", "CONFIG_EMPTY");
    }

    @Test
    void failureRateSeverityThresholds() {
        var warn = DiagnosticsService.toStats(List.of(
                sc("INVOICE", DocumentStatus.PROCESSED, 85), sc("INVOICE", DocumentStatus.FAILED, 15)));
        var crit = DiagnosticsService.toStats(List.of(
                sc("INVOICE", DocumentStatus.PROCESSED, 60), sc("INVOICE", DocumentStatus.FAILED, 40)));
        Tenant t = new Tenant("A", "A", "GOLD");
        assertThat(DiagnosticsService.findings(t, fullConfig(), warn)).extracting(Finding::severity)
                .containsExactly(Severity.WARNING);
        assertThat(DiagnosticsService.findings(t, fullConfig(), crit)).extracting(Finding::severity)
                .containsExactly(Severity.CRITICAL);
    }

    @Test
    void smallSamplesAreIgnoredAndInactiveTenantIsCritical() {
        var tiny = DiagnosticsService.toStats(List.of(sc("ORDER", DocumentStatus.FAILED, 3)));
        Tenant inactive = new Tenant("A", "A", "GOLD");
        inactive.setActive(false);
        var f = DiagnosticsService.findings(inactive, fullConfig(), tiny);
        assertThat(f).extracting(Finding::code).containsExactly("TENANT_INACTIVE");
    }
}
