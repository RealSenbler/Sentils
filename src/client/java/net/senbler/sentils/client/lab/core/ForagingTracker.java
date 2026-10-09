package net.senbler.sentils.client.lab.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.senbler.sentils.client.lab.model.Counters;

/**
 * Reads the gift a tree gives when it is cut down, which the server writes as a block of lines:
 * <pre>
 *                 TREE GIFT
 *   You helped cut 100% of the Fig Tree.
 *        +5 rewards gained! (hover)
 *                BONUS GIFT
 *        Stretching Sticks (20%)
 *     A Phanflare fell from the Tree!
 * </pre>
 * What the rewards were is in the text shown on hovering over their line, one to a line:
 * <pre>
 * Forest Whispers x40        (or Desert Whispers, by region)
 * HOTF Experience x10
 * Foraging Experience x1,000
 * Tender Wood x0-2
 * </pre>
 * Each is counted as a reward of the gifts, by its name. The items among them reach the sacks
 * or the inventory as well, and are counted there for the session's value like any others.
 */
public final class ForagingTracker {

    private static final Pattern CUT = Pattern.compile("^You helped cut ([\\d.]+)% of the (.+?) Tree\\.");
    private static final Pattern REWARDS = Pattern.compile("^\\+([\\d,]+) rewards? gained!");
    private static final Pattern REWARD = Pattern.compile("^(.+?) (?:x(?:0-)?([\\d,]+)|\\([\\d.]+%\\))$");
    private static final Pattern BONUS = Pattern.compile("^(.+?) \\(([\\d.]+)%\\)$");
    private static final Pattern MOB = Pattern.compile("^An? (.+?) fell from the Tree!");
    // The bonus gift's lines follow its heading at once; anything later is not part of it.
    private static final long BONUS_MILLIS = 2000;

    // "Whispers: 336.1k (+35)", which the scoreboard shows in place of the purse while they are coming in
    private static final Pattern WHISPERS = Pattern.compile("Whispers: ([\\d,]+(?:\\.\\d+)?)([kKmMbB]?)");

    private long bonusMillis; // when the bonus gift's heading came, or 0
    private long whispers = -1; // the whispers held when the scoreboard last showed them, or -1 if it has not

    /** Forgets anything half seen, as when recording stops. */
    public void reset() {
        this.bonusMillis = 0;
        this.whispers = -1;
    }

    /**
     * Takes the scoreboard's lines, without formatting codes. Whispers come with every log as
     * well as with a tree's gift, and the only place all of them show is the total held, on the
     * scoreboard. What it rises by is what was gained. The total is cut short there, to the
     * nearest hundred once it is in the thousands, so the count is that close and no closer;
     * a fall, which is whispers being spent, is not counted against it.
     */
    public List<Count> scoreboard(List<String> lines) {
        for (String line : lines) {
            Matcher m = WHISPERS.matcher(line);
            if (!m.find()) continue;
            long held;
            try {
                held = Math.round(Double.parseDouble(m.group(1).replace(",", "")) * scale(m.group(2)));
            } catch (NumberFormatException e) {
                return List.of();
            }
            long gained = this.whispers >= 0 ? held - this.whispers : 0;
            this.whispers = held;
            return gained > 0 ? List.of(new Count(Counters.FORAGING_WHISPERS, gained)) : List.of();
        }
        return List.of();
    }

    private static double scale(String suffix) {
        return switch (suffix.toLowerCase()) {
            case "k" -> 1e3;
            case "m" -> 1e6;
            case "b" -> 1e9;
            default -> 1;
        };
    }

    /**
     * Takes a line of chat from the server, without formatting codes, and the texts shown on
     * hovering over its parts.
     */
    public List<Count> chat(String text, List<String> hoverTexts, long millis) {
        String line = text.trim();
        List<Count> counts = new ArrayList<>();
        boolean inBonus = this.bonusMillis != 0 && millis - this.bonusMillis <= BONUS_MILLIS;
        Matcher m;
        if (line.equals("BONUS GIFT")) {
            this.bonusMillis = millis;
        } else if ((m = CUT.matcher(line)).find()) {
            counts.add(Count.one(Counters.FORAGING_TREES));
            counts.add(Count.one(Counters.FORAGING_TREE + m.group(2)));
            try {
                counts.add(new Count(Counters.FORAGING_SHARE_PERCENT, Double.parseDouble(m.group(1))));
            } catch (NumberFormatException ignored) {
            }
        } else if ((m = REWARDS.matcher(line)).find()) {
            counts.add(new Count(Counters.FORAGING_REWARDS, number(m.group(1))));
            for (String hover : hoverTexts) {
                for (String reward : hover.split("\n")) reward(reward.trim(), counts);
            }
        } else if ((m = MOB.matcher(line)).find()) {
            counts.add(Count.one(Counters.FORAGING_MOBS));
            counts.add(Count.one(Counters.FORAGING_MOB + m.group(1)));
        } else if (inBonus && (m = BONUS.matcher(line)).find()) {
            counts.add(Count.one(Counters.FORAGING_BONUS_GIFTS));
            counts.add(Count.one(Counters.FORAGING_BONUS + m.group(1)));
        } else if (!line.isEmpty()) {
            this.bonusMillis = 0; // the line that closes the block, or something else altogether
        }
        return counts;
    }

    private static void reward(String line, List<Count> counts) {
        Matcher m = REWARD.matcher(line);
        if (!m.find()) return;
        // A reward given with its chance and no amount is one of it.
        counts.add(new Count(Counters.FORAGING_REWARD + m.group(1), m.group(2) != null ? number(m.group(2)) : 1));
    }

    private static double number(String text) {
        try {
            return Double.parseDouble(text.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
