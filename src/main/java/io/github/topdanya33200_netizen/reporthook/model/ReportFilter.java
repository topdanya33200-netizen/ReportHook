package io.github.topdanya33200_netizen.reporthook.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Status filters used by the staff menu and {@code /reports list}.
 */
public enum ReportFilter {
    ACTIVE(EnumSet.of(ReportStatus.OPEN, ReportStatus.CLAIMED)),
    OPEN(EnumSet.of(ReportStatus.OPEN)),
    CLAIMED(EnumSet.of(ReportStatus.CLAIMED)),
    CLOSED(EnumSet.of(ReportStatus.RESOLVED, ReportStatus.DISMISSED)),
    ALL(EnumSet.allOf(ReportStatus.class));

    private final Set<ReportStatus> statuses;

    ReportFilter(Set<ReportStatus> statuses) {
        this.statuses = Collections.unmodifiableSet(statuses);
    }

    public Set<ReportStatus> statuses() {
        return statuses;
    }

    /** The next filter in menu order, wrapping around. */
    public ReportFilter next() {
        ReportFilter[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<ReportFilter> parse(String input) {
        for (ReportFilter filter : values()) {
            if (filter.name().equalsIgnoreCase(input.trim())) {
                return Optional.of(filter);
            }
        }
        return Optional.empty();
    }
}
