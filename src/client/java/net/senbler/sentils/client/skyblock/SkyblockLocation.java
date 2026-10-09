package net.senbler.sentils.client.skyblock;

import java.util.regex.Pattern;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

/**
 * Tracks which SkyBlock island the player is on by reading the tab list, where Hypixel
 * shows a line such as "Area: Hub" (or "Dungeon: Catacombs" inside a dungeon).
 */
public final class SkyblockLocation {

    private SkyblockLocation() {}

    private static final Pattern FORMATTING_CODES = Pattern.compile("§.");
    private static final String[] TAB_PREFIXES = {"Area: ", "Dungeon: "};
    private static final int REFRESH_TICKS = 20;

    private static String island; // null when not on a SkyBlock island
    private static int ticksUntilRefresh;

    /** Call once from your ClientModInitializer. */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(SkyblockLocation::tick);
    }

    /** The island name as Hypixel shows it, e.g. "Hub", or null when not on SkyBlock. */
    public static String getIsland() {
        return island;
    }

    public static boolean isOnSkyblock() {
        return island != null;
    }

    public static boolean isOn(String islandName) {
        return islandName.equalsIgnoreCase(island);
    }

    private static void tick(Minecraft mc) {
        if (--ticksUntilRefresh > 0) return;
        ticksUntilRefresh = REFRESH_TICKS;
        island = readIsland(mc.getConnection());
    }

    private static String readIsland(ClientPacketListener connection) {
        if (connection == null) return null;
        for (PlayerInfo info : connection.getOnlinePlayers()) {
            Component name = info.getTabListDisplayName();
            if (name == null) continue;
            String line = FORMATTING_CODES.matcher(name.getString()).replaceAll("").trim();
            for (String prefix : TAB_PREFIXES) {
                if (line.startsWith(prefix)) {
                    return line.substring(prefix.length()).trim();
                }
            }
        }
        return null;
    }
}
