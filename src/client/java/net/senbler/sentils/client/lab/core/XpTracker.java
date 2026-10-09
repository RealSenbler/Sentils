package net.senbler.sentils.client.lab.core;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Works out skill XP gains from the text Hypixel shows above the hotbar, such as
 * "+12.5 Farming (1,234.5/50,000)". The same text is sent again and again while it is on
 * screen, so adding up the "+12.5" would count it many times over. Instead the progress figure
 * is followed, and the gain is how far it has moved since it was last seen.
 * <p>
 * Below a skill's top level the game shows a percentage instead, "+12.5 Farming (24.69%)". That
 * is followed the same way once the skill's level is known, which the tab list gives: the gain
 * is how far the percentage has moved, as a share of the XP that level takes. It is as close as
 * a hundredth of a percent is, so those gains are reported as inexact. Without the level, all
 * there is to go on is a message differing from the last, which misses most of the gains of
 * anything done many times a second.
 * <p>
 * The message is looked for loosely, by a "+" and a number followed closely by a skill's name,
 * because its exact form varies: there can be an icon between the two, "XP" after the name,
 * and the brackets can hold a progress, a percentage, a total, or be missing.
 */
public final class XpTracker {

    private static final String SKILLS = "Farming|Mining|Combat|Foraging|Fishing|Enchanting|Alchemy|Carpentry"
            + "|Runecrafting|Social|Taming|Hunting";

