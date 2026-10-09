package net.senbler.sentils.client.safari.party;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;

import net.senbler.sentils.client.safari.SafariSettings;
import net.senbler.sentils.client.skyblock.Hypixel;
import net.senbler.sentils.client.skyblock.Party;

/**
 * Tells the party which biome the player's Hunting Hotspot is in, once for each time the game
 * says so on entering the Critter Safari:
 * <pre>
 * HOTSPOT! Your Hunting Hotspot is the Forest Biome!
 * </pre>
 * This sends a party chat message for the player, so nothing
 * is sent unless the player is known to be in a party. It can be switched off.
 */
public final class HotspotAnnouncer {

    private HotspotAnnouncer() {}

    private static final Pattern FORMATTING_CODES = Pattern.compile("§.");
    // Anchored on the prefix so that a player quoting the line in chat does not set it off.
    private static final Pattern HOTSPOT = Pattern.compile("^HOTSPOT! Your Hunting Hotspot is the (.+?) Biome!");

    /** Call once from your ClientModInitializer. */
    public static void init() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) onMessage(message.getString());
        });
    }

    /** The biome named by the game's hotspot line, or null if the line is not one. */
    public static String biome(String message) {
        Matcher m = HOTSPOT.matcher(FORMATTING_CODES.matcher(message).replaceAll("").trim());
        return m.find() ? m.group(1) : null;
    }

    private static void onMessage(String message) {
        if (!SafariSettings.announceHotspot || !Hypixel.isOnHypixel() || !Party.isInParty()) return;
        String biome = biome(message);
        Minecraft mc = Minecraft.getInstance();
        if (biome == null || mc.player == null) return;
        mc.player.connection.sendCommand("pc My biome is " + biome);
    }
}
