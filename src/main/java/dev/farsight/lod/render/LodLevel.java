package dev.farsight.lod.render;

/** Multi-resolution level selector. Higher levels use larger sampling cells. */
public enum LodLevel {
    NEAR(0, 1),
    MEDIUM(1, 2),
    FAR(2, 4),
    HORIZON(3, 8);

    private final int level;
    private final int cellChunks;

    LodLevel(int level, int cellChunks) {
        this.level = level;
        this.cellChunks = cellChunks;
    }

    public int level() { return level; }
    public int cellChunks() { return cellChunks; }

    public static LodLevel forDistance(int distanceChunks) {
        if (distanceChunks < 16) return NEAR;
        if (distanceChunks < 64) return MEDIUM;
        if (distanceChunks < 256) return FAR;
        return HORIZON;
    }
}
