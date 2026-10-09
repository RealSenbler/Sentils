package net.senbler.sentils.client.lab;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Keys for starting, pausing and ending a session, set in the game's own Controls screen and
 * unbound to begin with. They only work the Lab's recorder; nothing they do reaches the server.
 */
public final class LabKeys {

    private LabKeys() {}

    private static KeyMapping start;
    private static KeyMapping pause;
    private static KeyMapping stop;

    /** Call once from your ClientModInitializer. */
    public static void init() {
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("sentils", "lab"));
        int unbound = InputConstants.UNKNOWN.getValue();
        start = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sentils.lab_start", unbound, category));
        pause = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sentils.lab_pause", unbound, category));
        stop = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sentils.lab_stop", unbound, category));
        ClientTickEvents.END_CLIENT_TICK.register(LabKeys::tick);
    }

    private static void tick(Minecraft mc) {
        while (start.consumeClick()) Lab.say(LabCommands.start(LabSettings.lastActivity, ""));
        while (pause.consumeClick()) Lab.say(Lab.recorder().isPaused() ? Lab.resume() : Lab.pause("paused by hand"));
        while (stop.consumeClick()) LabCommands.stop();
    }
}
