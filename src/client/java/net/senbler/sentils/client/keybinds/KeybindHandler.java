package net.senbler.sentils.client.keybinds;

import java.util.HashSet;
import java.util.Set;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

import net.senbler.sentils.client.skyblock.Hypixel;

/**
 * Sends the command or chat message bound to a key when that key is pressed in game.
 * Each physical press sends at most one thing: holding a key does not repeat it, and
 * nothing is sent while a screen (chat, inventory, settings) is open.
 */
public final class KeybindHandler {

    private KeybindHandler() {}

    private static Set<Integer> heldKeys = new HashSet<>();

    /** Call once from your ClientModInitializer. */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(KeybindHandler::tick);
    }

    private static void tick(Minecraft mc) {
        boolean inGame = mc.player != null && mc.gui.screen() == null && Hypixel.featuresAllowed();
        Set<Integer> downNow = new HashSet<>();

        for (KeybindsConfig.Binding binding : KeybindsConfig.bindings) {
            if (binding.key == KeybindsConfig.UNBOUND) continue;
            if (!InputConstants.isKeyDown(mc.getWindow(), binding.key)) continue;

            // add() is false for a second binding on the same key, so only the first one fires.
            boolean firstForKey = downNow.add(binding.key);
            if (inGame && firstForKey && !heldKeys.contains(binding.key)) {
                send(mc, binding.command.trim());
            }
        }
        heldKeys = downNow;
    }

    private static void send(Minecraft mc, String text) {
        if (text.isEmpty() || mc.player == null) return;
        if (text.startsWith("/")) {
            mc.player.connection.sendCommand(text.substring(1));
        } else {
            mc.player.connection.sendChat(text);
        }
    }
}
