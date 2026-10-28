package com.triagelens.domain;

import java.util.EnumSet;
import java.util.Set;

public enum IncidentState {
    NEW, ACKNOWLEDGED, IN_PROGRESS, RESOLVED, CLOSED;

    public Set<IncidentState> allowedNext() {
        return switch (this) {
            case NEW -> EnumSet.of(ACKNOWLEDGED, IN_PROGRESS, RESOLVED);
            case ACKNOWLEDGED -> EnumSet.of(IN_PROGRESS, RESOLVED);
            case IN_PROGRESS -> EnumSet.of(RESOLVED);
            case RESOLVED -> EnumSet.of(CLOSED, IN_PROGRESS);
            case CLOSED -> EnumSet.noneOf(IncidentState.class);
        };
    }

    public boolean canMoveTo(IncidentState next) {
        return allowedNext().contains(next);
    }

    public boolean isOpen() {
        return this != RESOLVED && this != CLOSED;
    }
}
