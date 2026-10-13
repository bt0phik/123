package dev.farsight.lod.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Produces a bounded list of chunk coordinates for LOD generation.
 *
 * It deliberately does not load chunks: callers should only sample data that
 * is already available or comes from an explicit, safe background data source.
 * Distance is measured in chunks, not blocks.
 */
public final class LodWorkPlanner {
    private LodWorkPlanner() {}

    public record ChunkTarget(int x, int z, int distanceSquared) { }

    /**
     * Return missing LOD tiles inside the configured circular chunk radius.
     * maxTargets limits work per tick/frame to avoid spikes.
     */
    public static List<ChunkTarget> plan(
            int centerChunkX,
            int centerChunkZ,
            int radiusChunks,
            int maxTargets,
            SurfaceTileCache cache
    ) {
        int radius = Math.max(0, Math.min(radiusChunks, 2048));
        int budget = Math.max(0, Math.min(maxTargets, 4096));
        if (budget == 0) return List.of();

        long radiusSquared = (long) radius * radius;
        List<ChunkTarget> targets = new ArrayList<>();
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                long d2 = (long) dx * dx + (long) dz * dz;
                if (d2 > radiusSquared) continue;
                int x = centerChunkX + dx;
                int z = centerChunkZ + dz;
                if (cache.get(x, z) == null) {
                    targets.add(new ChunkTarget(x, z, (int) Math.min(Integer.MAX_VALUE, d2)));
                }
            }
        }

        targets.sort(Comparator.comparingInt(ChunkTarget::distanceSquared));
        if (targets.size() > budget) {
            return List.copyOf(targets.subList(0, budget));
        }
        return List.copyOf(targets);
    }
}
