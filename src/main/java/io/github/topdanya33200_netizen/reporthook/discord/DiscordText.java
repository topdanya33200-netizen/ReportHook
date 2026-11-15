package io.github.topdanya33200_netizen.reporthook.discord;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Makes player-written text safe to put into Discord embeds.
 */
public final class DiscordText {

    /** Characters Discord treats as markdown; a backslash in front renders them literally. */
    private static final String MARKDOWN = "\\*_~`|>#[]()-<";
    private static final char ZERO_WIDTH_SPACE = '​';
    private static final String ELLIPSIS = "…";

    private DiscordText() {
    }

    /**
     * Escapes markdown and breaks up mentions such as {@code @everyone}, so a report reason
     * can't format the embed, hide text behind links or look like a ping.
     */
    public static String escape(String input) {
        StringBuilder out = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (MARKDOWN.indexOf(c) >= 0) {
                out.append('\\');
            }
            out.append(c);
            if (c == '@') {
                out.append(ZERO_WIDTH_SPACE);
            }
        }
        return out.toString();
    }

    /** Cuts {@code input} to at most {@code max} characters, ending with an ellipsis when shortened. */
    public static String truncate(String input, int max) {
        if (input.length() <= max) {
            return input;
        }
        int end = max - ELLIPSIS.length();
        // Never split a surrogate pair (emoji) in half
        if (end > 0 && Character.isHighSurrogate(input.charAt(end - 1))) {
            end--;
        }
        return input.substring(0, Math.max(0, end)) + ELLIPSIS;
    }

    /** Wraps text in single backticks for inline code. */
    public static String inlineCode(String input) {
        return "`" + input.replace('`', 'ˋ') + "`";
    }

    /**
     * Builds a code block that fits into {@code maxLength} characters, keeping the newest lines.
     */
    public static String codeBlock(List<String> lines, int maxLength) {
        String open = "```\n";
        String close = "\n```";
        int budget = maxLength - open.length() - close.length();
        Deque<String> kept = new ArrayDeque<>();
        int used = 0;
        for (int i = lines.size() - 1; i >= 0; i--) {
            // A line containing ``` would close the block early
            String line = lines.get(i).replace("```", "`" + ZERO_WIDTH_SPACE + "`" + ZERO_WIDTH_SPACE + "`");
            int cost = line.length() + (kept.isEmpty() ? 0 : 1);
            if (used + cost > budget) {
                if (kept.isEmpty()) {
                    kept.addFirst(truncate(line, budget));
                }
                break;
            }
            kept.addFirst(line);
            used += cost;
        }
        return open + String.join("\n", kept) + close;
    }
}
