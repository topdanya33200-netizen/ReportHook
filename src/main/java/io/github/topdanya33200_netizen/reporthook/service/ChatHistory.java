package io.github.topdanya33200_netizen.reporthook.service;

import io.github.topdanya33200_netizen.reporthook.model.ChatLine;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers the last few chat messages of every online player, so a report can carry
 * the chat that led to it. Thread-safe: chat events arrive asynchronously.
 */
public final class ChatHistory {

    private final Map<UUID, Deque<ChatLine>> lines = new ConcurrentHashMap<>();
    private final Clock clock;
    private volatile int capacity = 10;
    private volatile Duration maxAge = Duration.ofMinutes(10);

    public ChatHistory() {
        this(Clock.systemUTC());
    }

    public ChatHistory(Clock clock) {
        this.clock = clock;
    }

    public void configure(int capacity, Duration maxAge) {
        this.capacity = Math.max(0, capacity);
        this.maxAge = maxAge;
    }

    public void record(UUID player, String message) {
        int limit = capacity;
        if (limit == 0) {
            return;
        }
        Deque<ChatLine> history = lines.computeIfAbsent(player, ignored -> new ArrayDeque<>());
        synchronized (history) {
            history.addLast(new ChatLine(clock.instant(), message));
            while (history.size() > limit) {
                history.removeFirst();
            }
        }
    }

    /** Messages younger than the configured max age, oldest first. */
    public List<ChatLine> snapshot(UUID player) {
        Deque<ChatLine> history = lines.get(player);
        if (history == null) {
            return List.of();
        }
        Instant oldest = clock.instant().minus(maxAge);
        List<ChatLine> recent = new ArrayList<>();
        synchronized (history) {
            for (ChatLine line : history) {
                if (!line.time().isBefore(oldest)) {
                    recent.add(line);
                }
            }
        }
        return List.copyOf(recent);
    }

    public void forget(UUID player) {
        lines.remove(player);
    }
}
