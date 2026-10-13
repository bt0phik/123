package dev.farsight.lod;

import dev.farsight.lod.render.LodWorldRenderer;
import dev.farsight.lod.render.SurfaceSampler;
import dev.farsight.lod.render.SurfaceTile;
import dev.farsight.lod.render.SurfaceTileStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FarSightClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("FarSight LOD");
    private int scanCursor;
    private long clientTicks;
    private Object activeLevel;
    private SurfaceTileStore tileStore;

    @Override
    public void onInitializeClient() {
        FarSightConfig.get();
        LodWorldRenderer.register();

        // Incrementally sample already-loaded chunks only. Never force-load terrain.
        ClientTickEvents.END_CLIENT_TICK.register(client -> sampleOneLoadedChunk(client));
        LOGGER.info("FarSight LOD renderer hook registered.");
    }

    private static String worldKey(Minecraft client) {
        String serverIdentity = "singleplayer";
        if (client.getCurrentServer() != null && client.getCurrentServer().ip != null) {
            serverIdentity = client.getCurrentServer().ip;
        }
        String identity = serverIdentity + "|" + client.level.dimension().toString();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
        }
    }

    private void sampleOneLoadedChunk(Minecraft client) {
        clientTicks++;
        if (client.level == null || client.player == null) {
            if (activeLevel != null) {
                LodWorldRenderer.cache().clear();
                activeLevel = null;
                tileStore = null;
            }
            return;
        }

        if (activeLevel != client.level) {
            LodWorldRenderer.cache().clear();
            activeLevel = client.level;
            String worldKey = worldKey(client);
            Path storePath = FabricLoader.getInstance().getGameDir().resolve("farsight-lod").resolve(worldKey);
            tileStore = new SurfaceTileStore(storePath);
            scanCursor = 0;
            int loadCenterX = client.player.chunkPosition().x();
            int loadCenterZ = client.player.chunkPosition().z();
            try {
                int loaded = tileStore.loadNearest(LodWorldRenderer.cache(), loadCenterX, loadCenterZ);
                LOGGER.info("Loaded {} persisted FarSight LOD tiles", loaded);
            } catch (Exception exception) {
                LOGGER.warn("Could not load FarSight LOD tile cache", exception);
            }
        }

        if (!FarSightConfig.get().enabled) return;

        int centerX = client.player.chunkPosition().x();
        int centerZ = client.player.chunkPosition().z();
        int vanillaRadius = Math.max(2, client.options.getEffectiveRenderDistance());
        int radius = Math.min(vanillaRadius + 4, 32);
        int width = radius * 2 + 1;
        int candidates = width * width;

        // Periodically trim CPU-side data. Disk cache remains untouched.
        if (clientTicks % 200 == 0) {
            LodWorldRenderer.cache().trimToNearest(centerX, centerZ, 8192);
        }

        // Scan one loaded chunk per tick. Existing tiles are occasionally sampled
        // again so player-built changes and terrain edits eventually reach the LOD.
        int refreshInterval = 10;
        for (int tries = 0; tries < 12; tries++) {
            int index = Math.floorMod(scanCursor++, candidates);
            int dx = index % width - radius;
            int dz = index / width - radius;
            int chunkX = centerX + dx;
            int chunkZ = centerZ + dz;
            if (!client.level.hasChunk(chunkX, chunkZ)) continue;

            SurfaceTile previous = LodWorldRenderer.cache().get(chunkX, chunkZ);
            if (previous != null && clientTicks % refreshInterval != 0) continue;
            try {
                SurfaceTile sampled = SurfaceSampler.sample(client.level, chunkX, chunkZ);
                if (sampled.sameData(previous)) return;
                LodWorldRenderer.cache().put(sampled);
                if (tileStore != null) {
                    try {
                        tileStore.save(sampled);
                    } catch (Exception exception) {
                        LOGGER.warn("Could not persist LOD tile {}, {}", chunkX, chunkZ, exception);
                    }
                }
                return;
            } catch (RuntimeException exception) {
                LOGGER.debug("Skipped LOD sample for chunk {}, {}", chunkX, chunkZ, exception);
            }
        }
    }
}
