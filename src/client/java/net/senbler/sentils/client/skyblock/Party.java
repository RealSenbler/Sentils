package net.senbler.sentils.client.skyblock;

import java.util.Set;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;

/**
 * The Hypixel party the player is in, for any feature that wants to know. It is read from chat
 * alone; see {@link PartyState} for what is followed and what cannot be known.
 */
public final class Party {

    private Party() {}

    private static final PartyState STATE = new PartyState();

    /** Call once from your ClientModInitializer. */
    public static void init() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) STATE.accept(message.getString(), Minecraft.getInstance().getUser().getName());
        });
        // A party outlasts a short disconnect, but not a long one, and which it was cannot be seen.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> STATE.reset());
    }

    public static boolean isInParty() {
        return STATE.isInParty();
    }

    /** The others known to be in the party. */
    public static Set<String> members() {
        return STATE.members();
    }

    /** The party's leader, or null if there is no party or it is not known who leads it. */
    public static String leader() {
        return STATE.leader();
    }
}
