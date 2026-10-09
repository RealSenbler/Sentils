package net.senbler.sentils.client.mining;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import net.senbler.sentils.client.skyblock.ActionBar;
import net.senbler.sentils.client.skyblock.SkyblockLocation;
import net.senbler.sentils.client.waypoints.WaypointRenderer;

/**
 * Works out which chest the Metal Detector is pointing at in the Mines of Divan, from the
 * "TREASURE: 12.3m" distance Hypixel shows above the hotbar while the detector is held.
 * <p>
 * The mines are built the same in every lobby and a chest can only be in one of a fixed list of
 * places, so once the middle of the mines is known (from the name tag of any Keeper) one distance
 * is usually enough to tell which place it is.
 * <p>
 * The distance on screen is for where the server had the player when it sent it, which while
 * walking is some ticks behind where the player is by the time it arrives. So each reading is
 * kept with the player's last few positions, and a chest only counts as matching if one delay
 * makes every recent reading agree with it. That is what lets this work without standing still.
 */
public final class MetalDetectorSolver {

    private MetalDetectorSolver() {}

    private static final String ISLAND = "crystal hollows";
    private static final Pattern FORMATTING_CODES = Pattern.compile("§.");
    private static final Pattern TREASURE = Pattern.compile("TREASURE: (\\d+(?:\\.\\d+)?)m");
    private static final Pattern KEEPER = Pattern.compile("Keeper of (Diamond|Lapis|Emerald|Gold)");
    private static final String FOUND_PREFIX = "You found ";

    // The Keeper offsets and chest positions are the ones the NotEnoughUpdates and Skyblocker
    // metal detector solvers use.

    /** From the block a Keeper's name tag is in to the middle of the mines. */
    private static final Map<String, int[]> KEEPER_TO_CENTER = Map.of(
            "Diamond", new int[] {33, 0, 3},
            "Lapis", new int[] {-33, 0, -3},
            "Emerald", new int[] {-3, 0, 33},
            "Gold", new int[] {3, 0, -33});

    /** Every block a chest can be in, from the middle of the mines. */
    private static final int[][] CHEST_OFFSETS = {
            {-38, -22, 26}, {38, -22, -26}, {-40, -22, 18}, {-41, -20, 22}, {-5, -21, 16}, {40, -22, -30},
            {-42, -20, -28}, {-43, -22, -40}, {42, -19, -41}, {43, -21, -16}, {-1, -22, -20}, {6, -21, 28},
            {7, -21, 11}, {7, -21, 22}, {-12, -21, -44}, {12, -22, 31}, {12, -22, -22}, {12, -21, 7},
            {12, -21, -43}, {-14, -21, 43}, {-14, -21, 22}, {-17, -21, 20}, {-20, -22, 0}, {1, -21, 20},
            {19, -22, 29}, {20, -22, 0}, {20, -21, -26}, {-23, -22, 40}, {22, -21, -14}, {-24, -22, 12},
            {23, -22, 26}, {23, -22, -39}, {24, -22, 27}, {25, -22, 17}, {29, -21, -44}, {-31, -21, -12},
            {-31, -21, -40}, {30, -21, -25}, {-32, -21, -40}, {-36, -20, 42}, {-37, -21, -14}, {-37, -21, -22},
    };

    private static final int CHECK_TICKS = 20; // how often to look for a Keeper
    private static final int MAX_LAG_TICKS = 14; // the longest delay between moving and the distance showing it
    private static final int LAG_JITTER_TICKS = 1; // how much that delay may differ from one reading to the next
    private static final int TRAIL_TICKS = MAX_LAG_TICKS + LAG_JITTER_TICKS + 1;
    private static final int MAX_READINGS = 8;
    private static final double TOLERANCE = 0.25; // the distance is rounded to a tenth of a block
    // A delay is remembered once the readings pin it down to a span of at most this many ticks.
    private static final int LEARNED_LAG_SPAN = 2 * LAG_JITTER_TICKS + 1;
    // This many readings in a row have to disagree with the chest marked before the waypoint moves.
    private static final int STRIKES_TO_CHANGE = 2;
    // Just after a chest is opened the detector can still be reporting it for a moment.
    private static final long FOUND_GRACE_MILLIS = 1000;
    private static final double FOUND_GRACE_DISTANCE = 5.0;
    private static final long HOLDING_MILLIS = 3000; // a reading this recent means the detector is in hand

