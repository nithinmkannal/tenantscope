package com.triagelens;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.triagelens.domain.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class IncidentStateTest {

    private Incident incident(Priority p, Instant created) {
        return new Incident("TL1", 1L, ServiceArea.API_GATEWAY, IssueCategory.API_FAILURE, p, "t", null, "g", created);
    }

    @Test
    void resolveDueFollowsPriorityTarget() {
        Instant created = Instant.parse("2025-01-01T00:00:00Z");
        assertThat(incident(Priority.P1, created).getResolveDueAt()).isEqualTo(created.plusSeconds(4 * 3600));
        assertThat(incident(Priority.P4, created).getResolveDueAt()).isEqualTo(created.plusSeconds(72 * 3600));
    }

    @Test
    void happyPathStampsTimestamps() {
        Instant t0 = Instant.parse("2025-01-01T00:00:00Z");
        Incident i = incident(Priority.P2, t0);
        i.transitionTo(IncidentState.ACKNOWLEDGED, t0.plusSeconds(600));
        i.transitionTo(IncidentState.IN_PROGRESS, t0.plusSeconds(1200));
        i.transitionTo(IncidentState.RESOLVED, t0.plusSeconds(3600));
        assertThat(i.getAcknowledgedAt()).isEqualTo(t0.plusSeconds(600));
        assertThat(i.getResolvedAt()).isEqualTo(t0.plusSeconds(3600));
        assertThat(i.isBreached(t0.plusSeconds(99999))).isFalse();
    }

    @Test
    void reopenClearsResolvedAt() {
        Instant t0 = Instant.parse("2025-01-01T00:00:00Z");
        Incident i = incident(Priority.P3, t0);
        i.transitionTo(IncidentState.RESOLVED, t0.plusSeconds(60));
        i.transitionTo(IncidentState.IN_PROGRESS, t0.plusSeconds(120));
        assertThat(i.getResolvedAt()).isNull();
    }

    @Test
    void illegalTransitionsRejected() {
        Instant t0 = Instant.parse("2025-01-01T00:00:00Z");
        Incident i = incident(Priority.P3, t0);
        assertThatThrownBy(() -> i.transitionTo(IncidentState.CLOSED, t0)).isInstanceOf(IllegalStateException.class);
        i.transitionTo(IncidentState.RESOLVED, t0);
        i.transitionTo(IncidentState.CLOSED, t0);
        assertThatThrownBy(() -> i.transitionTo(IncidentState.IN_PROGRESS, t0)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void openIncidentPastDueIsBreached() {
        Instant t0 = Instant.parse("2025-01-01T00:00:00Z");
        Incident i = incident(Priority.P1, t0);
        assertThat(i.isBreached(t0.plusSeconds(3 * 3600))).isFalse();
        assertThat(i.isBreached(t0.plusSeconds(5 * 3600))).isTrue();
    }
}
