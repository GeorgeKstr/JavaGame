package com.example.game.World;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;

/**
 * Screen-to-terrain picking using a simple ray march against the current terrain
 * height plus a plane fallback. Designed to be called from TerrainSystem as:
 *
 *   boolean hit = picking.raycastToGround(cam, sx, sy, out);
 *
 * Notes:
 * - Marches along the ray until the world-space ray.y crosses the sampled height.
 * - Uses TerrainWorld.sampleHeightAtWorld(...) so it only "hits" loaded chunks;
 *   outside loaded area it falls back to intersecting the y=0 plane and then
 *   snaps y to terrain height at that XZ (which may still be default height).
 */
final class TerrainPicking {
  private final TerrainWorld w;

  // Reusable scratch
  private final Vector3 tmpNear = new Vector3();
  private final Vector3 tmpFar  = new Vector3();
  private final Vector3 dir     = new Vector3();
  private final Vector3 p       = new Vector3();

  TerrainPicking(TerrainWorld w) {
    this.w = w;
  }

  /**
   * Raycast from screen (sx, sy) to the terrain. Writes the hit world position to 'out'.
   * @return true if a surface was hit (either actual terrain crossing, or plane fallback).
   */
  protected Vector3 rayIntersectTerrain(Ray ray, HeightSampler s, float step, int refineIters) {
    float lastT = 0f;
    float lastDiff = Float.NaN; // ray.y - height
    Vector3 p = new Vector3();

    for (float t = step; t <= 4000f; t += step) {
      p.set(ray.origin).mulAdd(ray.direction, t);
      float h = s.heightAt(p.x, p.z, Float.NaN);
      if (Float.isNaN(h)) { lastDiff = Float.NaN; lastT = t; continue; }

      float diff = p.y - h;
      if (!Float.isNaN(lastDiff) && Math.signum(lastDiff) != Math.signum(diff)) {
        // Bracketed: binary search a few steps
        float a = lastT, b = t;
        for (int i = 0; i < refineIters; i++) {
          float m = 0.5f * (a + b);
          Vector3 pm = new Vector3(ray.origin).mulAdd(ray.direction, m);
          float hm = s.heightAt(pm.x, pm.z, Float.NaN);
          float dm = pm.y - hm;
          if (dm > 0f) a = m; else b = m;
        }
        float hitT = 0.5f * (a + b);
        Vector3 hit = new Vector3(ray.origin).mulAdd(ray.direction, hitT);
        hit.y = s.heightAt(hit.x, hit.z, hit.y);
        return hit;
      }
      lastDiff = diff;
      lastT = t;
    }
    return null; // no intersection in range
  }
}
