package io.github.topdanya33200_netizen.reporthook.service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Per-player cooldown between reports.
 */
public final class CooldownTracker {

    private final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();
    private final LongSupplier clock;
    private volatile long cooldownMillis;

    public CooldownTracker() {
        this(System::currentTimeMillis);
    }

    public CooldownTracker(LongSupplier clock) {
        this.clock = clock;
    }

    public void cooldown(Duration cooldown) {
        this.cooldownMillis = Math.max(0, cooldown.toMillis());
    }

    /** Milliseconds left before the player may report again, {@code 0} if they may report now. */
    public long remaining(UUID player) {
        Long previous = lastUse.get(player);
        if (previous == null) {
            return 0;
        }
        long elapsed = clock.getAsLong() - previous;
        long window = cooldownMillis;
        return elapsed >= window ? 0 : window - elapsed;
    }

    /**
     * Starts the cooldown if it is not running.
     *
     * @return {@code 0} when the player may report now, otherwise the milliseconds left
     */
    public long tryAcquire(UUID player) {
        long now = clock.getAsLong();
        long window = cooldownMillis;
        long[] remaining = {0};
        lastUse.compute(player, (ignored, previous) -> {
            if (previous != null && now - previous < window) {
                remaining[0] = window - (now - previous);
                return previous;
            }
            return now;
        });
        return remaining[0];
    }

    /** Gives the cooldown back, e.g. when the report could not be saved. */
    public void release(UUID player) {
        lastUse.remove(player);
    }

    /** Drops expired entries so the map does not grow forever. */
    public void purgeExpired() {
        long now = clock.getAsLong();
        long window = cooldownMillis;
        lastUse.values().removeIf(time -> now - time >= window);
    }
}
