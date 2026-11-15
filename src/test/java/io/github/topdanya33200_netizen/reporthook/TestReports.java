package io.github.topdanya33200_netizen.reporthook;

import io.github.topdanya33200_netizen.reporthook.model.ChatLine;
import io.github.topdanya33200_netizen.reporthook.model.NewReport;
import io.github.topdanya33200_netizen.reporthook.model.Report;
import io.github.topdanya33200_netizen.reporthook.model.ReportLocation;
import io.github.topdanya33200_netizen.reporthook.model.ReportStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Test fixtures. */
public final class TestReports {

    public static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000a1e7");
    public static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-0000000057e7");
    public static final UUID MOD = UUID.fromString("00000000-0000-0000-0000-000000000303");
    public static final Instant NOW = Instant.parse("2026-11-15T19:30:00Z");

    private TestReports() {
    }

    public static NewReport draft(UUID reporter, UUID target, Instant createdAt) {
        return new NewReport(reporter, name(reporter), target, name(target), "Kill aura", "Survival",
                new ReportLocation("world", 120.7, 64.0, -30.2),
                List.of(new ChatLine(createdAt.minusSeconds(5), "ez")), createdAt);
    }

    public static Report report(long id, ReportStatus status) {
        return new Report(id, ALEX, "Alex", STEVE, "Steve", "Kill aura", "Survival",
                new ReportLocation("world", 120.7, 64.0, -30.2),
                List.of(new ChatLine(NOW.minusSeconds(5), "ez")),
                status, status == ReportStatus.OPEN ? null : MOD, status == ReportStatus.OPEN ? null : "Mod",
                null, null, null, NOW, NOW);
    }

    private static String name(UUID id) {
        if (id.equals(ALEX)) {
            return "Alex";
        }
        if (id.equals(STEVE)) {
            return "Steve";
        }
        return "Mod";
    }
}
