package io.github.topdanya33200_netizen.reporthook.discord;

import io.github.topdanya33200_netizen.reporthook.model.ReportStatus;
import java.util.EnumMap;
import java.util.Map;

/**
 * Translatable texts used in Discord embeds. {@code {id}} and {@code {handler}} are replaced.
 */
public record DiscordLabels(
        String title,
        String reportedPlayer,
        String reporter,
        String reason,
        String server,
        String location,
        String status,
        String note,
        String chat,
        String footer,
        Map<ReportStatus, String> statuses) {

    public DiscordLabels {
        EnumMap<ReportStatus, String> copy = new EnumMap<>(ReportStatus.class);
        copy.putAll(defaultStatuses());
        copy.putAll(statuses);
        statuses = Map.copyOf(copy);
    }

    public static DiscordLabels defaults() {
        return new DiscordLabels("Report #{id}", "Reported player", "Reporter", "Reason", "Server", "Location",
                "Status", "Staff note", "Recent chat", "ReportHook", Map.of());
    }

    private static Map<ReportStatus, String> defaultStatuses() {
        return Map.of(
                ReportStatus.OPEN, "🔴 Open",
                ReportStatus.CLAIMED, "🟡 Claimed by {handler}",
                ReportStatus.RESOLVED, "🟢 Resolved by {handler}",
                ReportStatus.DISMISSED, "⚪ Dismissed by {handler}");
    }
}
