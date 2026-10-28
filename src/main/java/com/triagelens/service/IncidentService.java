package com.triagelens.service;

import com.triagelens.domain.*;
import com.triagelens.repository.*;
import com.triagelens.web.dto.*;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentService {

    private final IncidentRepository incidents;
    private final IncidentNoteRepository notes;
    private final TenantRepository tenants;
    private final Clock clock;

    public IncidentService(IncidentRepository incidents, IncidentNoteRepository notes,
                           TenantRepository tenants, Clock clock) {
        this.incidents = incidents;
        this.notes = notes;
        this.tenants = tenants;
        this.clock = clock;
    }

    /**
     * Platinum tenants get their priority raised one level (never above P1), and the incident is
     * routed to the owning team of the affected service.
     */
    @Transactional
    public IncidentView create(CreateIncidentRequest req) {
        Tenant tenant = tenants.findByCode(req.tenantCode())
                .orElseThrow(() -> new NotFoundException("Unknown tenant: " + req.tenantCode()));
        Priority priority = effectivePriority(req.priority(), tenant);
        String number = "TL" + String.format("%07d", incidents.nextNumber());
        Incident incident = new Incident(number, tenant.getId(), req.service(), req.category(), priority,
                req.title(), req.description(), req.service().defaultGroup(), clock.instant());
        incidents.save(incident);
        if (priority != req.priority()) {
            notes.save(new IncidentNote(incident.getId(), "system", NoteKind.WORK_NOTE,
                    "Priority raised from " + req.priority() + " to " + priority + " for " + tenant.getTier() + " tenant"));
        }
        return IncidentView.of(incident, tenant.getCode(), clock.instant());
    }

    static Priority effectivePriority(Priority requested, Tenant tenant) {
        if ("PLATINUM".equalsIgnoreCase(tenant.getTier()) && requested.ordinal() > 0) {
            return Priority.values()[requested.ordinal() - 1];
        }
        return requested;
    }

    @Transactional(readOnly = true)
    public Page<IncidentView> search(IncidentState state, Priority priority, ServiceArea service,
                                     String tenantCode, Pageable pageable) {
        Long tenantId = null;
        if (tenantCode != null) {
            tenantId = tenants.findByCode(tenantCode).map(Tenant::getId).orElse(-1L);
        }
        final Long tid = tenantId;
        Specification<Incident> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (state != null) p.add(cb.equal(root.get("state"), state));
            if (priority != null) p.add(cb.equal(root.get("priority"), priority));
            if (service != null) p.add(cb.equal(root.get("service"), service));
            if (tid != null) p.add(cb.equal(root.get("tenantId"), tid));
            return cb.and(p.toArray(new Predicate[0]));
        };
        Page<Incident> page = incidents.findAll(spec, pageable);
        Map<Long, String> codes = tenantCodes(page.getContent());
        Instant now = clock.instant();
        return page.map(i -> IncidentView.of(i, codes.get(i.getTenantId()), now));
    }

    @Transactional(readOnly = true)
    public IncidentDetailView get(String number) {
        Incident i = find(number);
        String code = tenants.findById(i.getTenantId()).map(Tenant::getCode).orElse("?");
        List<NoteView> history = notes.findByIncidentIdOrderByCreatedAtAsc(i.getId())
                .stream().map(NoteView::of).toList();
        return new IncidentDetailView(IncidentView.of(i, code, clock.instant()), history);
    }

    @Transactional
    public IncidentView transition(String number, TransitionRequest req) {
        Incident i = find(number);
        IncidentState from = i.getState();
        try {
            i.transitionTo(req.state(), clock.instant());
        } catch (IllegalStateException e) {
            throw new InvalidTransitionException(e.getMessage());
        }
        String actor = req.actor() == null ? "system" : req.actor();
        String text = from + " -> " + req.state() + (req.comment() == null ? "" : ": " + req.comment());
        notes.save(new IncidentNote(i.getId(), actor, NoteKind.STATE_CHANGE, text));
        String code = tenants.findById(i.getTenantId()).map(Tenant::getCode).orElse("?");
        return IncidentView.of(i, code, clock.instant());
    }

    @Transactional
    public NoteView addNote(String number, NoteRequest req) {
        Incident i = find(number);
        return NoteView.of(notes.save(new IncidentNote(i.getId(), req.author(), req.kind(), req.body())));
    }

    @Transactional
    public void addDiagnosticNote(Incident incident, String body) {
        notes.save(new IncidentNote(incident.getId(), "triagelens", NoteKind.DIAGNOSTIC, body));
    }

    @Transactional(readOnly = true)
    public Incident find(String number) {
        return incidents.findByNumber(number)
                .orElseThrow(() -> new NotFoundException("Unknown incident: " + number));
    }

    private Map<Long, String> tenantCodes(List<Incident> list) {
        return tenants.findAllById(list.stream().map(Incident::getTenantId).distinct().toList())
                .stream().collect(Collectors.toMap(Tenant::getId, Tenant::getCode, (a, b) -> a, java.util.HashMap::new));
    }
}
