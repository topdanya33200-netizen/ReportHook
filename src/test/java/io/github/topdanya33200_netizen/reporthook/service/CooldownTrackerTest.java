package io.github.topdanya33200_netizen.reporthook.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class CooldownTrackerTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final UUID player = UUID.randomUUID();

    private CooldownTracker tracker() {
        CooldownTracker tracker = new CooldownTracker(now::get);
        tracker.cooldown(Duration.ofSeconds(60));
        return tracker;
    }

    @Test
    void blocksUntilTheCooldownIsOver() {
        CooldownTracker tracker = tracker();
        assertEquals(0, tracker.tryAcquire(player));
        now.addAndGet(20_000);
        assertEquals(40_000, tracker.tryAcquire(player));
        assertEquals(40_000, tracker.remaining(player));
        now.addAndGet(40_000);
        assertEquals(0, tracker.remaining(player));
        assertEquals(0, tracker.tryAcquire(player));
    }

    @Test
    void releaseGivesTheCooldownBack() {
        CooldownTracker tracker = tracker();
        tracker.tryAcquire(player);
        tracker.release(player);
        assertEquals(0, tracker.tryAcquire(player));
    }

    @Test
    void purgeKeepsRunningCooldowns() {
        CooldownTracker tracker = tracker();
        tracker.tryAcquire(player);
        now.addAndGet(10_000);
        tracker.purgeExpired();
        assertEquals(50_000, tracker.remaining(player));
    }
}
