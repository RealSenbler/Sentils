package net.senbler.sentils.client.lab.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A second way of seeing skill XP, for when the line above the hotbar gives none: the Skills
 * widget of the tab list, which the player can switch on with /tablist. Its lines look like
 * " Mining 14: 22,922/75k", and the XP gained is how far the first number has moved.
 * <p>
 * It only works for a line in that form. A skill shown as a percentage, " Farming 35: 12.4%",
 * or as " Farming 60: MAX", gives no number to follow. The widget is also a few seconds behind,
 * so gains read from it are reported as inexact.
 */
public final class TabSkillTracker {

    // Other widgets have lines of the same shape, such as collections, so only skills are taken.
    private static final Set<String> SKILLS = Set.of("farming", "mining", "combat", "foraging", "fishing",
            "enchanting", "alchemy", "carpentry", "runecrafting", "social", "taming", "hunting");

    private static final Pattern LINE =
            Pattern.compile("^\\s*([A-Za-z]+)(?: (\\d+))?: ([\\d,]+(?:\\.\\d+)?)/([\\d,.]+[kKmMbB]?)\\s*$");

    private record Seen(String level, String needed, double progress) {}

    private final Map<String, Seen> lastSeen = new HashMap<>(); // by skill in lower case

    /** Forgets what was last seen, so the next reading only sets a starting point. */
    public void reset() {
        this.lastSeen.clear();
    }

    /** Whether the line is a skill's, in the form with a number that can be followed. */
    public static boolean isSkillLine(String line) {
        Matcher m = LINE.matcher(line);
        return m.matches() && SKILLS.contains(m.group(1).toLowerCase(Locale.ROOT));
    }

    /**
     * Takes the tab list's lines, with formatting codes already removed. Returns the XP gained
     * in each skill since the last reading. A skill seen for the first time, or one that has
     * gone up a level since, only sets a starting point.
     */
    public List<XpTracker.Gain> accept(List<String> lines) {
        List<XpTracker.Gain> gains = new ArrayList<>();
        for (String line : lines) {
            Matcher m = LINE.matcher(line);
            if (!m.matches() || !SKILLS.contains(m.group(1).toLowerCase(Locale.ROOT))) continue;
            double progress;
            try {
                progress = Double.parseDouble(m.group(3).replace(",", ""));
            } catch (NumberFormatException e) {
                continue;
            }
            String level = m.group(2) == null ? "" : m.group(2);
            Seen now = new Seen(level, m.group(4), progress);
            Seen last = this.lastSeen.put(m.group(1).toLowerCase(Locale.ROOT), now);
            if (last == null || !last.level().equals(level) || !last.needed().equals(now.needed())) continue;
            if (progress > last.progress()) gains.add(new XpTracker.Gain(m.group(1), progress - last.progress(), false));
        }
        return gains;
    }
}
