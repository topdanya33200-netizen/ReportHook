package io.github.topdanya33200_netizen.reporthook.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WebhookUrlTest {

    private static final String TOKEN = "AbCdEfGhIjKlMnOpQrStUvWxYz_0123456789-abc";

    @Test
    void parsesStandardUrl() {
        WebhookUrl url = WebhookUrl.parse("https://discord.com/api/webhooks/123456789012345678/" + TOKEN).orElseThrow();
        assertEquals("123456789012345678", url.id());
        assertEquals(TOKEN, url.token());
        assertNull(url.threadId());
    }

    @Test
    void acceptsOtherDiscordHostsAndApiVersions() {
        assertTrue(WebhookUrl.parse("https://discordapp.com/api/webhooks/123456789/" + TOKEN).isPresent());
        assertTrue(WebhookUrl.parse("https://canary.discord.com/api/v10/webhooks/123456789/" + TOKEN + "/").isPresent());
        assertTrue(WebhookUrl.parse("  https://ptb.discord.com/api/webhooks/123456789/" + TOKEN + "  ").isPresent());
    }

    @Test
    void readsThreadIdFromQuery() {
        WebhookUrl url = WebhookUrl.parse("https://discord.com/api/webhooks/123456789/" + TOKEN + "?thread_id=987654321").orElseThrow();
        assertEquals("987654321", url.threadId());
        assertTrue(url.executeUri().toString().endsWith("?wait=true&thread_id=987654321"));
    }

    @Test
    void rejectsAnythingElse() {
        assertTrue(WebhookUrl.parse(null).isEmpty());
        assertTrue(WebhookUrl.parse("").isEmpty());
        assertTrue(WebhookUrl.parse("http://discord.com/api/webhooks/123456789/" + TOKEN).isEmpty());
        assertTrue(WebhookUrl.parse("https://discord.com.evil.example/api/webhooks/123456789/" + TOKEN).isEmpty());
        assertTrue(WebhookUrl.parse("https://discord.com/api/webhooks/abc/" + TOKEN).isEmpty());
        assertTrue(WebhookUrl.parse("https://discord.com/api/webhooks/123456789/short").isEmpty());
    }

    @Test
    void neverPrintsTheToken() {
        WebhookUrl url = WebhookUrl.parse("https://discord.com/api/webhooks/123456789/" + TOKEN).orElseThrow();
        assertFalse(url.toString().contains(TOKEN));
        assertFalse(url.masked().contains(TOKEN));
    }

    @Test
    void buildsMessageUris() {
        WebhookUrl url = WebhookUrl.parse("https://discord.com/api/webhooks/123456789/" + TOKEN).orElseThrow();
        assertEquals("https://discord.com/api/webhooks/123456789/" + TOKEN + "/messages/555666777",
                url.messageUri("555666777", null).toString());
        assertEquals("https://discord.com/api/webhooks/123456789/" + TOKEN + "/messages/555666777?thread_id=111222333",
                url.messageUri("555666777", "111222333").toString());
        assertThrows(IllegalArgumentException.class, () -> url.messageUri("../../evil", null));
    }
}
