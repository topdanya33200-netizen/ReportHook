package io.github.topdanya33200_netizen.reporthook.model;

import java.util.List;

/**
 * One page of reports. {@code page} is zero-based.
 */
public record ReportPage(List<Report> reports, int total, int page, int pageSize) {

    public ReportPage {
        reports = List.copyOf(reports);
    }

    public int pages() {
        return Math.max(1, (total + pageSize - 1) / pageSize);
    }

    public boolean hasPrevious() {
        return page > 0;
    }

    public boolean hasNext() {
        return (long) (page + 1) * pageSize < total;
    }
}
