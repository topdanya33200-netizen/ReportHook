package io.github.topdanya33200_netizen.reporthook.util;

import java.util.regex.Pattern;

/**
 * Cleans text typed by players before it is stored or shown to others.
 */
public final class Text {

    private static final Pattern LEGACY_COLOR = Pattern.compile("(?i)§[0-9a-fk-orx]");
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}\\u2028\\u2029]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private Text() {
    }

    /**
     * Removes legacy {@code §} colour codes and control characters and collapses whitespace.
     * MiniMessage tags are left alone: they are always inserted as unparsed placeholders.
     */
    public static String clean(String input) {
        String withoutColors = LEGACY_COLOR.matcher(input).replaceAll("").replace("§", "");
        String withoutControl = CONTROL.matcher(withoutColors).replaceAll(" ");
        return WHITESPACE.matcher(withoutControl).replaceAll(" ").strip();
    }
}
