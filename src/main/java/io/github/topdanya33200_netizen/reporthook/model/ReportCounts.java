package io.github.topdanya33200_netizen.reporthook.model;

import java.util.Map;

/**
 * Cached number of reports per status, used by placeholders and join reminders.
 */
public record ReportCounts(int open, int claimed, int resolved, int dismissed) {

    public static final ReportCounts EMPTY = new ReportCounts(0, 0, 0, 0);

    public static ReportCounts of(Map<ReportStatus, Integer> counts) {
        return new ReportCounts(
                counts.getOrDefault(ReportStatus.OPEN, 0),
                counts.getOrDefault(ReportStatus.CLAIMED, 0),
                counts.getOrDefault(ReportStatus.RESOLVED, 0),
                counts.getOrDefault(ReportStatus.DISMISSED, 0));
    }

    public int active() {
        return open + claimed;
    }

    public int total() {
        return open + claimed + resolved + dismissed;
    }
}
