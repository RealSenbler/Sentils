package net.senbler.sentils.client.lab.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.senbler.sentils.client.lab.model.Counters;

/**
 * Follows slayer quests from chat and from the scoreboard. In chat, each on a line of its own:
 * <pre>
 * SLAYER QUEST STARTED!
 * SLAYER MINIBOSS! The Revenant Champion spawned!
 * SLAYER BOSS! The Revenant Horror IV spawned!
 * SLAYER QUEST COMPLETE!
 *  Zombie Slayer LVL 8 - Next LVL in 191,850 XP!
 *  RNG Meter - 1,276,210 Stored XP
 * SLAYER QUEST FAILED!
 * Took 1.9k coins from your bank for auto-slayer...
 * </pre>
 * and on the scoreboard, where the third line changes as the quest goes on:
 * <pre>
 * Slayer Quest
 * Revenant Horror V
 * (1,200/2,400) Combat XP      then      Slay the boss!      then      Boss slain!
 * </pre>
 * The scoreboard is what times the boss: how long it took to bring out, and how long to kill.
 */
public final class SlayerTracker {

    private static final Pattern NOT_TEXT = Pattern.compile("[^\\p{L}\\p{N}\\p{P}\\p{Zs}]");
    private static final Pattern BANK = Pattern.compile("^Took ([\\d,.]+)([kKmMbB]?) coins from your bank for auto-slayer");
    // "Zombie Slayer LVL 8 - Next LVL in 3,500 XP!", which follows a completed quest below the top level
    private static final Pattern LEVEL = Pattern.compile("^(\\w+) Slayer LVL (\\d+) - Next LVL in ([\\d,]+) XP");
    private static final long XP_LINE_MILLIS = 2000;
    private static final Pattern BOSS_SPAWNED = Pattern.compile("^SLAYER BOSS! The (.+) spawned!");
    private static final Pattern RNG_METER = Pattern.compile("^RNG Meter - ([\\d,]+) Stored XP");
    private static final String STARTED = "SLAYER QUEST STARTED!";
    private static final String COMPLETE = "SLAYER QUEST COMPLETE!";
    private static final String FAILED = "SLAYER QUEST FAILED!";

    private long questMillis; // when the quest in hand was started, or 0 if that was not seen
    private long bossMillis; // when its boss appeared, or 0 while it has not or once it is dead
    private String boss; // the quest on the scoreboard, such as "Revenant Horror V", or null
    // The boss is dead, but the scoreboard may go on saying to slay it for a moment yet.
    private boolean bossDone;
    private double pendingXp; // XP for a quest just completed, not yet counted
    private long pendingMillis;
    private String level; // the slayer and level last seen in chat, such as "Zombie 8", or null
    private double remaining; // and the XP that was then left to the next level
    private double rngMeter = -1; // the XP stored in the RNG meter when last seen, or -1 if it has not been

    /** Forgets the quest in hand, as when recording stops: its times would no longer be right. */
    public void reset() {
        this.rngMeter = -1;
        this.pendingXp = 0;
        this.level = null;
        this.bossDone = false;
        this.questMillis = 0;
        this.bossMillis = 0;
        this.boss = null;
    }

    /** Takes a line of chat from the server, without formatting codes. */
    public List<Count> chat(String text, long millis) {
        String line = text.trim();
        List<Count> counts = new ArrayList<>();
        Matcher m;
        if (line.equals(STARTED)) {
            counts.add(Count.one(Counters.SLAYER_STARTED));
            this.questMillis = millis;
            this.bossMillis = 0;
        } else if (line.equals(COMPLETE)) {
            flushXp(counts);
            counts.add(Count.one(Counters.SLAYER_COMPLETED));
            if (this.boss != null) counts.add(Count.one(Counters.SLAYER_BOSS + this.boss));
            // Held back for a moment: the line that follows may give the XP exactly.
            this.pendingXp = tierXp(this.boss);
            this.pendingMillis = millis;
            // Chat can be ahead of the scoreboard in saying the boss is dead.
            bossKilled(millis, counts);
            this.questMillis = 0;
        } else if (line.equals(FAILED)) {
            counts.add(Count.one(Counters.SLAYER_FAILED));
            this.questMillis = 0;
            this.bossMillis = 0;
        } else if ((m = LEVEL.matcher(line)).find()) {
            levelLine(m.group(1) + " " + m.group(2), m.group(3), counts);
        } else if (line.startsWith("SLAYER MINIBOSS! ")) {
            counts.add(Count.one(Counters.SLAYER_MINIBOSSES));
        } else if ((m = BOSS_SPAWNED.matcher(line)).find()) {
            this.boss = m.group(1);
            this.bossDone = false;
            bossSpawned(millis, counts);
        } else if ((m = RNG_METER.matcher(line)).find()) {
            // The meter only goes up, until a drop from it sets it back.
            double stored = Double.parseDouble(m.group(1).replace(",", ""));
            if (this.rngMeter >= 0 && stored > this.rngMeter) {
                counts.add(new Count(Counters.SLAYER_RNG_METER_XP, stored - this.rngMeter));
            }
            this.rngMeter = stored;
        } else if ((m = BANK.matcher(line)).find()) {
            try {
                counts.add(new Count(Counters.SLAYER_BANK_COINS,
                        Double.parseDouble(m.group(1).replace(",", "")) * scale(m.group(2))));
            } catch (NumberFormatException ignored) {
            }
        }
        return counts;
    }

