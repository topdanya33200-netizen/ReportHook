package io.github.topdanya33200_netizen.reporthook.config;

import io.github.topdanya33200_netizen.reporthook.discord.DiscordLabels;
import io.github.topdanya33200_netizen.reporthook.model.ReportStatus;
import io.github.topdanya33200_netizen.reporthook.util.TimeFormat;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

/**
 * Language file loaded from {@code plugins/ReportHook/lang/messages_<language>.yml}.
 *
 * <p>Messages use MiniMessage. Values typed by players are always inserted with
 * {@link Placeholder#unparsed(String, String)}, so they can't smuggle in click events or
 * colours. Numeric ids may also be written as {@code {id}}, which works inside
 * {@code <click:...>} arguments where MiniMessage tags do not.</p>
 */
public final class Messages {

    private static final List<String> BUNDLED = List.of("en", "ru");
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final Map<String, String> strings;
    private final TagResolver prefix;

    private Messages(Map<String, String> strings) {
        this.strings = Map.copyOf(strings);
        this.prefix = Placeholder.parsed("prefix", this.strings.getOrDefault("prefix", ""));
    }

    /** Copies the bundled language files to the data folder unless they already exist. */
    public static void saveBundled(JavaPlugin plugin) {
        for (String language : BUNDLED) {
            String path = "lang/messages_" + language + ".yml";
            if (!new File(plugin.getDataFolder(), path).exists()) {
                plugin.saveResource(path, false);
            }
        }
    }

    /**
     * Loads a language. Keys missing from the user's file fall back to the bundled version,
     * so new messages added by an update always show up.
     */
    public static Messages load(JavaPlugin plugin, String language) {
        Map<String, String> strings = new HashMap<>();
        readInto(strings, plugin.getResource("lang/messages_en.yml"));
        if (!language.equals("en") && BUNDLED.contains(language)) {
            readInto(strings, plugin.getResource("lang/messages_" + language + ".yml"));
        }
        File file = new File(plugin.getDataFolder(), "lang/messages_" + language + ".yml");
        if (file.isFile()) {
            readInto(strings, YamlConfiguration.loadConfiguration(file));
        } else {
            plugin.getLogger().warning("lang/messages_" + language + ".yml was not found, using the built-in messages");
        }
        return new Messages(strings);
    }

    private static void readInto(Map<String, String> target, @Nullable InputStream stream) {
        if (stream == null) {
            return;
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            readInto(target, YamlConfiguration.loadConfiguration(reader));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void readInto(Map<String, String> target, YamlConfiguration yaml) {
        for (String key : yaml.getKeys(true)) {
            if (yaml.isConfigurationSection(key)) {
                continue;
            }
            if (yaml.isList(key)) {
                target.put(key, String.join("\n", yaml.getStringList(key)));
            } else {
                String value = yaml.getString(key);
                if (value != null) {
                    target.put(key, value);
                }
            }
        }
    }

    /** The unformatted entry, or the key itself so missing translations are easy to spot. */
    public String raw(String key) {
        return strings.getOrDefault(key, key);
    }

    /** The entry with MiniMessage tags removed, for Discord and console output. */
    public String plain(String key) {
        return MINI.stripTags(raw(key));
    }

    public Component render(String key, TagResolver... resolvers) {
        return render(key, Map.of(), resolvers);
    }

    public Component render(String key, Map<String, String> literals, TagResolver... resolvers) {
        return MINI.deserialize(substitute(raw(key), literals), resolver(resolvers));
    }

    /** One component per line, for item lore. */
    public List<Component> lines(String key, TagResolver... resolvers) {
        String raw = raw(key);
        if (raw.isEmpty()) {
            return List.of();
        }
        TagResolver resolver = resolver(resolvers);
        List<Component> lines = new ArrayList<>();
        for (String line : raw.split("\n", -1)) {
            lines.add(MINI.deserialize(line, resolver));
        }
        return lines;
    }

    public void send(Audience audience, String key, TagResolver... resolvers) {
        send(audience, key, Map.of(), resolvers);
    }

    /** Sends the message unless it was set to an empty string, which disables it. */
    public void send(Audience audience, String key, Map<String, String> literals, TagResolver... resolvers) {
        if (!raw(key).isEmpty()) {
            audience.sendMessage(render(key, literals, resolvers));
        }
    }

    public Component status(ReportStatus status) {
        return render("status." + status.name().toLowerCase(Locale.ROOT));
    }

    public TimeFormat.Units timeUnits() {
        return new TimeFormat.Units(raw("time.days"), raw("time.hours"), raw("time.minutes"), raw("time.seconds"));
    }

    public DiscordLabels discordLabels() {
        Map<ReportStatus, String> statuses = new EnumMap<>(ReportStatus.class);
        for (ReportStatus status : ReportStatus.values()) {
            statuses.put(status, raw("discord.status." + status.name().toLowerCase(Locale.ROOT)));
        }
        return new DiscordLabels(
                raw("discord.title"),
                raw("discord.field.reported"),
                raw("discord.field.reporter"),
                raw("discord.field.reason"),
                raw("discord.field.server"),
                raw("discord.field.location"),
                raw("discord.field.status"),
                raw("discord.field.note"),
                raw("discord.field.chat"),
                raw("discord.footer"),
                statuses);
    }

    private TagResolver resolver(TagResolver... resolvers) {
        return TagResolver.resolver(prefix, TagResolver.resolver(resolvers));
    }

    private static String substitute(String raw, Map<String, String> literals) {
        String result = raw;
        for (Map.Entry<String, String> literal : literals.entrySet()) {
            result = result.replace("{" + literal.getKey() + "}", literal.getValue());
        }
        return result;
    }
}
