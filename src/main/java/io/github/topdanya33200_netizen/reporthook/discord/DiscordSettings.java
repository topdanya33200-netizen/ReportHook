package io.github.topdanya33200_netizen.reporthook.discord;

import io.github.topdanya33200_netizen.reporthook.model.ReportStatus;
import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalInt;
import org.jspecify.annotations.Nullable;

/**
 * The {@code discord} section of config.yml.
 */
public record DiscordSettings(
        boolean enabled,
        @Nullable WebhookUrl webhook,
        @Nullable String mentionRoleId,
        String username,
        String avatarUrl,
        String thumbnailUrl,
        boolean updateOnStatusChange,
        boolean forumThreads,
        boolean includeLocation,
        boolean includeChatContext,
        Map<ReportStatus, Integer> colors) {

    public static final String DEFAULT_THUMBNAIL = "https://mc-heads.net/avatar/{uuid}/64";

    public DiscordSettings {
        EnumMap<ReportStatus, Integer> copy = new EnumMap<>(ReportStatus.class);
        copy.putAll(colors);
        colors = Map.copyOf(copy);
    }

    public static DiscordSettings disabled() {
        return new DiscordSettings(false, null, null, "ReportHook", "", DEFAULT_THUMBNAIL,
                true, false, true, true, Map.of());
    }

    /** Discord sync runs only when it is switched on and the URL is valid. */
    public boolean active() {
        return enabled && webhook != null;
    }

    /** Whether messages live in threads, which changes how they must be edited. */
    public boolean usesThreads() {
        return forumThreads || (webhook != null && webhook.threadId() != null);
    }

    public int color(ReportStatus status) {
        Integer color = colors.get(status);
        return color != null ? color : defaultColor(status);
    }

    public static int defaultColor(ReportStatus status) {
        return switch (status) {
            case OPEN -> 0xE74C3C;
            case CLAIMED -> 0xF1C40F;
            case RESOLVED -> 0x2ECC71;
            case DISMISSED -> 0x95A5A6;
        };
    }

    /** Parses {@code #RRGGBB} or {@code RRGGBB}. */
    public static OptionalInt parseHexColor(@Nullable String raw) {
        if (raw == null) {
            return OptionalInt.empty();
        }
        String hex = raw.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.length() != 6) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(hex, 16));
        } catch (NumberFormatException exception) {
            return OptionalInt.empty();
        }
    }
}