    /**
     * Takes the line that says how much XP is left to the next slayer level. Two of them for the
     * same level, one after the other, give what the quest between them was worth exactly, with
     * any bonus in it. The first of a session, or one after a level up, has nothing to be taken from.
     */
    private void levelLine(String level, String left, List<Count> counts) {
        double remaining;
        try {
            remaining = Double.parseDouble(left.replace(",", ""));
        } catch (NumberFormatException e) {
            return;
        }
        double gained = level.equals(this.level) ? this.remaining - remaining : 0;
        this.level = level;
        this.remaining = remaining;
        if (this.pendingXp == 0 && gained <= 0) return;
        if (gained > 0) this.pendingXp = gained;
        flushXp(counts);
    }

    private void flushXp(List<Count> counts) {
        if (this.pendingXp > 0) counts.add(new Count(Counters.SLAYER_XP, this.pendingXp));
        this.pendingXp = 0;
    }

    /**
     * What a boss of this tier is worth before any bonus: 5, 25, 100, 500 and 1,500 for tiers I
     * to V, and less for the Rift's vampire. The tier is the numeral the quest's name ends in.
     */
    static double tierXp(String boss) {
        if (boss == null) return 0;
        int tier = switch (boss.substring(boss.lastIndexOf(' ') + 1)) {
            case "I" -> 1;
            case "II" -> 2;
            case "III" -> 3;
            case "IV" -> 4;
            case "V" -> 5;
            default -> 0;
        };
        if (tier == 0) return 0;
        boolean vampire = boss.startsWith("Riftstalker");
        return (vampire ? new double[] {10, 25, 60, 120, 150} : new double[] {5, 25, 100, 500, 1500})[tier - 1];
    }

    private static double scale(String suffix) {
        return switch (suffix.toLowerCase()) {
            case "k" -> 1e3;
            case "m" -> 1e6;
            case "b" -> 1e9;
            default -> 1;
        };
    }

    /** Takes the scoreboard's lines, top to bottom, without formatting codes. */
    public List<Count> scoreboard(List<String> lines, long millis) {
        List<Count> counts = new ArrayList<>();
        // No line came to give the XP exactly, as at the top level: the tier's usual XP is counted.
        if (this.pendingXp > 0 && millis - this.pendingMillis > XP_LINE_MILLIS) flushXp(counts);
        List<String> clean = new ArrayList<>();
        // Hypixel pads each line with a symbol of its own to tell the lines apart.
        for (String line : lines) clean.add(NOT_TEXT.matcher(line).replaceAll("").trim());
        int quest = clean.indexOf("Slayer Quest");
        if (quest < 0 || quest + 2 >= clean.size()) return counts;
        this.boss = clean.get(quest + 1).isEmpty() ? null : clean.get(quest + 1);
        String state = clean.get(quest + 2);

        if (state.startsWith("Slay the boss")) {
            if (!this.bossDone) bossSpawned(millis, counts);
        } else if (state.startsWith("Boss slain")) {
            bossKilled(millis, counts);
        } else {
            this.bossDone = false;
        }
        return counts;
    }

    /** Chat says so the moment it happens, and the scoreboard a little later; whichever is first counts. */
    private void bossSpawned(long millis, List<Count> counts) {
        if (this.bossMillis != 0) return;
        this.bossMillis = millis;
        if (this.questMillis == 0) return;
        counts.add(new Count(Counters.SLAYER_SPAWN_MILLIS, millis - this.questMillis));
        counts.add(Count.one(Counters.SLAYER_SPAWNS_TIMED));
        this.questMillis = 0;
    }

    private void bossKilled(long millis, List<Count> counts) {
        this.bossDone = true;
        if (this.bossMillis == 0) return;
        counts.add(new Count(Counters.SLAYER_KILL_MILLIS, millis - this.bossMillis));
        counts.add(Count.one(Counters.SLAYER_KILLS_TIMED));
        this.bossMillis = 0;
    }
}
