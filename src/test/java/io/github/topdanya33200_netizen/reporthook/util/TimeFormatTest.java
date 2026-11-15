package io.github.topdanya33200_netizen.reporthook.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class TimeFormatTest {

    private static String format(Duration duration) {
        return TimeFormat.duration(duration, TimeFormat.Units.DEFAULT);
    }

    @Test
    void showsTheTwoLargestUnits() {
        assertEquals("45s", format(Duration.ofSeconds(45)));
        assertEquals("2m 5s", format(Duration.ofSeconds(125)));
        assertEquals("1h 30m", format(Duration.ofMinutes(90)));
        assertEquals("3d 4h", format(Duration.ofHours(76).plusMinutes(10)));
    }

    @Test
    void stopsAtAZeroUnit() {
        assertEquals("2h", format(Duration.ofHours(2).plusSeconds(5)));
    }

    @Test
    void roundsPartialSecondsUp() {
        assertEquals("1s", format(Duration.ofMillis(300)));
        assertEquals("0s", format(Duration.ZERO));
    }

    @Test
    void usesTranslatedUnits() {
        assertEquals("1ч 5м", TimeFormat.duration(Duration.ofMinutes(65), new TimeFormat.Units("д", "ч", "м", "с")));
    }

    @Test
    void cleansPlayerText() {
        assertEquals("hello world", Text.clean("  hello\n\tworld  "));
        assertEquals("red text", Text.clean("§cred text"));
    }
}
