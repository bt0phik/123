package dev.farsight.lod.render;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small disk cache for sampled terrain. Each tile is stored independently so a
 * crash cannot corrupt the entire world cache. This stores only sampled surface
 * data; it never loads or generates Minecraft chunks.
 */
public final class SurfaceTileStore {
    private static final int MAGIC = 0x46534C44; // FSLD
    private static final int VERSION = 1;
    private static final Pattern FILE_NAME = Pattern.compile("(-?\\d+)_(-?\\d+)\\.tile");
    private static final int MAX_LOAD = 2048;

    private final Path directory;

    public SurfaceTileStore(Path directory) {
        this.directory = directory;
    }

    public void save(SurfaceTile tile) throws IOException {
        Files.createDirectories(directory);
        String name = tile.chunkX() + "_" + tile.chunkZ() + ".tile";
        Path target = directory.resolve(name);
        Path temporary = directory.resolve(name + ".tmp");
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(temporary))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(tile.chunkX());
            out.writeInt(tile.chunkZ());
            out.writeInt(SurfaceTile.GRID);
            for (int z = 0; z < SurfaceTile.GRID; z++) {
                for (int x = 0; x < SurfaceTile.GRID; x++) {
                    out.writeInt(tile.height(x, z));
                    out.writeInt(tile.color(x, z));
                }
            }
        }
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Load only the closest persisted tiles, bounding startup memory and work. */
    public int loadNearest(SurfaceTileCache cache, int centerX, int centerZ) throws IOException {
        if (!Files.isDirectory(directory)) return 0;
        List<Path> candidates = new ArrayList<>();
        try (var paths = Files.list(directory)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> FILE_NAME.matcher(path.getFileName().toString()).matches())
                    .forEach(candidates::add);
        }
        candidates.sort(Comparator.comparingLong(path -> distanceSquared(path, centerX, centerZ)));
        int loaded = 0;
        for (Path path : candidates) {
            if (loaded >= MAX_LOAD) break;
            try {
                SurfaceTile tile = read(path);
                if (tile != null && cache.get(tile.chunkX(), tile.chunkZ()) == null) {
                    cache.put(tile);
                    loaded++;
                }
            } catch (IOException | RuntimeException ignored) {
                // A single damaged or obsolete tile must not prevent other tiles loading.
            }
        }
        return loaded;
    }

    private static long distanceSquared(Path path, int centerX, int centerZ) {
        Matcher matcher = FILE_NAME.matcher(path.getFileName().toString());
        if (!matcher.matches()) return Long.MAX_VALUE;
        try {
            long dx = Long.parseLong(matcher.group(1)) - centerX;
            long dz = Long.parseLong(matcher.group(2)) - centerZ;
            return dx * dx + dz * dz;
        } catch (NumberFormatException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static SurfaceTile read(Path path) throws IOException {
        try (DataInputStream in = new DataInputStream(Files.newInputStream(path))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION) return null;
            int chunkX = in.readInt();
            int chunkZ = in.readInt();
            if (in.readInt() != SurfaceTile.GRID) return null;
            SurfaceTile tile = new SurfaceTile(chunkX, chunkZ);
            for (int z = 0; z < SurfaceTile.GRID; z++) {
                for (int x = 0; x < SurfaceTile.GRID; x++) {
                    int height = in.readInt();
                    int color = in.readInt();
                    tile.set(x, z, height, color);
                }
            }
            // Reject files with unexpected trailing bytes instead of accepting a
            // partially overwritten or incompatible record as valid terrain data.
            if (in.read() != -1) return null;
            return tile;
        }
    }
}
