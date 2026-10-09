package net.senbler.sentils.client.safari.alerts;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.mojang.blaze3d.platform.Window;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.senbler.sentils.client.safari.SafariSettings;
import net.senbler.sentils.client.skyblock.LineOfSight;
import net.senbler.sentils.client.skyblock.SkyblockLocation;

/**
 * On the Safari island, shows a title the first time an entity whose nametag contains
 * "sparkling" comes into the player's view.
 */
public final class SparklingAlert {

    private SparklingAlert() {}

    private static final String ISLAND = "safari";
    private static final String NAME_MARKER = "sparkling";
    private static final int CHECK_TICKS = 5;
    private static final int TITLE_TICKS = 60;
    private static final double NAMETAG_OFFSET = 0.3; // how far above an entity its nametag floats

    private static final Set<UUID> alerted = new HashSet<>(); // creatures already announced
    private static int ticksUntilCheck;

    /** Call once from your ClientModInitializer. */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(SparklingAlert::tick);
    }

    private static void tick(Minecraft mc) {
        if (--ticksUntilCheck > 0) return;
        ticksUntilCheck = CHECK_TICKS;

        String island = SkyblockLocation.getIsland();
        if (!SafariSettings.sparklingAlert || mc.level == null || mc.player == null
                || island == null || !island.toLowerCase(Locale.ROOT).contains(ISLAND)) {
            alerted.clear();
            return;
        }

        boolean found = false;
        Set<UUID> present = new HashSet<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!nametag(entity).toLowerCase(Locale.ROOT).contains(NAME_MARKER)) continue;
            present.add(entity.getUUID());
            if (!alerted.contains(entity.getUUID()) && isInView(mc, entity)) {
                alerted.add(entity.getUUID());
                found = true;
            }
        }
        // Forget creatures that have unloaded, so one that comes back is announced again.
        alerted.retainAll(present);

        if (found) {
            mc.gui.hud.setTimes(0, TITLE_TICKS, 10);
            mc.gui.hud.setTitle(Component.literal("SPARKLING CREATURE SEEN")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }
    }

    /** The text shown above an entity: its custom name, or the text of a text display. */
    private static String nametag(Entity entity) {
        if (entity instanceof Display.TextDisplay display) {
            return display.getText().getString();
        }
        Component name = entity.getCustomName();
        return name != null ? name.getString() : "";
    }

    /** Whether the entity's nametag or body is on screen with nothing opaque in the way. */
    private static boolean isInView(Minecraft mc, Entity entity) {
        Vec3 eye = mc.player.getEyePosition();
        AABB box = entity.getBoundingBox();
        Vec3 nametag = new Vec3(entity.getX(), box.maxY + NAMETAG_OFFSET, entity.getZ());
        return canSee(mc, eye, nametag) || canSee(mc, eye, box.getCenter());
    }

    private static boolean canSee(Minecraft mc, Vec3 eye, Vec3 target) {
        return isInFieldOfView(mc, target.subtract(eye)) && LineOfSight.isClear(mc.level, eye, target);
    }

    /** Whether a direction from the player's eye falls inside the screen's view. */
    private static boolean isInFieldOfView(Minecraft mc, Vec3 direction) {
        Vec3 forward = mc.player.getViewVector(1f);
        Vec3 up = mc.player.getUpVector(1f);
        Vec3 right = forward.cross(up);

        double depth = direction.dot(forward);
        if (depth <= 0) return false; // behind the player

        Window window = mc.getWindow();
        double aspect = (double) window.getWidth() / Math.max(1, window.getHeight());
        double verticalReach = depth * Math.tan(Math.toRadians(mc.options.fov().get() / 2.0));
        return Math.abs(direction.dot(up)) <= verticalReach
                && Math.abs(direction.dot(right)) <= verticalReach * aspect;
    }
}
