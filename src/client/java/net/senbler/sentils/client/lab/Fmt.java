package net.senbler.sentils.client.lab;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import net.senbler.sentils.client.lab.analysis.SessionReport;

/** How the Lab writes numbers, lengths of time and dates. */
public final class Fmt {

    private Fmt() {}

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.ROOT);
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ROOT);
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.ROOT);

    /** A number cut short with k, M or B: 1,234 is "1,234", 12,345 is "12.3k", 1,234,567 is "1.23M". */
    public static String number(double value) {
        double size = Math.abs(value);
        if (size >= 1e9) return String.format(Locale.ROOT, "%.2fB", value / 1e9);
        if (size >= 1e6) return String.format(Locale.ROOT, "%.2fM", value / 1e6);
        if (size >= 1e4) return String.format(Locale.ROOT, "%.1fk", value / 1e3);
        if (size >= 100 || value == Math.rint(value)) return String.format(Locale.ROOT, "%,.0f", value);
        return String.format(Locale.ROOT, "%,.1f", value);
    }

    /** The measure's value, or a dash if it is unavailable. */
    public static String measure(SessionReport.Measure measure) {
        return measure.isAvailable() ? number(measure.value()) : "-";
    }

    /** A number, or a dash for one there is not. */
    public static String number(Double value) {
        return value == null ? "-" : number(value.doubleValue());
    }

    /** A difference with its sign: "+1.2k" or "-340". */
    public static String signed(Double value) {
        if (value == null) return "-";
        return (value > 0 ? "+" : "") + number(value.doubleValue());
    }

    public static String percent(Double value) {
        if (value == null) return "-";
        return (value > 0 ? "+" : "") + String.format(Locale.ROOT, "%.1f%%", value);
    }

    /** A length of time as "1h 05m" or "4m 09s". */
    public static String duration(long millis) {
        long seconds = Math.max(0, millis) / 1000;
        long hours = seconds / 3600;
        long minutes = seconds % 3600 / 60;
        if (hours > 0) return String.format(Locale.ROOT, "%dh %02dm", hours, minutes);
        return String.format(Locale.ROOT, "%dm %02ds", minutes, seconds % 60);
    }

    /** A length of time as a running clock, "1:05:09". */
    public static String clock(long millis) {
        long seconds = Math.max(0, millis) / 1000;
        return String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds % 3600 / 60, seconds % 60);
    }

    public static String date(long millis) {
        return DATE.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
    }

    public static String shortDate(long millis) {
        return SHORT_DATE.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
    }

    public static String fileDate(long millis) {
        return FILE_DATE.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
    }

    /** How long ago a moment was, roughly: "just now", "5 min ago", "3 h ago", "2 days ago". */
    public static String ago(long millis, long now) {
        long minutes = Math.max(0, now - millis) / 60_000;
        if (minutes < 1) return "just now";
        if (minutes < 60) return minutes + " min ago";
        if (minutes < 48 * 60) return minutes / 60 + " h ago";
        return minutes / (24 * 60) + " days ago";
    }
}
