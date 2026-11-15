package io.github.topdanya33200_netizen.reporthook.discord;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/**
 * Sends webhook requests one at a time on a background thread.
 *
 * <p>Requests are queued so bursts of reports never trip Discord's rate limit; a 429 answer is
 * retried after the {@code retry_after} delay Discord asks for, server errors with backoff.</p>
 */
public final class DiscordWebhookClient implements AutoCloseable {

    private static final int MAX_ATTEMPTS = 4;

    private final HttpClient http;
    private final ExecutorService worker;
    private final String userAgent;

    public DiscordWebhookClient(String userAgent) {
        this.userAgent = userAgent;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.worker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ReportHook-Discord");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Creates a message. When {@code threadName} is set the webhook must belong to a forum
     * channel and Discord opens a new thread with that title.
     */
    public CompletableFuture<SentMessage> post(WebhookUrl webhook, JsonObject payload, @Nullable String threadName) {
        JsonObject body = payload.deepCopy();
        if (threadName != null) {
            body.addProperty("thread_name", threadName);
        }
        return submit(() -> {
            HttpResponse<String> response = send(request(webhook.executeUri(), "POST", body));
            JsonObject message = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonElement channel = message.get("channel_id");
            return new SentMessage(message.get("id").getAsString(), channel == null ? null : channel.getAsString());
        });
    }

    /** Replaces the content of a message this webhook created. */
    public CompletableFuture<Void> edit(WebhookUrl webhook, String messageId, @Nullable String threadId, JsonObject payload) {
        return submit(() -> {
            send(request(webhook.messageUri(messageId, threadId), "PATCH", payload));
            return null;
        });
    }

    private <T> CompletableFuture<T> submit(Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            worker.execute(() -> {
                try {
                    future.complete(task.call());
                } catch (Throwable throwable) {
                    future.completeExceptionally(throwable);
                }
            });
        } catch (RejectedExecutionException exception) {
            future.completeExceptionally(exception);
        }
        return future;
    }

    private HttpRequest request(URI uri, String method, JsonObject payload) {
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("User-Agent", userAgent)
                .method(method, HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException, WebhookException {
        for (int attempt = 1; ; attempt++) {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return response;
            }
            boolean retryable = status == 429 || status >= 500;
            if (!retryable || attempt >= MAX_ATTEMPTS) {
                throw new WebhookException(status, response.body());
            }
            long waitMillis = status == 429
                    ? retryAfterMillis(response.body(), response.headers().firstValue("Retry-After").orElse(null))
                    : backoffMillis(attempt);
            Thread.sleep(waitMillis);
        }
    }

    /** Reads Discord's {@code retry_after} (seconds, JSON body first, header second). */
    static long retryAfterMillis(@Nullable String body, @Nullable String header) {
        double seconds = -1;
        if (body != null && !body.isBlank()) {
            try {
                JsonElement parsed = JsonParser.parseString(body);
                if (parsed.isJsonObject() && parsed.getAsJsonObject().has("retry_after")) {
                    seconds = parsed.getAsJsonObject().get("retry_after").getAsDouble();
                }
            } catch (RuntimeException ignored) {
                // Not JSON, fall back to the header
            }
        }
        if (seconds < 0 && header != null) {
            try {
                seconds = Double.parseDouble(header.trim());
            } catch (NumberFormatException ignored) {
                // Fall through to the default
            }
        }
        if (seconds < 0) {
            seconds = 1;
        }
        return Math.clamp(Math.round(seconds * 1000), 250L, 30_000L);
    }

    static long backoffMillis(int attempt) {
        return Math.min(8_000L, 500L << Math.min(attempt - 1, 10));
    }

    /** Lets queued requests finish for a few seconds, then stops the worker. */
    @Override
    public void close() {
        worker.shutdown();
        try {
            if (!worker.awaitTermination(5, TimeUnit.SECONDS)) {
                worker.shutdownNow();
            }
        } catch (InterruptedException exception) {
            worker.shutdownNow();
            Thread.currentThread().interrupt();
        }
        http.close();
    }
}
