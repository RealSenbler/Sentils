package net.senbler.sentils.client.dungeons;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import net.senbler.sentils.client.skyblock.SkyblockLocation;
import net.senbler.sentils.client.waypoints.BlockHighlightRenderer;

/**
 * Colors the cracked stone bricks around the player while in a dungeon. Those are what the walls
 * a Superboom TNT blows open are made of, though some rooms also have them as plain decoration.
 */
public final class CrackedBrickHighlight {

    private CrackedBrickHighlight() {}

    private static final String ISLAND = "catacombs"; // the tab list says "Dungeon: Catacombs" inside one
    private static final int CHECK_TICKS = 10;
    private static final int CHUNK_RADIUS = 4; // how many chunks out from the player's are looked through
    private static final int SECTION_SIZE = 16;

    private static final float RED = 0.15f;
    private static final float GREEN = 0.45f;
    private static final float BLUE = 1f;
    private static final float ALPHA = 0.5f;

    private static boolean shown;
    private static int ticksUntilCheck;

    /** Call once from your ClientModInitializer. */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(CrackedBrickHighlight::tick);
    }

    private static void tick(Minecraft mc) {
        if (--ticksUntilCheck > 0) return;
        ticksUntilCheck = CHECK_TICKS;

        String island = SkyblockLocation.getIsland();
        if (!DungeonSettings.highlightCrackedBricks || mc.level == null || mc.player == null
                || island == null || !island.toLowerCase(Locale.ROOT).contains(ISLAND)) {
            if (shown) BlockHighlightRenderer.clear();
            shown = false;
            return;
        }

        BlockHighlightRenderer.show(findBricks(mc.level, mc.player.getBlockX() >> 4, mc.player.getBlockZ() >> 4),
                RED, GREEN, BLUE, ALPHA);
        shown = true;
    }

    /** Every cracked stone brick in the chunks around the given one that has a side in the open. */
    private static List<BlockHighlightRenderer.Block> findBricks(ClientLevel level, int chunkX, int chunkZ) {
        List<BlockHighlightRenderer.Block> bricks = new ArrayList<>();
        BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();

        for (int cx = chunkX - CHUNK_RADIUS; cx <= chunkX + CHUNK_RADIUS; cx++) {
            for (int cz = chunkZ - CHUNK_RADIUS; cz <= chunkZ + CHUNK_RADIUS; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) continue;

                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection section = sections[i];
                    // Most sections have none, which they can tell without being gone through block by block.
                    if (section.hasOnlyAir() || !section.maybeHas(CrackedBrickHighlight::isCrackedBrick)) continue;

                    int baseX = cx * SECTION_SIZE;
                    int baseY = chunk.getMinY() + i * SECTION_SIZE;
                    int baseZ = cz * SECTION_SIZE;
                    for (int y = 0; y < SECTION_SIZE; y++) {
                        for (int z = 0; z < SECTION_SIZE; z++) {
                            for (int x = 0; x < SECTION_SIZE; x++) {
                                if (!isCrackedBrick(section.getBlockState(x, y, z))) continue;
                                int faces = openFaces(level, neighbor, baseX + x, baseY + y, baseZ + z);
                                if (faces != 0) {
                                    bricks.add(new BlockHighlightRenderer.Block(baseX + x, baseY + y, baseZ + z, faces));
                                }
                            }
                        }
                    }
                }
            }
        }
        return bricks;
    }

    private static boolean isCrackedBrick(BlockState state) {
        return state.is(Blocks.CRACKED_STONE_BRICKS);
    }

    /** The sides of the block that do not have a block that hides them up against them. */
    private static int openFaces(ClientLevel level, BlockPos.MutableBlockPos neighbor, int x, int y, int z) {
        int faces = 0;
        for (Direction side : Direction.values()) {
            neighbor.set(x + side.getStepX(), y + side.getStepY(), z + side.getStepZ());
            if (!level.getBlockState(neighbor).canOcclude()) faces |= BlockHighlightRenderer.faceBit(side);
        }
        return faces;
    }
}
