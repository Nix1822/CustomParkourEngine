package dev.parkour.util;

public final class TimeFormat {
    private TimeFormat() {}

    /** Minutes:Seconds.Milliseconds, e.g. 01:07.042 */
    public static String format(long ms) {
        long safe = Math.max(0, ms);
        return String.format("%02d:%02d.%03d", safe / 60000, (safe / 1000) % 60, safe % 1000);
    }
}
