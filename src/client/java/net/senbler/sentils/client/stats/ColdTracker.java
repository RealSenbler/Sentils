package net.senbler.sentils.client.stats;

import java.util.ArrayDeque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * Reads the player's cold from the sidebar, where Hypixel shows a line such as "Cold: -45❄"
 * in cold areas, and works out from how fast it is rising when it will reach the maximum.
 */
public final class ColdTracker {

    private ColdTracker() {}

    public static final int MAX_COLD = 100;

    private static final Pattern FORMATTING_CODES = Pattern.compile("§.");
    private static final Pattern COLD_LINE = Pattern.compile("Cold: -?(\\d{1,3})");
    private static final int CHECK_TICKS = 2;
    private static final int HISTORY = 6; // rises the speed is averaged over
    // With no rise for this many times the usual gap, the cold is taken to have stopped rising.
    private static final double STALL_FACTOR = 3.0;
    private static final long STALL_SLACK_MILLIS = 1000;

    /** A moment the cold was seen to go up, and what it went up to. */
    private record Rise(long millis, int cold) {}

    private static final ArrayDeque<Rise> rises = new ArrayDeque<>();
    private static int cold; // 0 when the player has none
    private static boolean stalled;
    private static int ticksUntilCheck;

    /** Call once from your ClientModInitializer. */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(ColdTracker::tick);
    }

    /** The player's cold from 0 to 100, as a positive number; 0 when they have none. */
    public static int cold() {
        return cold;
    }

    /** Whether the cold was rising and has stopped, for example next to a heat source. */
    public static boolean isStalled() {
        return stalled;
    }

    /** Estimated milliseconds until the cold reaches the maximum, or -1 while there is no estimate. */
    public static long millisUntilMax() {
        if (cold >= MAX_COLD) return 0;
        double perCold = millisPerCold();
        if (perCold < 0) return -1;
        Rise last = rises.peekLast();
        long sinceLast = System.currentTimeMillis() - last.millis();
        return Math.max(0, Math.round((MAX_COLD - last.cold()) * perCold) - sinceLast);
    }

    /** Average time one point of cold takes over the recorded rises, or -1 if too few were seen. */
    private static double millisPerCold() {
        if (rises.size() < 2) return -1;
        Rise first = rises.peekFirst();
        Rise last = rises.peekLast();
        return (last.millis() - first.millis()) / (double) (last.cold() - first.cold());
    }

    private static void tick(Minecraft mc) {
        if (--ticksUntilCheck > 0) return;
        ticksUntilCheck = CHECK_TICKS;

        int previous = cold;
        cold = ColdSettings.coldTimer ? readCold(mc) : 0;
        long now = System.currentTimeMillis();

        if (cold > previous && previous > 0) {
            rises.addLast(new Rise(now, cold));
            if (rises.size() > HISTORY) rises.removeFirst();
            stalled = false;
        } else if (cold < previous) {
            // Warming up, or the cold is gone: the old speed no longer applies.
            rises.clear();
            stalled = false;
        } else if (cold > 0) {
            double perCold = millisPerCold();
            if (perCold >= 0 && now - rises.peekLast().millis() > perCold * STALL_FACTOR + STALL_SLACK_MILLIS) {
                rises.clear();
                stalled = true;
            }
        }
        // The first sighting (previous == 0) isn't recorded: there is no telling how long the
        // cold had already been at that value, so it says nothing about the speed.
    }

    private static int readCold(Minecraft mc) {
        if (mc.level == null) return 0;
        Scoreboard scoreboard = mc.level.getScoreboard();
        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar == null) return 0;

        for (PlayerScoreEntry entry : scoreboard.listPlayerScores(sidebar)) {
            // Hypixel keeps each line's text in the prefix and suffix of the entry's team.
            String line = PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), entry.ownerName())
                    .getString();
            Matcher m = COLD_LINE.matcher(FORMATTING_CODES.matcher(line).replaceAll(""));
            if (m.find()) return Math.min(MAX_COLD, Integer.parseInt(m.group(1)));
        }
        return 0;
    }
}
