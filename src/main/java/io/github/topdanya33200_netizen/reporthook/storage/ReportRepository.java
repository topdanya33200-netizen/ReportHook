package io.github.topdanya33200_netizen.reporthook.storage;

import io.github.topdanya33200_netizen.reporthook.model.NewReport;
import io.github.topdanya33200_netizen.reporthook.model.Report;
import io.github.topdanya33200_netizen.reporthook.model.ReportStatus;
import io.github.topdanya33200_netizen.reporthook.model.TransitionResult;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Persistence for reports. Implementations are blocking and must only be called from
 * one thread at a time; {@link io.github.topdanya33200_netizen.reporthook.service.ReportService}
 * runs every call on its own database thread.
 */
public interface ReportRepository extends AutoCloseable {

    void open() throws SQLException;

    Report insert(NewReport report) throws SQLException;

    Optional<Report> find(long id) throws SQLException;

    /** Newest first. */
    List<Report> list(Set<ReportStatus> statuses, int limit, int offset) throws SQLException;

    int count(Set<ReportStatus> statuses) throws SQLException;

    Map<ReportStatus, Integer> countByStatus() throws SQLException;

    /** Reports filed against a player, newest first. */
    List<Report> findByTarget(UUID targetId, int limit) throws SQLException;

    /** Whether {@code reporterId} already has an open or claimed report against {@code targetId}. */
    boolean hasActiveReport(UUID reporterId, UUID targetId) throws SQLException;

    TransitionResult transition(long id, ReportStatus next, @Nullable UUID handlerId,
                                @Nullable String handlerName, @Nullable String note, Instant now) throws SQLException;

    void setDiscordMessage(long id, String messageId, @Nullable String channelId) throws SQLException;

    /** Deletes resolved and dismissed reports last changed before {@code cutoff}. */
    int purgeClosedBefore(Instant cutoff) throws SQLException;

    @Override
    void close();
}
