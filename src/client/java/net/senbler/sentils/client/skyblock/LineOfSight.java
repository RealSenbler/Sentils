package net.senbler.sentils.client.skyblock;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class LineOfSight {

    private LineOfSight() {}

    /**
     * Whether nothing opaque lies between the two points. Blocks that don't occlude what is
     * behind them (glass, water, seagrass, leaves, ice and the like) are looked through.
     */
    public static boolean isClear(ClientLevel level, Vec3 from, Vec3 to) {
        return BlockGetter.traverseBlocks(from, to, level, (lvl, pos) -> {
            BlockState state = lvl.getBlockState(pos);
            if (!state.canOcclude()) return null;
            return state.getCollisionShape(lvl, pos).clip(from, to, pos) != null ? Boolean.FALSE : null;
        }, lvl -> Boolean.TRUE);
    }
}
