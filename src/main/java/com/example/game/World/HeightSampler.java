package com.example.game.World;

/**
 * Samples terrain height at world (x,z).
 * Implemented by TerrainSystem (and can be mocked in tests).
 */
public interface HeightSampler {

  /**
   * @param x world X (same units as your tile size)
   * @param z world Z
   * @param defaultHeight value to return when no data is available (e.g., chunk missing)
   * @return ground height (Y) at (x,z), or defaultHeight if unknown
   */
  float heightAt(float x, float z, float defaultHeight);

  /** Convenience overload: returns Float.NEGATIVE_INFINITY when unknown. */
  default float heightAt(float x, float z) {
    return heightAt(x, z, Float.NEGATIVE_INFINITY);
  }

  /** Optional metadata; TerrainSystem should return real values. */
  default int getSamplesPerSide() { return -1; }
  default float getTileSize()     { return 1f; }
  default float getYScale()       { return 1f; }
}
