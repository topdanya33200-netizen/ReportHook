package io.github.topdanya33200_netizen.reporthook.hook;

import io.github.topdanya33200_netizen.reporthook.ReportHookPlugin;
import io.github.topdanya33200_netizen.reporthook.model.ReportCounts;
import java.util.Locale;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jspecify.annotations.Nullable;

/**
 * PlaceholderAPI placeholders, e.g. for a staff scoreboard:
 * {@code %reporthook_open%}, {@code %reporthook_claimed%}, {@code %reporthook_active%},
 * {@code %reporthook_resolved%}, {@code %reporthook_dismissed%}, {@code %reporthook_total%}.
 *
 * <p>Values come from a cache refreshed after every change, so scoreboards never hit the database.</p>
 */
public final class ReportHookExpansion extends PlaceholderExpansion {

    private final ReportHookPlugin plugin;

    public ReportHookExpansion(ReportHookPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "reporthook";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    /** Keep the expansion registered across {@code /papi reload}. */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(@Nullable OfflinePlayer player, String params) {
        ReportCounts counts = plugin.reports().counts();
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "open" -> Integer.toString(counts.open());
            case "claimed" -> Integer.toString(counts.claimed());
            case "active" -> Integer.toString(counts.active());
            case "resolved" -> Integer.toString(counts.resolved());
            case "dismissed" -> Integer.toString(counts.dismissed());
            case "total" -> Integer.toString(counts.total());
            default -> null;
        };
    }
}
