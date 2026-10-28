package com.tenantscope.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "incident")
public class Incident {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String number;
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ServiceArea service;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssueCategory category;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentState state = IncidentState.NEW;
    @Column(nullable = false)
    private String title;
    @Column(length = 4000)
    private String description;
    @Column(name = "assignment_group", nullable = false)
    private String assignmentGroup;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;
    @Column(name = "resolved_at")
    private Instant resolvedAt;
    @Column(name = "resolve_due_at", nullable = false)
    private Instant resolveDueAt;

    protected Incident() {
    }

    public Incident(String number, Long tenantId, ServiceArea service, IssueCategory category, Priority priority,
                    String title, String description, String assignmentGroup, Instant createdAt) {
        this.number = number;
        this.tenantId = tenantId;
        this.service = service;
        this.category = category;
        this.priority = priority;
        this.title = title;
        this.description = description;
        this.assignmentGroup = assignmentGroup;
        this.createdAt = createdAt;
        this.resolveDueAt = createdAt.plus(priority.resolveTarget());
    }

    /** Applies a state transition and stamps the acknowledgement/resolution timestamps. */
    public void transitionTo(IncidentState next, Instant now) {
        if (!state.canMoveTo(next)) {
            throw new IllegalStateException("Cannot move incident " + number + " from " + state + " to " + next);
        }
        if (acknowledgedAt == null && next != IncidentState.CLOSED) {
            acknowledgedAt = now;
        }
        if (next == IncidentState.RESOLVED) {
            resolvedAt = now;
        } else if (next == IncidentState.IN_PROGRESS) {
            resolvedAt = null;
        }
        state = next;
    }

    public boolean isBreached(Instant now) {
        Instant end = resolvedAt != null ? resolvedAt : now;
        return end.isAfter(resolveDueAt);
    }

    public Long getId() { return id; }
    public String getNumber() { return number; }
    public Long getTenantId() { return tenantId; }
    public ServiceArea getService() { return service; }
    public IssueCategory getCategory() { return category; }
    public Priority getPriority() { return priority; }
    public IncidentState getState() { return state; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getAssignmentGroup() { return assignmentGroup; }
    public void setAssignmentGroup(String assignmentGroup) { this.assignmentGroup = assignmentGroup; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Instant getResolveDueAt() { return resolveDueAt; }
}
