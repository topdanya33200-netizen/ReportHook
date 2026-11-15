package io.github.topdanya33200_netizen.reporthook.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle of a report.
 *
 * <pre>
 *   OPEN ──claim──▶ CLAIMED ──resolve/dismiss──▶ RESOLVED / DISMISSED
 *     ▲               │                                   │
 *     └───unclaim─────┘◀───────────reopen─────────────────┘
 * </pre>
 */
public enum ReportStatus {
    OPEN,
    CLAIMED,
    RESOLVED,
    DISMISSED;

    /** Open and claimed reports still need attention from staff. */
    public boolean isActive() {
        return this == OPEN || this == CLAIMED;
    }

    public boolean canTransitionTo(ReportStatus next) {
        return switch (this) {
            case OPEN -> next == CLAIMED || next == RESOLVED || next == DISMISSED;
            case CLAIMED -> next == OPEN || next == RESOLVED || next == DISMISSED;
            case RESOLVED, DISMISSED -> next == OPEN;
        };
    }

    public static Set<ReportStatus> active() {
        return EnumSet.of(OPEN, CLAIMED);
    }

    public static Set<ReportStatus> closed() {
        return EnumSet.of(RESOLVED, DISMISSED);
    }
}
