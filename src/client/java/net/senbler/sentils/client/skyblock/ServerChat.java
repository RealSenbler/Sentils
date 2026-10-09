package net.senbler.sentils.client.skyblock;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.network.chat.Component;

import net.senbler.sentils.client.Log;

/**
 * Tells whoever asks about each line the server writes in chat, as the server sent it. Like the
 * action bar, it is read from the packet by ClientPacketListenerMixin: other mods shorten, merge
 * and hide chat lines on their way to the screen, and a feature that counts from them needs the
 * lines as they were. Lines typed by players come another way and are not among these.
 */
public final class ServerChat {

    private ServerChat() {}

    private static final List<Consumer<Component>> listeners = new ArrayList<>();

    /** Asks to be given every server chat line from now on. Call from your ClientModInitializer. */
    public static void register(Consumer<Component> listener) {
        listeners.add(listener);
    }

    /** Called by ClientPacketListenerMixin with a line the server has sent for the chat. */
    public static void shown(Component text) {
        for (Consumer<Component> listener : listeners) {
            try {
                listener.accept(text);
            } catch (RuntimeException e) {
                // Whatever goes wrong in a listener, the line must still get into the chat.
                Log.warn("A chat listener failed: " + e);
            }
        }
    }
}
