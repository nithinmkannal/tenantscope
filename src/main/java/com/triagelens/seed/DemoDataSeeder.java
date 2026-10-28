package com.triagelens.seed;

import com.triagelens.domain.*;
import com.triagelens.repository.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Loads a deterministic synthetic data set when triagelens.seed.enabled=true and the DB is empty. */
@Component
@ConditionalOnProperty(name = "triagelens.seed.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private final TenantRepository tenants;
    private final TenantConfigRepository configs;
    private final TenantDocumentRepository documents;
    private final IncidentRepository incidents;
    private final IncidentNoteRepository notes;
    private final Clock clock;

    public DemoDataSeeder(TenantRepository tenants, TenantConfigRepository configs, TenantDocumentRepository documents,
                          IncidentRepository incidents, IncidentNoteRepository notes, Clock clock) {
        this.tenants = tenants;
        this.configs = configs;
        this.documents = documents;
        this.incidents = incidents;
        this.notes = notes;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (tenants.count() > 0) return;
        Random rnd = new Random(42);
        Instant now = clock.instant();

        Tenant northwind = tenants.save(new Tenant("NORTHWIND", "Northwind Logistics", "PLATINUM"));
        Tenant contoso = tenants.save(new Tenant("CONTOSO", "Contoso Retail", "GOLD"));
        Tenant fabrikam = tenants.save(new Tenant("FABRIKAM", "Fabrikam Foods", "STANDARD"));
        Tenant legacy = new Tenant("LEGACYCO", "LegacyCo Manufacturing", "STANDARD");
        legacy.setActive(false);
        legacy = tenants.save(legacy);

        // Northwind: complete config, healthy traffic
        config(northwind, true, true, true);
        traffic(rnd, northwind, "ORDER", 60, 0.03, now);
        traffic(rnd, northwind, "INVOICE", 40, 0.02, now);
        // Contoso: missing partner.id, an empty value, high failure rate on shipment notices
        config(contoso, true, false, true);
        configs.save(new TenantConfig(contoso.getId(), ServiceArea.SCHEDULER, "cron.expression", "", false));
        traffic(rnd, contoso, "ORDER", 50, 0.05, now);
        traffic(rnd, contoso, "SHIPMENT_NOTICE", 40, 0.35, now);
        // Fabrikam: elevated failures on invoices
        config(fabrikam, true, true, true);
        traffic(rnd, fabrikam, "INVOICE", 45, 0.15, now);
        // LegacyCo: inactive, nothing else

        incident(rnd, now, 1, contoso, ServiceArea.EDI_PROCESSING, IssueCategory.PROCESSING_FAILURE, Priority.P1,
                "Shipment notices rejected since morning", IncidentState.IN_PROGRESS, 6);
        incident(rnd, now, 2, contoso, ServiceArea.CONFIGURATION, IssueCategory.CONFIGURATION, Priority.P2,
                "Trading partner id not resolving", IncidentState.ACKNOWLEDGED, 30);
        incident(rnd, now, 3, northwind, ServiceArea.API_GATEWAY, IssueCategory.API_FAILURE, Priority.P2,
                "Gateway timeouts on bulk order upload", IncidentState.NEW, 3);
        incident(rnd, now, 4, fabrikam, ServiceArea.INVOICING, IssueCategory.PROCESSING_FAILURE, Priority.P3,
                "Invoice batch partially failed", IncidentState.IN_PROGRESS, 50);
        incident(rnd, now, 5, fabrikam, ServiceArea.TRANSACTION_PROCESSING, IssueCategory.TRANSACTION_FAILURE, Priority.P3,
                "Duplicate transaction reported", IncidentState.NEW, 100);
        incident(rnd, now, 6, northwind, ServiceArea.API_GATEWAY, IssueCategory.AUTHENTICATION, Priority.P4,
                "Token refresh failing for one client", IncidentState.RESOLVED, 120);
        incident(rnd, now, 7, contoso, ServiceArea.SCHEDULER, IssueCategory.CONFIGURATION, Priority.P3,
                "Nightly export job not triggered", IncidentState.RESOLVED, 200);
        incident(rnd, now, 8, northwind, ServiceArea.EDI_PROCESSING, IssueCategory.INTEGRATION, Priority.P2,
                "Partner AS2 endpoint unreachable", IncidentState.CLOSED, 260);
    }

    private void config(Tenant t, boolean api, boolean partner, boolean tx) {
        Long id = t.getId();
        if (api) {
            configs.save(new TenantConfig(id, ServiceArea.API_GATEWAY, "rate.limit.per.minute", "600", false));
            configs.save(new TenantConfig(id, ServiceArea.API_GATEWAY, "auth.mode", "OAUTH2_CLIENT_CREDENTIALS", false));
            configs.save(new TenantConfig(id, ServiceArea.API_GATEWAY, "client.secret", "s3cr3t-" + t.getCode(), true));
        }
        if (partner) {
            configs.save(new TenantConfig(id, ServiceArea.EDI_PROCESSING, "partner.id", "PARTNER-" + t.getCode(), false));
        }
        configs.save(new TenantConfig(id, ServiceArea.EDI_PROCESSING, "retry.max", "5", false));
        if (tx) {
            configs.save(new TenantConfig(id, ServiceArea.TRANSACTION_PROCESSING, "batch.size", "500", false));
        }
    }

    private void traffic(Random rnd, Tenant t, String type, int count, double failRate, Instant now) {
        for (int i = 0; i < count; i++) {
            Instant at = now.minus(Duration.ofMinutes(rnd.nextInt(6 * 24 * 60)));
            boolean fail = rnd.nextDouble() < failRate;
            DocumentStatus status = !fail ? DocumentStatus.PROCESSED
                    : (rnd.nextBoolean() ? DocumentStatus.FAILED : DocumentStatus.REJECTED);
            String err = fail ? (status == DocumentStatus.FAILED ? "Mapping error: unexpected segment" : "Schema validation failed") : null;
            documents.save(new TenantDocument(t.getId(), type, status, err, at));
        }
    }

    private void incident(Random rnd, Instant now, int n, Tenant t, ServiceArea svc, IssueCategory cat, Priority p,
                          String title, IncidentState target, int ageHours) {
        Instant created = now.minus(Duration.ofHours(ageHours));
        Incident i = new Incident("TL" + String.format("%07d", 900 + n), t.getId(), svc, cat, p, title,
                "Reported by " + t.getName(), svc.defaultGroup(), created);
        int step = switch (target) {
            case NEW -> 0;
            case ACKNOWLEDGED -> 1;
            case IN_PROGRESS -> 2;
            case RESOLVED -> 3;
            case CLOSED -> 4;
        };
        Instant at = created;
        if (step >= 1) { at = at.plus(Duration.ofMinutes(5 + rnd.nextInt(40))); i.transitionTo(IncidentState.ACKNOWLEDGED, at); }
        if (step >= 2) { at = at.plus(Duration.ofMinutes(10 + rnd.nextInt(60))); i.transitionTo(IncidentState.IN_PROGRESS, at); }
        if (step >= 3) { at = at.plus(Duration.ofHours(1 + rnd.nextInt(3))); i.transitionTo(IncidentState.RESOLVED, at); }
        if (step >= 4) { i.transitionTo(IncidentState.CLOSED, at.plus(Duration.ofHours(1))); }
        incidents.save(i);
        notes.save(new IncidentNote(i.getId(), "system", NoteKind.WORK_NOTE, "Seeded incident"));
    }
}
