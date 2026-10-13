package dev.farsight.lod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;

/** User settings for FarSight LOD. */
public final class FarSightConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("farsight-lod.json");
    private static FarSightConfig instance;

    /** Requested distant chunk radius. UI clamps this to 32..2048. */
    public int chunkDistance = 512;
    public boolean enabled = true;

    public static FarSightConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static FarSightConfig load() {
        if (Files.exists(FILE)) {
            try {
                FarSightConfig loaded = GSON.fromJson(Files.readString(FILE), FarSightConfig.class);
                if (loaded != null) {
                    loaded.chunkDistance = clamp(loaded.chunkDistance);
                    return loaded;
                }
            } catch (Exception ignored) {
                // A malformed config is replaced with defaults.
            }
        }
        FarSightConfig config = new FarSightConfig();
        config.save();
        return config;
    }

    public void setChunkDistance(int distance) {
        chunkDistance = clamp(distance);
        save();
    }

    private static int clamp(int value) {
        return Math.max(32, Math.min(2048, value));
    }

    public void save() {
        Path temporary = FILE.resolveSibling(FILE.getFileName() + ".tmp");
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(temporary, GSON.toJson(this));
            try {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            FarSightClient.LOGGER.error("Could not save FarSight LOD settings", e);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // A stale temporary file is harmless and will be overwritten next time.
            }
        }
    }
}
