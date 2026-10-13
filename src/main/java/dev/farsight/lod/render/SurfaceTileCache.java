package dev.farsight.lod.render;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.Comparator;

/** Thread-safe cache of sampled surface tiles and their generated meshes. */
public final class SurfaceTileCache {
    private final Map<Long, SurfaceTile> tiles = new ConcurrentHashMap<>();
    private final Map<Long, LodMesh> meshes = new ConcurrentHashMap<>();

    public void put(SurfaceTile tile) {
        long key = key(tile.chunkX(), tile.chunkZ());
        tiles.put(key, tile);
        meshes.put(key, tile.triangulate());
    }

    public SurfaceTile get(int chunkX, int chunkZ) {
        return tiles.get(key(chunkX, chunkZ));
    }

    public LodMesh getMesh(int chunkX, int chunkZ) {
        return meshes.get(key(chunkX, chunkZ));
    }

    public Collection<SurfaceTile> values() {
        return tiles.values();
    }

    public List<LodMesh> meshesSnapshot() {
        return List.copyOf(meshes.values());
    }

    public void clear() {
        tiles.clear();
        meshes.clear();
    }

    public int size() {
        return tiles.size();
    }

    /**
     * Keep the closest tiles in memory. Persisted tiles are not deleted, so they
     * can be loaded again when the player returns to an area.
     */
    public void trimToNearest(int centerChunkX, int centerChunkZ, int maxTiles) {
        if (maxTiles < 0) throw new IllegalArgumentException("maxTiles must be non-negative");
        if (tiles.size() <= maxTiles) return;

        var keys = new ArrayList<>(tiles.keySet());
        keys.sort(Comparator.comparingLong(key -> distanceSquared(key, centerChunkX, centerChunkZ)));
        for (int i = maxTiles; i < keys.size(); i++) {
            long key = keys.get(i);
            tiles.remove(key);
            meshes.remove(key);
        }
    }

    private static long distanceSquared(long key, int centerChunkX, int centerChunkZ) {
        int x = (int) (key >> 32);
        int z = (int) key;
        long dx = (long) x - centerChunkX;
        long dz = (long) z - centerChunkZ;
        return dx * dx + dz * dz;
    }

    public static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
