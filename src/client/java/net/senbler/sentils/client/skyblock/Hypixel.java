package net.senbler.sentils.client.skyblock;

import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;

/** Whether the server the player is on is Hypixel, which is the one the mod is made for. */
public final class Hypixel {

    private Hypixel() {}

    private static final String NAME = "hypixel";

    /** Going by the address joined, or by what the server calls itself for one joined some other way. */
    public static boolean isOnHypixel() {
        Minecraft mc = Minecraft.getInstance();
        ServerData server = mc.getCurrentServer();
        if (server != null && server.ip != null && server.ip.toLowerCase(Locale.ROOT).contains(NAME)) return true;
        ClientPacketListener connection = mc.getConnection();
        String brand = connection != null ? connection.serverBrand() : null;
        return brand != null && brand.toLowerCase(Locale.ROOT).contains(NAME);
    }

    /**
     * Whether the features that act on chat or send anything may run: on Hypixel, and in a
     * single-player world, where they can be tried out. Not on some other server, whose chat
     * they were not made for.
     */
    public static boolean featuresAllowed() {
        return Minecraft.getInstance().hasSingleplayerServer() || isOnHypixel();
    }
}
