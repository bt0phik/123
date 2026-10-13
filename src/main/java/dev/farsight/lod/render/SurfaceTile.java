package dev.farsight.lod.render;

/**
 * Coarse surface-height/color map for one chunk.
 * Grid size 5 means 4x4 quads per chunk, dramatically fewer triangles than
 * the original block surface.
 */
public final class SurfaceTile {
    public static final int GRID = 5;
    private final int chunkX;
    private final int chunkZ;
    private final int[] heights = new int[GRID * GRID];
    private final int[] colors = new int[GRID * GRID];

    public SurfaceTile(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public int chunkX() { return chunkX; }
    public int chunkZ() { return chunkZ; }

    public void set(int gx, int gz, int height, int rgb) {
        int i = index(gx, gz);
        heights[i] = height;
        colors[i] = rgb & 0xFFFFFF;
    }

    public int height(int gx, int gz) { return heights[index(gx, gz)]; }
    public int color(int gx, int gz) { return colors[index(gx, gz)]; }

    /** Returns true when the sampled surface data is identical to another tile. */
    public boolean sameData(SurfaceTile other) {
        if (other == null || chunkX != other.chunkX || chunkZ != other.chunkZ) return false;
        return java.util.Arrays.equals(heights, other.heights)
                && java.util.Arrays.equals(colors, other.colors);
    }

    public LodMesh triangulate() {
        var out = new java.util.ArrayList<LodVertex>((GRID - 1) * (GRID - 1) * 6);
        for (int z = 0; z < GRID - 1; z++) {
            for (int x = 0; x < GRID - 1; x++) {
                int x0 = chunkX * 16 + x * 4;
                int z0 = chunkZ * 16 + z * 4;
                int x1 = x0 + 4;
                int z1 = z0 + 4;
                LodVertex a = vertex(x0, z0, x, z);
                LodVertex b = vertex(x1, z0, x + 1, z);
                LodVertex c = vertex(x1, z1, x + 1, z + 1);
                LodVertex d = vertex(x0, z1, x, z + 1);
                // Two triangles per quad. Diagonal alternates to reduce visible banding.
                if (((x + z + chunkX + chunkZ) & 1) == 0) {
                    out.add(a); out.add(b); out.add(c);
                    out.add(a); out.add(c); out.add(d);
                } else {
                    out.add(a); out.add(b); out.add(d);
                    out.add(b); out.add(c); out.add(d);
                }
            }
        }
        return new LodMesh(out, chunkX, chunkZ);
    }

    private LodVertex vertex(int x, int z, int gx, int gz) {
        return new LodVertex(x, height(gx, gz), z, color(gx, gz));
    }

    private static int index(int x, int z) {
        if (x < 0 || x >= GRID || z < 0 || z >= GRID) throw new IndexOutOfBoundsException();
        return z * GRID + x;
    }
}
