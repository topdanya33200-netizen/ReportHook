package io.github.topdanya33200_netizen.reporthook.discord;

import org.jspecify.annotations.Nullable;

/**
 * A message created by the webhook. {@code channelId} is the thread id for forum posts.
 */
public record SentMessage(String messageId, @Nullable String channelId) {
}
