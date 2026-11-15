package io.github.topdanya33200_netizen.reporthook.storage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.topdanya33200_netizen.reporthook.model.ChatLine;
import io.github.topdanya33200_netizen.reporthook.model.NewReport;
import io.github.topdanya33200_netizen.reporthook.model.Report;
import io.github.topdanya33200_netizen.reporthook.model.ReportLocation;
import io.github.topdanya33200_netizen.reporthook.model.ReportStatus;
import io.github.topdanya33200_netizen.reporthook.model.TransitionResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * SQLite storage. Paper bundles the SQLite JDBC driver, so nothing has to be shaded.
 */
public final class SqliteReportRepository implements ReportRepository {

    private static final int SCHEMA_VERSION = 1;

    private final Path file;
    private @Nullable Connection connection;

    public SqliteReportRepository(Path file) {
        this.file = file;
    }

    @Override
    public void open() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException exception) {
            throw new SQLException("The SQLite JDBC driver is not available on this server", exception);
        }
        Path absolute = file.toAbsolutePath();
        try {
            Files.createDirectories(absolute.getParent());
        } catch (IOException exception) {
            throw new SQLException("Could not create " + absolute.getParent(), exception);
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + absolute);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("PRAGMA busy_timeout=5000");
        }
        migrate();
    }

    private void migrate() throws SQLException {
        int version;
        try (Statement statement = connection().createStatement();
             ResultSet result = statement.executeQuery("PRAGMA user_version")) {
            version = result.next() ? result.getInt(1) : 0;
        }
        if (version >= SCHEMA_VERSION) {
            return;
        }
        try (Statement statement = connection().createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS reports (
                        id                 INTEGER PRIMARY KEY AUTOINCREMENT,
                        reporter_uuid      TEXT    NOT NULL,
                        reporter_name      TEXT    NOT NULL,
                        target_uuid        TEXT    NOT NULL,
                        target_name        TEXT    NOT NULL,
                        reason             TEXT    NOT NULL,
                        server_name        TEXT    NOT NULL,
                        world              TEXT,
                        x                  REAL,
                        y                  REAL,
                        z                  REAL,
                        chat_context       TEXT,
                        status             TEXT    NOT NULL,
                        handler_uuid       TEXT,
                        handler_name       TEXT,
                        note               TEXT,
                        discord_message_id TEXT,
                        discord_channel_id TEXT,
                        created_at         INTEGER NOT NULL,
                        updated_at         INTEGER NOT NULL
                    )""");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_reports_status ON reports (status, created_at)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_reports_target ON reports (target_uuid, created_at)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_reports_pair ON reports (reporter_uuid, target_uuid, status)");
            statement.execute("PRAGMA user_version = " + SCHEMA_VERSION);
        }
    }

    @Override
    public Report insert(NewReport report) throws SQLException {
        String sql = """
                INSERT INTO reports (reporter_uuid, reporter_name, target_uuid, target_name, reason, server_name,
                                     world, x, y, z, chat_context, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";
        long id;
        try (PreparedStatement statement = connection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, report.reporterId().toString());
            statement.setString(2, report.reporterName());
            statement.setString(3, report.targetId().toString());
            statement.setString(4, report.targetName());
            statement.setString(5, report.reason());
            statement.setString(6, report.serverName());
            ReportLocation location = report.location();
            if (location == null) {
                statement.setNull(7, Types.VARCHAR);
                statement.setNull(8, Types.REAL);
                statement.setNull(9, Types.REAL);
                statement.setNull(10, Types.REAL);
            } else {
                statement.setString(7, location.world());
                statement.setDouble(8, location.x());
                statement.setDouble(9, location.y());
                statement.setDouble(10, location.z());
            }
            setNullable(statement, 11, encodeChat(report.chatContext()));
            statement.setString(12, ReportStatus.OPEN.name());
            statement.setLong(13, report.createdAt().toEpochMilli());
            statement.setLong(14, report.createdAt().toEpochMilli());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("SQLite did not return an id for the new report");
                }
                id = keys.getLong(1);
            }
        }
        return find(id).orElseThrow(() -> new SQLException("Report " + id + " is missing right after insert"));
    }

    @Override
    public Optional<Report> find(long id) throws SQLException {
        try (PreparedStatement statement = connection().prepareStatement("SELECT * FROM reports WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        }
    }

    @Override
    public List<Report> list(Set<ReportStatus> statuses, int limit, int offset) throws SQLException {
        if (statuses.isEmpty()) {
            return List.of();
        }
        String sql = "SELECT * FROM reports WHERE status IN (" + placeholders(statuses.size())
                + ") ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?";
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            int index = bindStatuses(statement, statuses, 1);
            statement.setInt(index, limit);
            statement.setInt(index + 1, offset);
            return mapAll(statement);
        }
    }

    @Override
    public int count(Set<ReportStatus> statuses) throws SQLException {
        if (statuses.isEmpty()) {
            return 0;
        }
        String sql = "SELECT COUNT(*) FROM reports WHERE status IN (" + placeholders(statuses.size()) + ")";
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            bindStatuses(statement, statuses, 1);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    @Override
    public Map<ReportStatus, Integer> countByStatus() throws SQLException {
        Map<ReportStatus, Integer> counts = new EnumMap<>(ReportStatus.class);
        try (Statement statement = connection().createStatement();
             ResultSet result = statement.executeQuery("SELECT status, COUNT(*) FROM reports GROUP BY status")) {
            while (result.next()) {
                counts.put(ReportStatus.valueOf(result.getString(1)), result.getInt(2));
            }
        }
        return counts;
    }

    @Override
    public List<Report> findByTarget(UUID targetId, int limit) throws SQLException {
        String sql = "SELECT * FROM reports WHERE target_uuid = ? ORDER BY created_at DESC, id DESC LIMIT ?";
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            statement.setString(1, targetId.toString());
            statement.setInt(2, limit);
            return mapAll(statement);
        }
    }

    @Override
    public boolean hasActiveReport(UUID reporterId, UUID targetId) throws SQLException {
        String sql = "SELECT 1 FROM reports WHERE reporter_uuid = ? AND target_uuid = ? AND status IN (?, ?) LIMIT 1";
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            statement.setString(1, reporterId.toString());
            statement.setString(2, targetId.toString());
            statement.setString(3, ReportStatus.OPEN.name());
            statement.setString(4, ReportStatus.CLAIMED.name());
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    @Override
    public TransitionResult transition(long id, ReportStatus next, @Nullable UUID handlerId,
                                       @Nullable String handlerName, @Nullable String note, Instant now) throws SQLException {
        Optional<Report> found = find(id);
        if (found.isEmpty()) {
            return new TransitionResult.NotFound(id);
        }
        Report current = found.get();
        if (!current.status().canTransitionTo(next)) {
            return new TransitionResult.Rejected(current);
        }
        // Back to OPEN means nobody is handling the report any more
        boolean keepsHandler = next != ReportStatus.OPEN;
        boolean keepsNote = next == ReportStatus.RESOLVED || next == ReportStatus.DISMISSED;
        String sql = """
                UPDATE reports
                SET status = ?, handler_uuid = ?, handler_name = ?, note = ?, updated_at = ?
                WHERE id = ? AND status = ?""";
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            statement.setString(1, next.name());
            setNullable(statement, 2, keepsHandler && handlerId != null ? handlerId.toString() : null);
            setNullable(statement, 3, keepsHandler ? handlerName : null);
            setNullable(statement, 4, keepsNote ? note : null);
            statement.setLong(5, now.toEpochMilli());
            statement.setLong(6, id);
            // Compare-and-set on the old status so two staff members cannot overwrite each other
            statement.setString(7, current.status().name());
            if (statement.executeUpdate() == 0) {
                return new TransitionResult.Rejected(find(id).orElse(current));
            }
        }
        Report updated = find(id).orElseThrow(() -> new SQLException("Report " + id + " vanished during update"));
        return new TransitionResult.Changed(updated, current.status());
    }

    @Override
    public void setDiscordMessage(long id, String messageId, @Nullable String channelId) throws SQLException {
        String sql = "UPDATE reports SET discord_message_id = ?, discord_channel_id = ? WHERE id = ?";
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            statement.setString(1, messageId);
            setNullable(statement, 2, channelId);
            statement.setLong(3, id);
            statement.executeUpdate();
        }
    }

    @Override
    public int purgeClosedBefore(Instant cutoff) throws SQLException {
        String sql = "DELETE FROM reports WHERE status IN (?, ?) AND updated_at < ?";
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            statement.setString(1, ReportStatus.RESOLVED.name());
            statement.setString(2, ReportStatus.DISMISSED.name());
            statement.setLong(3, cutoff.toEpochMilli());
            return statement.executeUpdate();
        }
    }

    @Override
    public void close() {
        Connection current = connection;
        connection = null;
        if (current == null) {
            return;
        }
        try {
            current.close();
        } catch (SQLException ignored) {
            // Nothing useful can be done while shutting down
        }
    }

    private Connection connection() throws SQLException {
        Connection current = connection;
        if (current == null) {
            throw new SQLException("The report database is not open");
        }
        return current;
    }

    private static List<Report> mapAll(PreparedStatement statement) throws SQLException {
        List<Report> reports = new ArrayList<>();
        try (ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                reports.add(map(result));
            }
        }
        return reports;
    }

    private static Report map(ResultSet result) throws SQLException {
        String world = result.getString("world");
        ReportLocation location = world == null
                ? null
                : new ReportLocation(world, result.getDouble("x"), result.getDouble("y"), result.getDouble("z"));
        return new Report(
                result.getLong("id"),
                UUID.fromString(result.getString("reporter_uuid")),
                result.getString("reporter_name"),
                UUID.fromString(result.getString("target_uuid")),
                result.getString("target_name"),
                result.getString("reason"),
                result.getString("server_name"),
                location,
                decodeChat(result.getString("chat_context")),
                ReportStatus.valueOf(result.getString("status")),
                uuidOrNull(result.getString("handler_uuid")),
                result.getString("handler_name"),
                result.getString("note"),
                result.getString("discord_message_id"),
                result.getString("discord_channel_id"),
                Instant.ofEpochMilli(result.getLong("created_at")),
                Instant.ofEpochMilli(result.getLong("updated_at")));
    }

    private static String placeholders(int count) {
        return String.join(", ", Collections.nCopies(count, "?"));
    }

    private static int bindStatuses(PreparedStatement statement, Set<ReportStatus> statuses, int start) throws SQLException {
        int index = start;
        for (ReportStatus status : statuses) {
            statement.setString(index++, status.name());
        }
        return index;
    }

    private static void setNullable(PreparedStatement statement, int index, @Nullable String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    private static @Nullable UUID uuidOrNull(@Nullable String value) {
        return value == null ? null : UUID.fromString(value);
    }

    static @Nullable String encodeChat(List<ChatLine> lines) {
        if (lines.isEmpty()) {
            return null;
        }
        JsonArray array = new JsonArray();
        for (ChatLine line : lines) {
            JsonObject entry = new JsonObject();
            entry.addProperty("t", line.time().toEpochMilli());
            entry.addProperty("m", line.message());
            array.add(entry);
        }
        return array.toString();
    }

    static List<ChatLine> decodeChat(@Nullable String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<ChatLine> lines = new ArrayList<>();
            for (JsonElement element : JsonParser.parseString(json).getAsJsonArray()) {
                JsonObject entry = element.getAsJsonObject();
                lines.add(new ChatLine(Instant.ofEpochMilli(entry.get("t").getAsLong()), entry.get("m").getAsString()));
            }
            return lines;
        } catch (RuntimeException exception) {
            // A damaged chat log should never hide the report itself
            return List.of();
        }
    }
}
