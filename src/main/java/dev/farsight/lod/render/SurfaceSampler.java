package dev.farsight.lod.render;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Converts a loaded chunk's surface into a 5x5 low-resolution tile.
 * Call only on the client thread and only for chunks already loaded by vanilla.
 */
public final class SurfaceSampler {
    private SurfaceSampler() {}

    public static SurfaceTile sample(LevelReader level, int chunkX, int chunkZ) {
        SurfaceTile tile = new SurfaceTile(chunkX, chunkZ);
        int baseX = chunkX * 16;
        int baseZ = chunkZ * 16;
        for (int gz = 0; gz < SurfaceTile.GRID; gz++) {
            for (int gx = 0; gx < SurfaceTile.GRID; gx++) {
                // The mesh grid spans 0..16 in each chunk. Sample the shared
                // boundary at coordinate 16 when that neighboring chunk is
                // already loaded; otherwise duplicate this chunk's edge sample.
                // This avoids forcing chunk loads while allowing adjacent tiles to
                // converge on identical edge heights/colors once both are loaded.
                int x = baseX + gx * 4;
                int z = baseZ + gz * 4;
                if (!level.hasChunk(x >> 4, z >> 4)) {
                    x = baseX + Math.min(15, gx * 4);
                    z = baseZ + Math.min(15, gz * 4);
                }
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                BlockPos pos = new BlockPos(x, Math.max(level.getMinY(), y - 1), z);
                BlockState state = level.getBlockState(pos);
                int rgb = state.getMapColor(level, pos).col;
                if (rgb == 0) rgb = 0x7FA35B;
                tile.set(gx, gz, y, rgb);
            }
        }
        return tile;
    }
}