    // "+" and a number; a little that is neither digit nor bracket, such as a space or an icon;
    // the skill; then either brackets close behind, or the end of that part of the action bar.
    private static final Pattern MESSAGE = Pattern.compile(
            "\\+([\\d,]+(?:\\.\\d+)?)[^\\d(+]{0,12}?\\b(" + SKILLS + ")\\b(?: XP)?"
                    + "(?:[^(\\d]{0,6}\\(([^()]{1,32})\\)|(?=\\s{2,}|$))",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PROGRESS = Pattern.compile("([\\d,]+(?:\\.\\d+)?)\\s*/\\s*([\\d,.]+[kKmMbB]?)");
    private static final Pattern TOTAL = Pattern.compile("[\\d,]+(?:\\.\\d+)?");

    /** XP gained in a skill. exact is false if it is the "+" figure taken on trust. */
    public record Gain(String skill, double amount, boolean exact) {}

    /** What was last shown for a skill: a figure to measure from, or only the text if there was none. */
    private record Seen(Double progress, String needed, String text, Double percent) {}

    private static final Pattern PERCENT = Pattern.compile("([\\d.]+)%");
    // A skill's line in the tab list's Skills widget where it is given as a percentage: " Farming 45: 7.3%"
    private static final Pattern TAB_LEVEL = Pattern.compile("^\\s*([A-Za-z]+) (\\d+): [\\d.]+%\\s*$");

    /**
     * The XP each level takes, from the one before it, for levels 1 to 60. Every skill counted
     * here goes by this but Runecrafting and Social, which have shorter tables of their own.
     */
    private static final long[] LEVEL_XP = {
            50, 125, 200, 300, 500, 750, 1_000, 1_500, 2_000, 3_500,
            5_000, 7_500, 10_000, 15_000, 20_000, 30_000, 50_000, 75_000, 100_000, 200_000,
            300_000, 400_000, 500_000, 600_000, 700_000, 800_000, 900_000, 1_000_000, 1_100_000, 1_200_000,
            1_300_000, 1_400_000, 1_500_000, 1_600_000, 1_700_000, 1_800_000, 1_900_000, 2_000_000, 2_100_000, 2_200_000,
            2_300_000, 2_400_000, 2_500_000, 2_600_000, 2_750_000, 2_900_000, 3_100_000, 3_400_000, 3_700_000, 4_000_000,
            4_300_000, 4_600_000, 4_900_000, 5_200_000, 5_500_000, 5_800_000, 6_100_000, 6_400_000, 6_700_000, 7_000_000,
    };

    /** The XP it takes to reach this level from the one before, for levels 1 to 60. */
    public static long xpForLevel(int level) {
        return LEVEL_XP[level - 1];
    }

    private final Map<String, Seen> lastSeen = new HashMap<>(); // by skill in lower case
    private final Map<String, Integer> levels = new HashMap<>(); // by skill in lower case, as the tab list has them

    /** Takes the tab list's lines, for the level of each skill shown there as a percentage. */
    public void levels(List<String> tabLines) {
        for (String line : tabLines) {
            Matcher m = TAB_LEVEL.matcher(line);
            if (m.matches()) this.levels.put(m.group(1).toLowerCase(Locale.ROOT), Integer.parseInt(m.group(2)));
        }
    }

    /** The XP it takes to get from this level of the skill to the next, or 0 if that is not known. */
    private double levelXp(String skill) {
        Integer level = this.levels.get(skill);
        if (level == null || level < 0 || level >= LEVEL_XP.length) return 0;
        if (skill.equals("runecrafting") || skill.equals("social")) return 0;
        return LEVEL_XP[level];
    }

    /** Whether the text has a skill XP message in it, in any of its forms. */
    public static boolean hasSkillLine(String actionBar) {
        return MESSAGE.matcher(actionBar).find();
    }

    /** Forgets what was last seen, so the next message only sets a starting point. */
    public void reset() {
        this.lastSeen.clear();
    }

    /**
     * Takes the text above the hotbar, with formatting codes already removed. Returns the XP
     * gained since the last message for the same skill, or null if there is no gain to report:
     * no skill message in the text, a repeat, or the first message seen for the skill.
     */
    public Gain accept(String actionBar) {
        Matcher m = MESSAGE.matcher(actionBar);
        if (!m.find()) return null;
        double shown = number(m.group(1));
        String skill = m.group(2).substring(0, 1).toUpperCase(Locale.ROOT) + m.group(2).substring(1).toLowerCase(Locale.ROOT);
        String brackets = m.group(3) == null ? "" : m.group(3).trim();

        // A figure that only ever goes up within a level: the progress towards the next one, or
        // the total so far. The gain is how far it has moved.
        Double progress = null;
        String needed = "";
        Matcher fraction = PROGRESS.matcher(brackets);
        if (fraction.matches()) {
            progress = number(fraction.group(1));
            needed = fraction.group(2);
        } else if (TOTAL.matcher(brackets).matches()) {
            progress = number(brackets);
        }

        Double percent = null;
        Matcher share = PERCENT.matcher(brackets);
        if (progress == null && share.matches()) percent = number(share.group(1));

        String key = skill.toLowerCase(Locale.ROOT);
        Seen last = this.lastSeen.put(key, new Seen(progress, needed, m.group(), percent));
        if (last == null) return null; // nothing to measure from yet
        // A percentage of a level whose length is known: the gain is the share of it covered.
        double levelXp = levelXp(key);
        if (percent != null && last.percent() != null && levelXp > 0) {
            if (percent >= last.percent()) {
                double gained = (percent - last.percent()) / 100 * levelXp;
                return gained > 0 ? new Gain(skill, gained, false) : null;
            }
            // The level went up, so the percentage started again from the bottom.
            return shown > 0 ? new Gain(skill, shown, false) : null;
        }
        if (progress != null && last.progress() != null) {
            if (needed.equals(last.needed()) && progress >= last.progress()) {
                double gained = progress - last.progress();
                return gained > 0 ? new Gain(skill, gained, true) : null;
            }
            // The level went up, so the progress started again from the bottom.
            return shown > 0 ? new Gain(skill, shown, false) : null;
        }
        // A percentage, or nothing: all there is to go on is whether the text has changed.
        if (m.group().equals(last.text())) return null;
        return shown > 0 ? new Gain(skill, shown, false) : null;
    }

    private static double number(String text) {
        try {
            return Double.parseDouble(text.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
