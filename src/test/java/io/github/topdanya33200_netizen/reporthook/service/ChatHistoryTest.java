package io.github.topdanya33200_netizen.reporthook.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.topdanya33200_netizen.reporthook.model.ChatLine;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatHistoryTest {

    /** A clock the test can move forward. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-15T12:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }
    }

    private final UUID player = UUID.randomUUID();

    @Test
    void keepsOnlyTheNewestMessages() {
        ChatHistory history = new ChatHistory(new MutableClock());
        history.configure(3, Duration.ofMinutes(10));
        for (int i = 1; i <= 5; i++) {
            history.record(player, "message " + i);
        }
        List<ChatLine> lines = history.snapshot(player);
        assertEquals(List.of("message 3", "message 4", "message 5"), lines.stream().map(ChatLine::message).toList());
    }

    @Test
    void dropsMessagesOlderThanMaxAge() {
        MutableClock clock = new MutableClock();
        ChatHistory history = new ChatHistory(clock);
        history.configure(10, Duration.ofMinutes(5));
        history.record(player, "old");
        clock.advance(Duration.ofMinutes(6));
        history.record(player, "new");
        assertEquals(List.of("new"), history.snapshot(player).stream().map(ChatLine::message).toList());
    }

    @Test
    void forgetsPlayersWhoLeft() {
        ChatHistory history = new ChatHistory(new MutableClock());
        history.record(player, "hello");
        history.forget(player);
        assertTrue(history.snapshot(player).isEmpty());
    }
}
