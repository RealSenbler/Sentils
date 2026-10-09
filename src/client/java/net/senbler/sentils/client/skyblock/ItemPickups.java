package net.senbler.sentils.client.skyblock;

import java.util.ArrayList;
import java.util.List;

import net.senbler.sentils.client.Log;

/**
 * Tells whoever asks each time the player picks an item up off the ground, which the server
 * says with a packet of its own. Read by ClientPacketListenerMixin.
 */
public final class ItemPickups {

    private ItemPickups() {}

    private static final List<Runnable> listeners = new ArrayList<>();

    /** Asks to be told of every pickup from now on. Call from your ClientModInitializer. */
    public static void register(Runnable listener) {
        listeners.add(listener);
    }

    /** Called by ClientPacketListenerMixin when the player has picked an item up. */
    public static void pickedUp() {
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException e) {
                // Whatever goes wrong in a listener, the item must still be picked up.
                Log.warn("An item pickup listener failed: " + e);
            }
        }
    }
}
