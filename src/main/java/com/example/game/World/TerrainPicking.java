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
public class TerrainPicking {
  private final TerrainWorld w;

  // Reusable scratch
  private final Vector3 tmpA    = new Vector3();
  private final Vector3 tmpB    = new Vector3();
  private final Vector3 tmpP    = new Vector3();

  TerrainPicking(TerrainWorld w) {
    this.w = w;
  }

  /**
   * Raycast from screen (sx, sy) to the terrain. Writes the hit world position to 'out'.
   * @return true if a surface was hit (either actual terrain crossing, or plane fallback).
   */
  public Vector3 rayIntersectTerrain(Ray ray, HeightSampler s, float step, int refineIters) {
    float lastT = 0f;
    float lastDiff = Float.NaN; // ray.y - height

    for (float t = step; t <= 4000f; t += step) {
      tmpP.set(ray.origin).mulAdd(ray.direction, t);
      float h = s.heightAt(tmpP.x, tmpP.z, Float.NaN);
      if (Float.isNaN(h)) { lastDiff = Float.NaN; lastT = t; continue; }

      float diff = tmpP.y - h;
      if (!Float.isNaN(lastDiff) && Math.signum(lastDiff) != Math.signum(diff)) {
        // Bracketed: binary search a few steps
        float a = lastT, b = t;
        for (int i = 0; i < refineIters; i++) {
          float m = 0.5f * (a + b);
          tmpA.set(ray.origin).mulAdd(ray.direction, m);
          float hm = s.heightAt(tmpA.x, tmpA.z, Float.NaN);
          float dm = tmpA.y - hm;
          if (dm > 0f) a = m; else b = m;
        }
        float hitT = 0.5f * (a + b);
        tmpB.set(ray.origin).mulAdd(ray.direction, hitT);
        float hy = s.heightAt(tmpB.x, tmpB.z, tmpB.y);
        tmpB.y = hy;
        return new Vector3(tmpB); // return a copy for caller safety
      }
      lastDiff = diff;
      lastT = t;
    }
    return null; // no intersection in range
  }

  /**
   * Fast helper: intersect ray with y=0 plane to get XZ, then sample base height once.
   * No marching; suitable for editor tools that only need the point under the cursor.
   */
  public Vector3 rayIntersectTerrainFastPlane(Ray ray, HeightSampler s) {
    float denom = ray.direction.y;
    if (Math.abs(denom) > 1e-6f) {
      float t = (0f - ray.origin.y) / denom;
      tmpA.set(ray.origin).mulAdd(ray.direction, t);
    } else {
      tmpA.set(ray.origin.x, 0f, ray.origin.z);
    }
    float h = s.heightAt(tmpA.x, tmpA.z, Float.NaN);
    return new Vector3(tmpA.x, h, tmpA.z);
  }
}
