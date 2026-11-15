package io.github.topdanya33200_netizen.reporthook.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Data for a report that has not been saved yet.
 */
public record NewReport(
        UUID reporterId,
        String reporterName,
        UUID targetId,
        String targetName,
        String reason,
        String serverName,
        @Nullable ReportLocation location,
        List<ChatLine> chatContext,
        Instant createdAt) {

    public NewReport {
        chatContext = List.copyOf(chatContext);
    }
}
