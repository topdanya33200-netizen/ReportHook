package io.github.topdanya33200_netizen.reporthook.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DiscordWebhookClientTest {

    @Test
    void readsRetryAfterFromBody() {
        assertEquals(1500, DiscordWebhookClient.retryAfterMillis("{\"message\":\"You are being rate limited.\",\"retry_after\":1.5}", null));
    }

    @Test
    void fallsBackToHeader() {
        assertEquals(2000, DiscordWebhookClient.retryAfterMillis("not json", "2"));
    }

    @Test
    void clampsWaitingTime() {
        assertEquals(250, DiscordWebhookClient.retryAfterMillis("{\"retry_after\":0.01}", null));
        assertEquals(30_000, DiscordWebhookClient.retryAfterMillis("{\"retry_after\":3600}", null));
        assertEquals(1000, DiscordWebhookClient.retryAfterMillis(null, null));
    }

    @Test
    void backoffGrowsAndCaps() {
        assertEquals(500, DiscordWebhookClient.backoffMillis(1));
        assertEquals(1000, DiscordWebhookClient.backoffMillis(2));
        assertEquals(8000, DiscordWebhookClient.backoffMillis(10));
    }
}
