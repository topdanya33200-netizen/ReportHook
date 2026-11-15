package io.github.topdanya33200_netizen.reporthook.config;

import java.util.List;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * A reason players can pick in the report menu.
 *
 * @param name        MiniMessage formatted display name
 * @param icon        material name of the menu item
 * @param description MiniMessage lore lines
 */
public record ReasonPreset(String id, String name, String icon, List<String> description) {

    public ReasonPreset {
        description = List.copyOf(description);
    }

    public static ReasonPreset fallback() {
        return new ReasonPreset("other", "<white>Other", "PAPER", List.of());
    }

    /** The name without formatting, which is what gets stored and sent to Discord. */
    public String plainName() {
        return MiniMessage.miniMessage().stripTags(name).strip();
    }
}
