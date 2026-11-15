package io.github.topdanya33200_netizen.reporthook.util;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Short human readable durations such as {@code 2h 15m}.
 */
public final class TimeFormat {

    /** Unit suffixes, translatable through the language file. */
    public record Units(String days, String hours, String minutes, String seconds) {
        public static final Units DEFAULT = new Units("d", "h", "m", "s");
    }

    private TimeFormat() {
    }

    /** At most the two largest units; partial seconds round up so a cooldown never shows 0s. */
    public static String duration(Duration duration, Units units) {
        long millis = Math.max(0, duration.toMillis());
        long total = (millis + 999) / 1000;
        long[] values = {total / 86_400, total % 86_400 / 3_600, total % 3_600 / 60, total % 60};
        String[] suffixes = {units.days(), units.hours(), units.minutes(), units.seconds()};
        List<String> parts = new ArrayList<>(2);
        for (int i = 0; i < values.length; i++) {
            if (values[i] == 0) {
                if (!parts.isEmpty()) {
                    break; // "2h 0m 5s" reads better as "2h"
                }
                continue;
            }
            parts.add(values[i] + suffixes[i]);
            if (parts.size() == 2) {
                break;
            }
        }
        return parts.isEmpty() ? "0" + units.seconds() : String.join(" ", parts);
    }

    public static String since(Instant then, Instant now, Units units) {
        return duration(Duration.between(then, now), units);
    }
}
