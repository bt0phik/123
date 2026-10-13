package dev.farsight.lod.render;

/** Immutable vertex in world coordinates, with packed RGB terrain tint. */
public record LodVertex(float x, float y, float z, int rgb) { }
