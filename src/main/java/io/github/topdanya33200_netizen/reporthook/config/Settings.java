package io.github.topdanya33200_netizen.reporthook.config;

import io.github.topdanya33200_netizen.reporthook.discord.DiscordSettings;
import io.github.topdanya33200_netizen.reporthook.discord.WebhookUrl;
import io.github.topdanya33200_netizen.reporthook.model.ReportStatus;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.jspecify.annotations.Nullable;

/**
 * Immutable snapshot of config.yml. A new instance is built on every reload.
 */
public record Settings(
        String language,
        String serverName,
        Duration cooldown,
        int maxReasonLength,
        int retentionDays,
        List<ReasonPreset> reasons,
        ChatContext chatContext,
        Notifications notifications,
        DiscordSettings discord,
        boolean placeholderApi) {

    /** The report menu has room for 4 rows of 7 reasons. */
    public static final int MAX_REASONS = 28;

    private static final Pattern SNOWFLAKE = Pattern.compile("\\d{5,25}");

    public Settings {
        reasons = List.copyOf(reasons);
    }

    public record ChatContext(boolean enabled, int messages, Duration maxAge) {
    }

    public record Notifications(boolean inGame, @Nullable Sound sound, boolean joinReminder, boolean notifyReporter) {
    }

    public static Settings load(ConfigurationSection config, Logger logger) {
        String language = config.getString("language", "en").strip().toLowerCase(Locale.ROOT);
        String serverName = config.getString("server-name", "Server").strip();

        Duration cooldown = Duration.ofSeconds(Math.max(0, config.getLong("reports.cooldown-seconds", 60)));
        int maxReasonLength = Math.clamp(config.getInt("reports.max-reason-length", 200), 16, 1000);
        int retentionDays = Math.max(0, config.getInt("reports.retention-days", 30));

        ChatContext chat = new ChatContext(
                config.getBoolean("chat-context.enabled", true),
                Math.clamp(config.getInt("chat-context.messages", 10), 1, 50),
                Duration.ofMinutes(Math.max(1, config.getLong("chat-context.max-age-minutes", 10))));

        Notifications notifications = new Notifications(
                config.getBoolean("notifications.in-game", true),
                sound(config.getString("notifications.sound", ""), logger),
                config.getBoolean("notifications.join-reminder", true),
                config.getBoolean("notifications.notify-reporter", true));

        return new Settings(language, serverName.isEmpty() ? "Server" : serverName, cooldown, maxReasonLength,
                retentionDays, reasons(config.getConfigurationSection("reasons"), logger), chat, notifications,
                discord(config.getConfigurationSection("discord"), logger),
                config.getBoolean("placeholderapi", true));
    }

    private static List<ReasonPreset> reasons(@Nullable ConfigurationSection section, Logger logger) {
        if (section == null) {
            return List.of(ReasonPreset.fallback());
        }
        List<ReasonPreset> reasons = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection reason = section.getConfigurationSection(id);
            if (reason == null) {
                continue;
            }
            if (reasons.size() == MAX_REASONS) {
                logger.warning("Only the first " + MAX_REASONS + " reasons fit into the report menu, the rest are ignored");
                break;
            }
            reasons.add(new ReasonPreset(id, reason.getString("name", id), reason.getString("icon", "PAPER"),
                    reason.getStringList("description")));
        }
        return reasons.isEmpty() ? List.of(ReasonPreset.fallback()) : reasons;
    }

    private static @Nullable Sound sound(@Nullable String raw, Logger logger) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Sound.sound(Key.key(raw.strip()), Sound.Source.MASTER, 1f, 1f);
        } catch (InvalidKeyException exception) {
            logger.warning("notifications.sound '" + raw + "' is not a valid sound key, notifications will be silent");
            return null;
        }
    }

    private static DiscordSettings discord(@Nullable ConfigurationSection section, Logger logger) {
        if (section == null) {
            return DiscordSettings.disabled();
        }
        boolean enabled = section.getBoolean("enabled", false);
        String rawUrl = section.getString("webhook-url", "");
        WebhookUrl webhook = WebhookUrl.parse(rawUrl).orElse(null);
        if (enabled && webhook == null) {
            logger.warning("discord.enabled is true, but discord.webhook-url is not a valid Discord webhook URL. "
                    + "Discord sync stays off until it is fixed.");
        }

        String role = section.getString("mention-role-id", "").strip();
        if (!role.isEmpty() && !SNOWFLAKE.matcher(role).matches()) {
            logger.warning("discord.mention-role-id must be the numeric id of a role, ignoring it");
            role = "";
        }

        Map<ReportStatus, Integer> colors = new EnumMap<>(ReportStatus.class);
        for (ReportStatus status : ReportStatus.values()) {
            String key = "colors." + status.name().toLowerCase(Locale.ROOT);
            String raw = section.getString(key, null);
            OptionalInt parsed = DiscordSettings.parseHexColor(raw);
            if (raw != null && parsed.isEmpty()) {
                logger.warning("discord." + key + " must be a hex colour like #E74C3C");
            }
            colors.put(status, parsed.orElse(DiscordSettings.defaultColor(status)));
        }

        return new DiscordSettings(
                enabled,
                webhook,
                role.isEmpty() ? null : role,
                section.getString("username", "ReportHook"),
                section.getString("avatar-url", ""),
                section.getString("thumbnail-url", DiscordSettings.DEFAULT_THUMBNAIL),
                section.getBoolean("update-on-status-change", true),
                section.getBoolean("forum-threads", false),
                section.getBoolean("include-location", true),
                section.getBoolean("include-chat-context", true),
                colors);
    }
}
