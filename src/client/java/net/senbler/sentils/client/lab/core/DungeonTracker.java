package net.senbler.sentils.client.lab.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.senbler.sentils.client.lab.model.Counters;

/**
 * Follows dungeon runs from chat. A run that is finished ends with a block of lines, each set in
 * the middle of the chat:
 * <pre>
 *          Master Mode The Catacombs - Floor V
 *               Team Score: 305 (S+)
 *          ☠ Defeated Livid in 05m 12s
 *            +123,456 Catacombs Experience
 *              +41,500 Mage Experience
 * </pre>
 * and a death during one is a line ending "and became a ghost."
 * <pre>
 *  ☠ You were killed by Lost Adventurer and became a ghost.
 *  ☠ Steve fell into a deep hole and became a ghost.
 * </pre>
 */
public final class DungeonTracker {

    private static final Pattern COMPLETE =
            Pattern.compile("^(Master Mode )?The Catacombs - (?:Floor ([IVX]+)|(Entrance))$");
    private static final Pattern SCORE = Pattern.compile("^Team Score: (\\d+) \\(([A-Z][+]?)\\)");
    private static final Pattern DEFEATED =
            Pattern.compile("^\\P{L}*Defeated .+? in (?:(\\d+)h )?(?:(\\d+)m )?(\\d+)s");
    private static final Pattern XP = Pattern.compile("^\\+([\\d,]+(?:\\.\\d+)?) (\\w+) Experience");
    private static final Pattern DEATH = Pattern.compile("^\\P{L}*(\\w+) .*and became a ghost\\.$");

    /** Takes a line of chat from the server, without formatting codes. */
    public List<Count> chat(String text) {
        String line = text.trim();
        List<Count> counts = new ArrayList<>();
        Matcher m;
        if ((m = COMPLETE.matcher(line)).find()) {
            counts.add(Count.one(Counters.DUNGEONS_RUNS));
            String floor = m.group(3) != null ? "Entrance" : (m.group(1) != null ? "M" : "F") + arabic(m.group(2));
            counts.add(Count.one(Counters.DUNGEONS_FLOOR + floor));
        } else if ((m = SCORE.matcher(line)).find()) {
            counts.add(new Count(Counters.DUNGEONS_SCORE, number(m.group(1))));
            counts.add(Count.one(Counters.DUNGEONS_SCORES));
            counts.add(Count.one(Counters.DUNGEONS_GRADE + m.group(2)));
        } else if ((m = DEFEATED.matcher(line)).find()) {
            double seconds = number(m.group(1)) * 3600 + number(m.group(2)) * 60 + number(m.group(3));
            counts.add(new Count(Counters.DUNGEONS_RUN_MILLIS, seconds * 1000));
            counts.add(Count.one(Counters.DUNGEONS_RUNS_TIMED));
        } else if ((m = XP.matcher(line)).find()) {
            boolean catacombs = m.group(2).equals("Catacombs");
            counts.add(new Count(catacombs ? Counters.DUNGEONS_CATACOMBS_XP : Counters.DUNGEONS_CLASS_XP + m.group(2),
                    number(m.group(1))));
        } else if ((m = DEATH.matcher(line)).find()) {
            counts.add(Count.one(m.group(1).equals("You") ? Counters.DUNGEONS_DEATHS : Counters.DUNGEONS_TEAM_DEATHS));
        }
        return counts;
    }

    private static int arabic(String roman) {
        return switch (roman) {
            case "I" -> 1;
            case "II" -> 2;
            case "III" -> 3;
            case "IV" -> 4;
            case "V" -> 5;
            case "VI" -> 6;
            case "VII" -> 7;
            default -> 0;
        };
    }

    private static double number(String text) {
        if (text == null) return 0;
        try {
            return Double.parseDouble(text.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
