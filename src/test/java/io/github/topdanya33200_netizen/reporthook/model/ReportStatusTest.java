package io.github.topdanya33200_netizen.reporthook.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ReportStatusTest {

    @Test
    void openReportsCanBeClaimedOrClosed() {
        assertTrue(ReportStatus.OPEN.canTransitionTo(ReportStatus.CLAIMED));
        assertTrue(ReportStatus.OPEN.canTransitionTo(ReportStatus.RESOLVED));
        assertTrue(ReportStatus.OPEN.canTransitionTo(ReportStatus.DISMISSED));
        assertFalse(ReportStatus.OPEN.canTransitionTo(ReportStatus.OPEN));
    }

    @Test
    void claimedReportsCanBeReleasedOrClosedButNotClaimedTwice() {
        assertTrue(ReportStatus.CLAIMED.canTransitionTo(ReportStatus.OPEN));
        assertTrue(ReportStatus.CLAIMED.canTransitionTo(ReportStatus.RESOLVED));
        assertFalse(ReportStatus.CLAIMED.canTransitionTo(ReportStatus.CLAIMED));
    }

    @Test
    void closedReportsCanOnlyBeReopened() {
        for (ReportStatus closed : List.of(ReportStatus.RESOLVED, ReportStatus.DISMISSED)) {
            assertTrue(closed.canTransitionTo(ReportStatus.OPEN));
            assertFalse(closed.canTransitionTo(ReportStatus.CLAIMED));
            assertFalse(closed.canTransitionTo(ReportStatus.RESOLVED));
            assertFalse(closed.isActive());
        }
    }

    @Test
    void filtersCycleThroughAllValues() {
        ReportFilter filter = ReportFilter.ACTIVE;
        for (int i = 0; i < ReportFilter.values().length; i++) {
            filter = filter.next();
        }
        assertEquals(ReportFilter.ACTIVE, filter);
        assertEquals(ReportFilter.CLOSED, ReportFilter.parse(" Closed ").orElseThrow());
        assertTrue(ReportFilter.parse("nope").isEmpty());
    }

    @Test
    void pagesKnowTheirNeighbours() {
        ReportPage first = new ReportPage(List.of(), 46, 0, 45);
        assertEquals(2, first.pages());
        assertFalse(first.hasPrevious());
        assertTrue(first.hasNext());
        ReportPage second = new ReportPage(List.of(), 46, 1, 45);
        assertTrue(second.hasPrevious());
        assertFalse(second.hasNext());
        assertEquals(1, new ReportPage(List.of(), 0, 0, 45).pages());
    }
}
