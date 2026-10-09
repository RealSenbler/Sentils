package net.senbler.sentils.client.lab.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.senbler.sentils.client.lab.model.Counters;

/**
 * Follows farming from chat and from the tool in hand. Lines read:
 * <pre>
 * You received 7x Enchanted Potato for killing a Locust!
 * RARE CROP! Cropie (+97)
 * VERY RARE CROP! Burrowing Spores
 * RARE DROP! Polished Pumpkin x3 (+73.2)        (from a pest, going by its hover text)
 * OFFER ACCEPTED with Fear Mongerer (UNCOMMON)
 *     +15 Garden Experience
 * </pre>
 * A farming tool keeps a count of the crops it has harvested, which the server puts up as the
 * player farms. What that count rises by while the tool is held is the crops harvested with it.
 */
public final class FarmingTracker {

    private static final Pattern PEST = Pattern.compile("^You received (?:[\\d,]+x )?.+? for killing an? (.+?)!");
    private static final Pattern RARE_CROP = Pattern.compile("^(?:VERY )?RARE CROP! (.+?)(?: \\(.*)?$");
    private static final Pattern VISITOR = Pattern.compile("^OFFER ACCEPTED with (.+?) \\((\\w+)\\)");
    // More than this at once is not farming: a tool swapped for another, or its count set anew.
    private static final long MOST_AT_ONCE = 50_000;

    private static final Pattern RARE_DROP = Pattern.compile("^RARE DROP! ([^(].*?)(?: x([\\d,]+))? \\(\\+");
    private static final Pattern GARDEN_XP = Pattern.compile("^\\+([\\d,]+) Garden Experience");
    // Only where the amount is given in full. While it is coming in it is cut short, "Sowdust: 2.6M (+6.3k)",
    // which is no use to measure from; it is back in full a moment after.
    private static final Pattern HELD = Pattern.compile("^(Copper|Sowdust): ([\\d,]+)(?![\\d.kKmMbB])");
    // "GROSS! A Pest has appeared in Plot - 8!", with an icon before "Pest", or a number of them at once
    private static final Pattern PEST_SPAWN = Pattern.compile(
            "^[A-Z]+! (?:An? |(\\d+) )\\P{L}*Pests? ha(?:s|ve) (?:appeared|spawned)");

    private String tool; // what tells the tool last held from any other, or null
    private long harvested; // and the count it then had
    private long copper = -1; // as the scoreboard last showed it, or -1 if it has not
    private long sowdust = -1;

    /** Forgets every starting point, so that what happened while not recording is not counted. */
    public void reset() {
        this.tool = null;
        this.copper = -1;
        this.sowdust = -1;
    }

    private static double number(String text) {
        try {
            return Double.parseDouble(text.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Takes the scoreboard's lines, without formatting codes. Copper and sowdust are shown there
     * in full when they are not just then coming in, so what each rises by from one such time to
     * the next is what was gained; a fall is some being spent.
     */
    public List<Count> scoreboard(List<String> lines) {
        List<Count> counts = new ArrayList<>();
        for (String line : lines) {
            Matcher m = HELD.matcher(line.trim());
            if (!m.find()) continue;
            boolean copper = m.group(1).equals("Copper");
            long held;
            try {
                held = Long.parseLong(m.group(2).replace(",", ""));
            } catch (NumberFormatException e) {
                continue;
            }
            long before = copper ? this.copper : this.sowdust;
            if (copper) this.copper = held;
            else this.sowdust = held;
            if (before >= 0 && held > before) {
                counts.add(new Count(copper ? Counters.FARMING_COPPER : Counters.FARMING_SOWDUST, held - before));
            }
        }
        return counts;
    }

    /**
     * Takes a line of chat from the server, without formatting codes, and the texts shown on
     * hovering over its parts.
     */
    public List<Count> chat(String text, List<String> hoverTexts) {
        String line = text.trim();
        List<Count> counts = new ArrayList<>();
        Matcher m;
        if ((m = PEST_SPAWN.matcher(line)).find()) {
            counts.add(new Count(Counters.FARMING_PESTS_SPAWNED, m.group(1) != null ? number(m.group(1)) : 1));
        } else if ((m = PEST.matcher(line)).find()) {
            counts.add(Count.one(Counters.FARMING_PESTS));
            counts.add(Count.one(Counters.FARMING_PEST + m.group(1)));
        } else if ((m = RARE_DROP.matcher(line)).find()) {
            // Plenty of things give a rare drop. One from a pest says so in the text shown on hovering.
            if (hoverTexts.stream().noneMatch(hover -> hover.contains("Pest"))) return counts;
            counts.add(Count.one(Counters.FARMING_PEST_DROPS));
            counts.add(new Count(Counters.FARMING_PEST_DROP + m.group(1), m.group(2) != null ? number(m.group(2)) : 1));
        } else if ((m = GARDEN_XP.matcher(line)).find()) {
            counts.add(new Count(Counters.FARMING_GARDEN_XP, number(m.group(1))));
        } else if ((m = RARE_CROP.matcher(line)).find()) {
            counts.add(Count.one(Counters.FARMING_RARE_CROPS));
            counts.add(Count.one(Counters.FARMING_RARE_CROP + m.group(1)));
        } else if ((m = VISITOR.matcher(line)).find()) {
            counts.add(Count.one(Counters.FARMING_VISITORS));
        }
        return counts;
    }

    /**
     * Takes the tool in hand and the count of crops on it; a null tool is none, or one that
     * keeps no count. The first sight of a tool only sets its starting point.
     */
    public List<Count> tool(String tool, long harvested) {
        if (tool == null) {
            this.tool = null;
            return List.of();
        }
        long more = tool.equals(this.tool) ? harvested - this.harvested : 0;
        this.tool = tool;
        this.harvested = harvested;
        return more > 0 && more <= MOST_AT_ONCE ? List.of(new Count(Counters.FARMING_CROPS, more)) : List.of();
    }
}
