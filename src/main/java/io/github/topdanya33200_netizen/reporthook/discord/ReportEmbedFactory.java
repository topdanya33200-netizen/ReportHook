package io.github.topdanya33200_netizen.reporthook.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.topdanya33200_netizen.reporthook.model.ChatLine;
import io.github.topdanya33200_netizen.reporthook.model.Report;
import io.github.topdanya33200_netizen.reporthook.model.ReportLocation;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Turns a report into a Discord webhook payload.
 *
 * <p>Every payload sets {@code allowed_mentions}, so nothing a player types can ping
 * {@code @everyone} or a role; only the configured staff role is mentioned, and only once
 * when the report is created.</p>
 */
public final class ReportEmbedFactory {

    private static final int TITLE_LIMIT = 256;
    private static final int DESCRIPTION_LIMIT = 4096;
    private static final int FIELD_LIMIT = 1024;
    private static final int FOOTER_LIMIT = 2048;
    private static final int THREAD_NAME_LIMIT = 100;

    private final DiscordSettings settings;
    private final DiscordLabels labels;
    private final String serverName;
    private final DateTimeFormatter clock;

    public ReportEmbedFactory(DiscordSettings settings, DiscordLabels labels, String serverName, ZoneId zone) {
        this.settings = settings;
        this.labels = labels;
        this.serverName = serverName;
        this.clock = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(zone);
    }

    /** Payload for the first message about a new report, including the staff role ping. */
    public JsonObject created(Report report) {
        return payload(report, true);
    }

    /** Payload for editing the message after the report's status changed. */
    public JsonObject updated(Report report) {
        return payload(report, false);
    }

    /** Thread title for forum channels, or {@code null} when the webhook posts into a normal channel. */
    public @Nullable String threadName(Report report) {
        WebhookUrl webhook = settings.webhook();
        if (!settings.forumThreads() || (webhook != null && webhook.threadId() != null)) {
            return null;
        }
        return DiscordText.truncate("#" + report.id() + " " + report.targetName() + " - " + report.reason(), THREAD_NAME_LIMIT);
    }

    private JsonObject payload(Report report, boolean mention) {
        JsonObject payload = new JsonObject();
        if (!settings.username().isBlank()) {
            payload.addProperty("username", DiscordText.truncate(settings.username(), 80));
        }
        if (!settings.avatarUrl().isBlank()) {
            payload.addProperty("avatar_url", settings.avatarUrl());
        }

        JsonObject allowedMentions = new JsonObject();
        allowedMentions.add("parse", new JsonArray());
        String role = settings.mentionRoleId();
        if (mention && role != null) {
            payload.addProperty("content", "<@&" + role + ">");
            JsonArray roles = new JsonArray();
            roles.add(role);
            allowedMentions.add("roles", roles);
        }
        payload.add("allowed_mentions", allowedMentions);

        JsonArray embeds = new JsonArray();
        embeds.add(embed(report));
        payload.add("embeds", embeds);
        return payload;
    }

    private JsonObject embed(Report report) {
        JsonObject embed = new JsonObject();
        String title = labels.title().replace("{id}", Long.toString(report.id())) + " · " + report.targetName();
        embed.addProperty("title", DiscordText.truncate(title, TITLE_LIMIT));
        embed.addProperty("color", settings.color(report.status()));
        String description = "**" + DiscordText.escape(labels.reason()) + "**\n> " + DiscordText.escape(report.reason());
        embed.addProperty("description", DiscordText.truncate(description, DESCRIPTION_LIMIT));

        JsonArray fields = new JsonArray();
        fields.add(field(labels.reportedPlayer(),
                DiscordText.inlineCode(report.targetName()) + "\n" + DiscordText.inlineCode(report.targetId().toString()), true));
        fields.add(field(labels.reporter(), DiscordText.inlineCode(report.reporterName()), true));
        fields.add(field(labels.server(), DiscordText.escape(serverName), true));
        ReportLocation location = report.location();
        if (settings.includeLocation() && location != null) {
            fields.add(field(labels.location(), DiscordText.inlineCode(location.world()) + " "
                    + location.blockX() + ", " + location.blockY() + ", " + location.blockZ(), true));
        }
        fields.add(field(labels.status(), statusText(report), true));
        String note = report.note();
        if (note != null && !note.isBlank()) {
            fields.add(field(labels.note(), DiscordText.escape(note), false));
        }
        if (settings.includeChatContext() && !report.chatContext().isEmpty()) {
            List<String> lines = new ArrayList<>();
            for (ChatLine line : report.chatContext()) {
                lines.add("[" + clock.format(line.time()) + "] " + line.message());
            }
            fields.add(field(labels.chat(), DiscordText.codeBlock(lines, FIELD_LIMIT), false));
        }
        embed.add("fields", fields);

        if (!settings.thumbnailUrl().isBlank()) {
            JsonObject thumbnail = new JsonObject();
            thumbnail.addProperty("url", settings.thumbnailUrl()
                    .replace("{uuid}", report.targetId().toString())
                    .replace("{name}", report.targetName()));
            embed.add("thumbnail", thumbnail);
        }

        JsonObject footer = new JsonObject();
        footer.addProperty("text", DiscordText.truncate(labels.footer() + " · " + serverName, FOOTER_LIMIT));
        embed.add("footer", footer);
        embed.addProperty("timestamp", report.createdAt().toString());
        return embed;
    }

    private String statusText(Report report) {
        String template = labels.statuses().getOrDefault(report.status(), report.status().name());
        String handler = report.handlerName();
        return template.replace("{handler}", handler == null ? "?" : DiscordText.escape(handler));
    }

    private static JsonObject field(String name, String value, boolean inline) {
        JsonObject field = new JsonObject();
        field.addProperty("name", DiscordText.truncate(name, TITLE_LIMIT));
        field.addProperty("value", DiscordText.truncate(value.isEmpty() ? "-" : value, FIELD_LIMIT));
        field.addProperty("inline", inline);
        return field;
    }
}
