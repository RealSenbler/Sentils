package net.senbler.sentils.client.safari.waypoints;

import java.util.Locale;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;

import net.senbler.sentils.client.skyblock.SkyblockLocation;
import net.senbler.sentils.client.waypoints.WaypointRenderer;

/**
 * Marks the seven bells hidden around the Critter Safari. The waypoints are switched on by hand
 * and last only for that visit: they go when the player leaves the Safari, and nothing is saved.
 */
public final class BellWaypoints {

    private BellWaypoints() {}

    private static final String ISLAND = "safari";
    private static final int CHECK_TICKS = 20;

    // From the Critter Safari page of the Hypixel SkyBlock Wiki. The island is the same every visit.
    private static final int[][] BELLS = {
            {-68, 66, -43},  // Icy biome, under the ice platform by its entrance
            {-96, 46, -57},  // Icy biome, in the cavern under the lake
            {-4, 96, -42},   // Haunted biome, on the mansion's roof
            {47, 55, -7},    // the outer rim between the Forest and Haunted biomes
            {-30, 125, 59},  // Forest biome, on the floating island
            {-90, 109, 16},  // Cavern biome, on top of the tall stack
            {-50, 81, 0},    // on top of the landing zone
    };
    private static final float RED = 1f;
    private static final float GREEN = 0.8f;
    private static final float BLUE = 0.2f;

    private static boolean shown;
    private static int ticksUntilCheck;

    /** Call once from your ClientModInitializer. */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(BellWaypoints::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> hide());
    }

    public static boolean isShown() {
        return shown;
    }

    public static boolean isInSafari() {
        String island = SkyblockLocation.getIsland();
        return island != null && island.toLowerCase(Locale.ROOT).contains(ISLAND);
    }

    /** Shows the waypoints if they are hidden and the player is in the Safari; hides them if they are shown. */
    public static void toggle() {
        if (shown) {
            hide();
        } else if (isInSafari()) {
            WaypointRenderer.showMarkers(BELLS, RED, GREEN, BLUE);
            shown = true;
        }
    }

    private static void hide() {
        if (!shown) return;
        shown = false;
        WaypointRenderer.clearMarkers();
    }

    private static void tick(Minecraft mc) {
        if (!shown || --ticksUntilCheck > 0) return;
        ticksUntilCheck = CHECK_TICKS;
        // The island is unknown for a moment while changing servers, so only a named island that
        // is not the Safari counts as having left.
        if (SkyblockLocation.getIsland() != null && !isInSafari()) hide();
    }
}
