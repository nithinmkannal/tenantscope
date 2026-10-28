package com.tenantscope.domain;

import java.time.Duration;

public enum Priority {
    P1(4), P2(8), P3(24), P4(72);

    private final long resolveHours;

    Priority(long resolveHours) {
        this.resolveHours = resolveHours;
    }

    public Duration resolveTarget() {
        return Duration.ofHours(resolveHours);
    }

    public boolean isUrgent() {
        return this == P1 || this == P2;
    }
}
