package io.github.topdanya33200_netizen.reporthook.discord;

import java.net.URI;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * A validated Discord webhook URL. {@link #toString()} never prints the token.
 *
 * @param threadId set when the URL already points at a thread ({@code ?thread_id=...})
 */
public record WebhookUrl(String id, String token, @Nullable String threadId) {

    private static final Pattern URL = Pattern.compile(
            "^https://(?:(?:ptb|canary)\\.)?discord(?:app)?\\.com/api(?:/v\\d+)?/webhooks/(\\d{5,25})/([A-Za-z0-9_-]{20,200})/?(?:\\?(.*))?$");
    private static final Pattern THREAD_PARAM = Pattern.compile("(?:^|&)thread_id=(\\d{5,25})(?:&|$)");
    private static final Pattern SNOWFLAKE = Pattern.compile("\\d{5,25}");
    private static final String BASE = "https://discord.com/api/webhooks/";

    public static Optional<WebhookUrl> parse(@Nullable String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        Matcher matcher = URL.matcher(raw.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String threadId = null;
        String query = matcher.group(3);
        if (query != null) {
            Matcher thread = THREAD_PARAM.matcher(query);
            if (thread.find()) {
                threadId = thread.group(1);
            }
        }
        return Optional.of(new WebhookUrl(matcher.group(1), matcher.group(2), threadId));
    }

    /** POST target. {@code wait=true} makes Discord return the created message so it can be edited later. */
    public URI executeUri() {
        String thread = threadId == null ? "" : "&thread_id=" + threadId;
        return URI.create(BASE + id + "/" + token + "?wait=true" + thread);
    }

    /** PATCH target for a message this webhook sent earlier. */
    public URI messageUri(String messageId, @Nullable String messageThreadId) {
        requireSnowflake(messageId);
        String thread = "";
        if (messageThreadId != null) {
            requireSnowflake(messageThreadId);
            thread = "?thread_id=" + messageThreadId;
        }
        return URI.create(BASE + id + "/" + token + "/messages/" + messageId + thread);
    }

    public String masked() {
        return BASE + id + "/********";
    }

    @Override
    public String toString() {
        return masked();
    }

    private static void requireSnowflake(String value) {
        if (!SNOWFLAKE.matcher(value).matches()) {
            throw new IllegalArgumentException("Not a Discord id: " + value);
        }
    }
}
