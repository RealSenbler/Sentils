package net.senbler.sentils.client.lab.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.senbler.sentils.client.lab.model.Counters;

/**
 * Follows mining from chat, the tab list and the tool in hand. Lines read:
 * <pre>
 * MITHRIL MINER Commission Complete! Visit the King to claim your rewards!
 * Your Pickobulus destroyed 140 blocks!
 * COMPACT! You found an Enchanted Hard Stone!
 * PRISTINE! You found Flawed Ruby Gemstone x12!     (with an icon before the gemstone's name)
 * CHEST LOCKPICKED
 * CRYSTAL NUCLEUS LOOT BUNDLE
 * </pre>
 * and in the tab list, under "Powders:", " Mithril: 35,448". What each powder rises by from one
 * reading to the next is what was gained; a fall is some being spent on the Heart of the Mountain.
 * <p>
 * A tool with Compact on it keeps a count of the blocks it has mined, which the server puts up as
 * the player mines. Other tools keep none, so there is no count of blocks for them.
 */
public final class MiningTracker {

    private static final Pattern COMMISSION = Pattern.compile("^(.+?) Commission Complete!");
    private static final Pattern PICKOBULUS = Pattern.compile("^Your Pickobulus destroyed ([\\d,]+) blocks?!");
    private static final Pattern COMPACT = Pattern.compile("^COMPACT! You found an? (.+?)!");
    private static final Pattern PRISTINE = Pattern.compile("^PRISTINE! You found \\P{L}*(.+?)(?: x([\\d,]+))?!");
    private static final Pattern CHEST = Pattern.compile("^CHEST LOCKPICKED\\s*$");
    private static final Pattern NUCLEUS = Pattern.compile("^CRYSTAL NUCLEUS LOOT BUNDLE\\s*$");
    private static final Pattern MINESHAFT = Pattern.compile("^[A-Z]+! You found a Glacite Mineshaft portal!");
    // Only where the amount is given in full, as it is in the tab list.
    private static final Pattern POWDER = Pattern.compile(
            "^\\P{L}*(Mithril|Gemstone|Glacite)(?: Powder)?: ([\\d,]+)(?![\\d.kKmMbB])");
    // More than this at once is not mining: a tool swapped for another, or its count set anew.
    private static final long MOST_AT_ONCE = 50_000;

    private final Map<String, Long> powder = new HashMap<>(); // as last shown, by its kind
    private String tool; // what tells the tool last held from any other, or null
    private long mined; // and the count it then had

    /** Forgets every starting point, so that what happened while not recording is not counted. */
    public void reset() {
        this.powder.clear();
        this.tool = null;
    }

    private static double number(String text) {
        try {
            return Double.parseDouble(text.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Takes a line of chat from the server, without formatting codes. */
    public List<Count> chat(String text) {
        String line = text.trim();
        List<Count> counts = new ArrayList<>();
        Matcher m;
        if ((m = COMMISSION.matcher(line)).find()) {
            // Not a line of someone's chat that quotes one.
            if (m.group(1).contains(": ")) return counts;
            counts.add(Count.one(Counters.MINING_COMMISSIONS));
            counts.add(Count.one(Counters.MINING_COMMISSION + title(m.group(1))));
        } else if ((m = PICKOBULUS.matcher(line)).find()) {
            counts.add(Count.one(Counters.MINING_PICKOBULUS));
            counts.add(new Count(Counters.MINING_PICKOBULUS_BLOCKS, number(m.group(1))));
        } else if ((m = COMPACT.matcher(line)).find()) {
            counts.add(Count.one(Counters.MINING_COMPACT));
            counts.add(Count.one(Counters.MINING_COMPACT_ITEM + m.group(1)));
        } else if ((m = PRISTINE.matcher(line)).find()) {
            counts.add(Count.one(Counters.MINING_PRISTINE));
            counts.add(new Count(Counters.MINING_PRISTINE_ITEM + m.group(1), m.group(2) != null ? number(m.group(2)) : 1));
        } else if (CHEST.matcher(line).find()) {
            counts.add(Count.one(Counters.MINING_CHESTS));
        } else if (NUCLEUS.matcher(line).find()) {
            counts.add(Count.one(Counters.MINING_NUCLEUS_RUNS));
        } else if (MINESHAFT.matcher(line).find()) {
            counts.add(Count.one(Counters.MINING_MINESHAFTS));
        }
        return counts;
    }

    /** A commission's name, which the game writes all in capitals, in ordinary case. */
    private static String title(String name) {
        StringBuilder title = new StringBuilder();
        for (String word : name.trim().split(" ")) {
            if (word.isEmpty()) continue;
            if (!title.isEmpty()) title.append(' ');
            title.append(word.charAt(0)).append(word.substring(1).toLowerCase());
        }
        return title.toString();
    }

    /** Takes the tab list's lines, without formatting codes, for the powders held. */
    public List<Count> tab(List<String> lines) {
        List<Count> counts = new ArrayList<>();
        for (String line : lines) {
            Matcher m = POWDER.matcher(line.trim());
            if (!m.find()) continue;
            long held;
            try {
                held = Long.parseLong(m.group(2).replace(",", ""));
            } catch (NumberFormatException e) {
                continue;
            }
            Long before = this.powder.put(m.group(1), held);
            if (before != null && held > before) {
                counts.add(new Count(Counters.MINING_POWDER, held - before));
                counts.add(new Count(Counters.MINING_POWDER_KIND + m.group(1), held - before));
            }
        }
        return counts;
    }

    /**
     * Takes the tool in hand and the count of blocks on it; a null tool is none, or one that
     * keeps no count. The first sight of a tool only sets its starting point.
     */
    public List<Count> tool(String tool, long mined) {
        if (tool == null) {
            this.tool = null;
            return List.of();
        }
        long more = tool.equals(this.tool) ? mined - this.mined : 0;
        this.tool = tool;
        this.mined = mined;
        return more > 0 && more <= MOST_AT_ONCE ? List.of(new Count(Counters.MINING_BLOCKS, more)) : List.of();
    }
}