    private static final float RED = 0.12f;
    private static final float GREEN = 0.85f;
    private static final float BLUE = 0.95f;

    /** A distance from the detector, with where the player was on each of the ticks leading up to it. */
    private record Reading(double distance, Vec3[] trail) {}

    /** A chest every reading agrees with, and the delays, in ticks, for which they do. */
    private record Match(int chest, int firstLag, int lastLag) {}

    private static final ArrayDeque<Vec3> trail = new ArrayDeque<>(); // the player's position each tick, newest first
    private static final ArrayDeque<Reading> readings = new ArrayDeque<>();

    private static ClientLevel level;
    private static BlockPos center; // the middle of the mines, or null until a Keeper has been seen
    private static int solution = -1; // index into CHEST_OFFSETS of the chest marked, or -1
    private static int strikes;
    private static int learnedLag = -1; // the delay the readings have been arriving with, or -1
    private static long lastReadingMillis;
    private static long foundMillis;
    private static boolean hintShown;
    private static int ticksUntilCheck;

    /** Call once from your ClientModInitializer. */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(MetalDetectorSolver::tick);
        ClientReceiveMessageEvents.GAME.register(MetalDetectorSolver::onMessage);
        ActionBar.register(MetalDetectorSolver::onActionBar);
    }

    /** Call after a mining setting changes, so the waypoint shown follows it. */
    public static void settingsChanged() {
        if (!MiningSettings.metalDetectorSolver) {
            readings.clear();
            setSolution(-1);
        } else {
            showSolution();
        }
    }

    private static void tick(Minecraft mc) {
        if (mc.level != level) {
            // A different lobby has its mines somewhere else.
            level = mc.level;
            center = null;
            learnedLag = -1;
            hintShown = false;
            trail.clear();
            readings.clear();
            setSolution(-1);
        }
        if (mc.player == null || !MiningSettings.metalDetectorSolver) return;

        trail.addFirst(mc.player.position());
        while (trail.size() > TRAIL_TICKS) trail.removeLast();

        if (center != null || --ticksUntilCheck > 0) return;
        ticksUntilCheck = CHECK_TICKS;
        String island = SkyblockLocation.getIsland();
        if (island != null && island.toLowerCase(Locale.ROOT).contains(ISLAND)) findCenter(mc);
    }

    /** Works out the middle of the mines from any Keeper whose name tag is in view range. */
    private static void findCenter(Minecraft mc) {
        for (Entity entity : mc.level.entitiesForRendering()) {
            Component name = entity.getCustomName();
            if (name == null) continue;
            Matcher m = KEEPER.matcher(FORMATTING_CODES.matcher(name.getString()).replaceAll(""));
            if (!m.find()) continue;
            int[] offset = KEEPER_TO_CENTER.get(m.group(1));
            center = entity.blockPosition().offset(offset[0], offset[1], offset[2]);
            return;
        }
    }

    private static void onMessage(Component message, boolean overlay) {
        // The distance above the hotbar comes by way of onActionBar, however the server sent it.
        if (overlay || !MiningSettings.metalDetectorSolver) return;
        // "You found ... with your Metal Detector!": the next distance is for a new chest.
        String plain = FORMATTING_CODES.matcher(message.getString()).replaceAll("").stripLeading();
        if (plain.startsWith(FOUND_PREFIX) && System.currentTimeMillis() - lastReadingMillis < HOLDING_MILLIS) {
            foundMillis = System.currentTimeMillis();
            readings.clear();
            setSolution(-1);
        }
    }

    private static void onActionBar(Component message) {
        if (!MiningSettings.metalDetectorSolver) return;
        String text = message.getString();
        if (!text.contains("TREASURE")) return;
        Matcher m = TREASURE.matcher(FORMATTING_CODES.matcher(text).replaceAll(""));
        if (m.find()) onDistance(Double.parseDouble(m.group(1)));
    }

    private static void onDistance(double distance) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        long now = System.currentTimeMillis();
        lastReadingMillis = now;

        if (center == null) {
            if (!hintShown) {
                hintShown = true;
                mc.player.sendSystemMessage(
                        Component.literal("Sentils: ").withStyle(ChatFormatting.DARK_AQUA)
                                .append(Component.literal("Go near any Keeper so the metal detector solver can find"
                                        + " the middle of the mines").withStyle(ChatFormatting.WHITE)));
            }
            return;
        }
        if (now - foundMillis < FOUND_GRACE_MILLIS && distance < FOUND_GRACE_DISTANCE) return;

        readings.addLast(new Reading(distance, currentTrail(mc.player.position())));
        while (readings.size() > MAX_READINGS) readings.removeFirst();

        // If nothing matches, the oldest readings may be for a chest that has since been opened.
        List<Match> matches = solve();
        while (matches.isEmpty() && readings.size() > 1) {
            readings.removeFirst();
            matches = solve();
        }

        for (Match match : matches) {
            if (match.chest() == solution) {
                strikes = 0;
                return;
            }
        }
        // One odd reading does not move a waypoint that is already up.
        if (solution >= 0 && ++strikes < STRIKES_TO_CHANGE) return;
        setSolution(matches.size() == 1 ? matches.getFirst().chest() : -1);
    }

    /** Where the player is now, followed by where they were on each tick before, oldest last. */
    private static Vec3[] currentTrail(Vec3 current) {
        Vec3[] positions = new Vec3[TRAIL_TICKS + 1];
        positions[0] = current;
        int i = 1;
        for (Vec3 position : trail) {
            if (i == positions.length) break;
            positions[i++] = position;
        }
        // Too few ticks have gone by to fill it: the player is taken to have been standing still before.
        for (; i < positions.length; i++) positions[i] = positions[i - 1];
        return positions;
    }

    /** The chests that every reading held agrees with. */
    private static List<Match> solve() {
        // The delay hardly changes from one reading to the next, so the one seen before is tried
        // first: with it, a single reading taken on the move is usually enough.
        if (learnedLag >= 0) {
            List<Match> matches = matching(learnedLag, learnedLag);
            if (matches.size() == 1) return matches;
        }
        List<Match> matches = matching(0, MAX_LAG_TICKS);
        if (matches.size() == 1) {
            Match match = matches.getFirst();
            // Standing still, any delay fits; only readings taken on the move say what it is.
            if (match.lastLag() - match.firstLag() < LEARNED_LAG_SPAN) {
                learnedLag = (match.firstLag() + match.lastLag()) / 2;
            }
        }
        return matches;
    }

    /** The chests for which some one delay from lagFrom to lagTo ticks makes every reading agree. */
    private static List<Match> matching(int lagFrom, int lagTo) {
        List<Match> matches = new ArrayList<>();
        for (int chest = 0; chest < CHEST_OFFSETS.length; chest++) {
            // The distance is measured to the bottom corner of the block above the chest.
            int[] offset = CHEST_OFFSETS[chest];
            Vec3 target = new Vec3(center.getX() + offset[0], center.getY() + offset[1] + 1, center.getZ() + offset[2]);

            int firstLag = -1;
            int lastLag = -1;
            for (int lag = lagFrom; lag <= lagTo; lag++) {
                if (!agrees(target, lag)) continue;
                if (firstLag < 0) firstLag = lag;
                lastLag = lag;
            }
            if (firstLag >= 0) matches.add(new Match(chest, firstLag, lastLag));
        }
        return matches;
    }

    /** Whether every reading is the distance to the target from where the player was about lag ticks before it. */
    private static boolean agrees(Vec3 target, int lag) {
        for (Reading reading : readings) {
            double nearest = Double.MAX_VALUE;
            double farthest = 0;
            for (int ticks = Math.max(0, lag - LAG_JITTER_TICKS); ticks <= lag + LAG_JITTER_TICKS; ticks++) {
                double distance = reading.trail()[ticks].distanceTo(target);
                nearest = Math.min(nearest, distance);
                farthest = Math.max(farthest, distance);
            }
            if (reading.distance() < nearest - TOLERANCE || reading.distance() > farthest + TOLERANCE) return false;
        }
        return true;
    }

    private static void setSolution(int chest) {
        solution = chest;
        strikes = 0;
        showSolution();
    }

    private static void showSolution() {
        if (solution < 0 || center == null) {
            WaypointRenderer.clearTarget();
            return;
        }
        int[] offset = CHEST_OFFSETS[solution];
        WaypointRenderer.showTarget(center.getX() + offset[0], center.getY() + offset[1], center.getZ() + offset[2],
                RED, GREEN, BLUE, MiningSettings.metalDetectorTracer);
    }
}
