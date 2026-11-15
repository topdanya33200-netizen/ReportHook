package io.github.topdanya33200_netizen.reporthook.discord;

/**
 * Discord answered with an error status.
 */
public final class WebhookException extends Exception {

    private final int status;

    public WebhookException(int status, String body) {
        super("Discord answered HTTP " + status + ": " + DiscordText.truncate(body.strip(), 300));
        this.status = status;
    }

    public int status() {
        return status;
    }
}
