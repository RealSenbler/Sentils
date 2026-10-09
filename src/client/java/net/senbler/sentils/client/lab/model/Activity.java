package net.senbler.sentils.client.lab.model;

import java.util.Locale;

/**
 * What a session was spent doing. Every activity is recorded the same way; what differs is which
 * skill's XP counts as the activity's own. Sessions store the name, so an activity added later
 * does not break older records, and one this version does not know is read as OTHER.
 */
public enum Activity {
    FARMING("Farming", "Farming"),
    MINING("Mining", "Mining"),
    DUNGEONS("Dungeons", "Catacombs"),
    HUNTING("Hunting", "Hunting"),
    SLAYER("Slayer", "Combat"),
    FORAGING("Foraging", "Foraging"),
    FISHING("Fishing", "Fishing"),
    OTHER("Other", null);

    public final String label;
    /** The skill whose XP is this activity's, as the game names it, or null if it has none. */
    public final String skill;

    Activity(String label, String skill) {
        this.label = label;
        this.skill = skill;
    }

    /** The activity with this name or label, whatever its case; OTHER if there is none. */
    public static Activity parse(String text) {
        if (text != null) {
            String wanted = text.trim().toUpperCase(Locale.ROOT);
            for (Activity activity : values()) {
                if (activity.name().equals(wanted) || activity.label.toUpperCase(Locale.ROOT).equals(wanted)) {
                    return activity;
                }
            }
        }
        return OTHER;
    }

    /** Whether the text names an activity, as opposed to parse falling back on OTHER. */
    public static boolean isKnown(String text) {
        return parse(text) != OTHER || "OTHER".equalsIgnoreCase(text == null ? "" : text.trim());
    }
}
