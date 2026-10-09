package net.senbler.sentils.client.lab.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the list of items out of a sack message. The chat line only says "[Sacks] +351 items";
 * which items is in the text shown when the line is hovered over, one to a line, such as
 * "+64 Enchanted Cobblestone (Mining Sack)" or "-2 Wheat (Agronomy Sack)".
 */
public final class SackText {

    private SackText() {}

    public static final String PREFIX = "[Sacks]";

    // The sack's name in brackets at the end is dropped; other mods may add a price after it.
    private static final Pattern LINE = Pattern.compile("^([+-])([\\d,]+) (.+?)(?: \\([^()]*Sack[^()]*\\).*)?$");

    // The chat line ends with how long a stretch it reports on: "(Last 23s.)" or "(Last 1m 5s.)".
    private static final Pattern COVERS = Pattern.compile("\\(Last (?:(\\d+)m ?)?(?:(\\d+)s)?\\.?\\)");
    private static final long USUAL_COVER_MILLIS = 30_000L;

    /**
     * How long a stretch of time the sack message reports on, going by its last few words. If it
     * does not say, the usual half a minute is assumed.
     */
    public static long coversMillis(String line) {
        Matcher m = COVERS.matcher(line);
        if (!m.find() || (m.group(1) == null && m.group(2) == null)) return USUAL_COVER_MILLIS;
        long minutes = m.group(1) == null ? 0 : Long.parseLong(m.group(1));
        long seconds = m.group(2) == null ? 0 : Long.parseLong(m.group(2));
        return (minutes * 60 + seconds) * 1000L;
    }

    /** The items named in the hover texts, as amounts added (positive) or removed (negative). */
    public static List<ItemChange> parse(List<String> hoverTexts) {
        List<ItemChange> changes = new ArrayList<>();
        for (String hover : hoverTexts) {
            for (String line : hover.split("\n")) {
                Matcher m = LINE.matcher(line.strip());
                if (!m.matches()) continue; // a heading, a note, or something another mod put there
                long amount;
                try {
                    amount = Long.parseLong(m.group(2).replace(",", ""));
                } catch (NumberFormatException e) {
                    continue;
                }
                String name = m.group(3).strip();
                if (amount > 0 && !name.isEmpty()) {
                    changes.add(new ItemChange(name, null, m.group(1).equals("-") ? -amount : amount));
                }
            }
        }
        return changes;
    }
}
