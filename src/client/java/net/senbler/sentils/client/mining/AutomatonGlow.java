package net.senbler.sentils.client.mining;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.senbler.sentils.client.skyblock.LineOfSight;
import net.senbler.sentils.client.skyblock.SkyblockLocation;

/**
 * Gives the Automatons of the Crystal Hollows, which are iron golems, the glowing outline.
 * <p>
 * Minecraft draws that outline through walls, so an Automaton only gets it while the player has
 * a clear line to some part of it. The outline is put on by MinecraftMixin, for this client only.
 */
public final class AutomatonGlow {

    private AutomatonGlow() {}

    private static final String ISLAND = "crystal hollows";
    private static final int CHECK_TICKS = 2;
    private static final double INSET = 0.1; // how far inside the edges of its box an Automaton is looked for

    private static Set<Integer> glowing = Set.of(); // ids of the Automatons in sight
    private static int ticksUntilCheck;

    /** Call once from your ClientModInitializer. */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(AutomatonGlow::tick);
    }

    /** Whether the entity is an Automaton that should be drawn with the glowing outline. */
    public static boolean isGlowing(Entity entity) {
        return !glowing.isEmpty() && glowing.contains(entity.getId());
    }

    private static void tick(Minecraft mc) {
        if (--ticksUntilCheck > 0) return;
        ticksUntilCheck = CHECK_TICKS;

        String island = SkyblockLocation.getIsland();
        if (!MiningSettings.automatonGlow || mc.level == null || mc.player == null
                || island == null || !island.toLowerCase(Locale.ROOT).contains(ISLAND)) {
            glowing = Set.of();
            return;
        }

        Set<Integer> seen = new HashSet<>();
        Vec3 eye = mc.player.getEyePosition();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof IronGolem && isInSight(mc, eye, entity)) seen.add(entity.getId());
        }
        glowing = seen;
    }

    /** Whether there is a clear line from the eye to the middle, top or bottom of the entity. */
    private static boolean isInSight(Minecraft mc, Vec3 eye, Entity entity) {
        AABB box = entity.getBoundingBox();
        Vec3 middle = box.getCenter();
        return LineOfSight.isClear(mc.level, eye, middle)
                || LineOfSight.isClear(mc.level, eye, new Vec3(middle.x, box.maxY - INSET, middle.z))
                || LineOfSight.isClear(mc.level, eye, new Vec3(middle.x, box.minY + INSET, middle.z));
    }
}
