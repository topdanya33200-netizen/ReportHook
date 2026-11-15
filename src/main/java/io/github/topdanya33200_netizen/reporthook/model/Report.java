package io.github.topdanya33200_netizen.reporthook.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A stored report. Instances are immutable snapshots of a database row.
 */
public record Report(
        long id,
        UUID reporterId,
        String reporterName,
        UUID targetId,
        String targetName,
        String reason,
        String serverName,
        @Nullable ReportLocation location,
        List<ChatLine> chatContext,
        ReportStatus status,
        @Nullable UUID handlerId,
        @Nullable String handlerName,
        @Nullable String note,
        @Nullable String discordMessageId,
        @Nullable String discordChannelId,
        Instant createdAt,
        Instant updatedAt) {

    public Report {
        chatContext = List.copyOf(chatContext);
    }
}
