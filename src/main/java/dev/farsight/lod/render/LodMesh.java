package dev.farsight.lod.render;

import java.util.List;

/** CPU-side triangle mesh generated from low-resolution terrain samples. */
public record LodMesh(List<LodVertex> vertices, int chunkX, int chunkZ) {
    public LodMesh {
        vertices = List.copyOf(vertices);
    }

    public boolean isEmpty() {
        return vertices.isEmpty();
    }
}
