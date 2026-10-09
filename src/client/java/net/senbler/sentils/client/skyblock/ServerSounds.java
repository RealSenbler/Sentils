package net.senbler.sentils.client.skyblock;

import java.util.ArrayList;
import java.util.List;

import net.senbler.sentils.client.Log;

/**
 * Tells whoever asks about each sound the server has the client play. Hypixel marks some things
 * with a sound and nothing else, a fish being caught for one. Read from the packet by
 * ClientPacketListenerMixin, so that a mod that mutes a sound does not hide it from this one.
 */
public final class ServerSounds {

    private ServerSounds() {}

    /** Given the sound's name, such as "entity.experience_orb.pickup", and how loud it is played. */
    public interface Listener {
        void played(String name, float volume);
    }

    private static final List<Listener> listeners = new ArrayList<>();

    /** Asks to be told of every sound from now on. Call from your ClientModInitializer. */
    public static void register(Listener listener) {
        listeners.add(listener);
    }

    /** Called by ClientPacketListenerMixin with a sound the server has sent. */
    public static void played(String name, float volume) {
        for (Listener listener : listeners) {
            try {
                listener.played(name, volume);
            } catch (RuntimeException e) {
                // Whatever goes wrong in a listener, the sound must still play.
                Log.warn("A sound listener failed: " + e);
            }
        }
    }
}
