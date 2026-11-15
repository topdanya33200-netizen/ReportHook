package io.github.topdanya33200_netizen.reporthook.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class DiscordTextTest {

    @Test
    void escapesMarkdown() {
        assertEquals("\\*\\*bold\\*\\* \\_x\\_ \\~\\~s\\~\\~ \\`c\\` \\|\\|spoiler\\|\\|",
                DiscordText.escape("**bold** _x_ ~~s~~ `c` ||spoiler||"));
    }

    @Test
    void escapesMaskedLinks() {
        assertEquals("\\[free nitro\\]\\(https://evil.example\\)", DiscordText.escape("[free nitro](https://evil.example)"));
    }

    @Test
    void breaksUpMentions() {
        String escaped = DiscordText.escape("@everyone <@&123>");
        assertFalse(escaped.contains("@everyone"));
        assertFalse(escaped.contains("<@&"));
    }

    @Test
    void truncatesWithEllipsis() {
        assertEquals("abc", DiscordText.truncate("abc", 3));
        assertEquals("ab…", DiscordText.truncate("abcdef", 3));
    }

    @Test
    void truncateNeverSplitsAnEmoji() {
        String text = "ab😀cd"; // ab😀cd
        String cut = DiscordText.truncate(text, 4);
        assertEquals("ab…", cut);
    }

    @Test
    void codeBlockKeepsNewestLinesWithinLimit() {
        List<String> lines = List.of("first line", "second line", "third line");
        String block = DiscordText.codeBlock(lines, 30);
        assertTrue(block.length() <= 30, block);
        assertTrue(block.contains("third line"));
        assertFalse(block.contains("first line"));
        assertTrue(block.startsWith("```\n"));
        assertTrue(block.endsWith("\n```"));
    }

    @Test
    void codeBlockCannotBeClosedFromInside() {
        String block = DiscordText.codeBlock(List.of("```evil```"), 1024);
        String inner = block.substring(4, block.length() - 4);
        assertFalse(inner.contains("```"));
    }

    @Test
    void inlineCodeCannotBeClosedFromInside() {
        assertEquals("`aˋb`", DiscordText.inlineCode("a`b"));
    }
}
