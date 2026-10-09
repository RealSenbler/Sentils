package net.senbler.sentils.client.skyblock;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.network.chat.Component;

import net.senbler.sentils.client.Log;

/**
 * Tells whoever asks about each text the game is given to show above the hotbar.
 * <p>
 * A server can send that text in two ways: as a chat message marked for the action bar, or with
 * a packet of its own. Both are read from the packet itself, by ClientPacketListenerMixin, so
 * that the text is the server's own: other mods cut parts out of it before it is drawn, which
 * the chat message events and the screen would only show the remains of.
 */
public final class ActionBar {

    private ActionBar() {}

    private static final List<Consumer<Component>> listeners = new ArrayList<>();

    /** Asks to be given every action bar text from now on. Call from your ClientModInitializer. */
    public static void register(Consumer<Component> listener) {
        listeners.add(listener);
    }

    private static final List<Consumer<Component>> titleListeners = new ArrayList<>();

    /** Asks to be given every title and subtitle shown in the middle of the screen from now on. */
    public static void registerTitles(Consumer<Component> listener) {
        titleListeners.add(listener);
    }

    /** Called by HudMixin with a title or subtitle about to be shown. */
    public static void titleShown(Component text) {
        tell(titleListeners, text);
    }

    /** Called by ClientPacketListenerMixin with a text the server has sent for above the hotbar. */
    public static void shown(Component text) {
        tell(listeners, text);
    }

    private static void tell(List<Consumer<Component>> listeners, Component text) {
        for (Consumer<Component> listener : listeners) {
            try {
                listener.accept(text);
            } catch (RuntimeException e) {
                // Whatever goes wrong in a listener, the text must still get onto the screen.
                Log.warn("A screen text listener failed: " + e);
            }
        }
    }
}
