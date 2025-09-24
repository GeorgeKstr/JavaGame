package com.example.game.World;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Plane;
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
  boolean raycastToGround(Camera cam, int sx, int sy, Vector3 out) {
    // 1) Build a world-space ray from the screen coords
    tmpNear.set(sx, sy, 0f);
    tmpFar.set(sx, sy, 1f);
    cam.unproject(tmpNear);
    cam.unproject(tmpFar);
    dir.set(tmpFar).sub(tmpNear).nor();

    // 2) March forward until we cross height (ray y <= terrain height)
    final float MAX_DIST = 4000f;
    final float STEP = Math.max(0.25f, w.tile * 0.5f); // tune step vs precision
    float lastDy = Float.POSITIVE_INFINITY;

    for (float t = 0f; t <= MAX_DIST; t += STEP) {
      p.set(tmpNear).mulAdd(dir, t);
      float h = w.sampleHeightAtWorld(p.x, p.z, Float.NEGATIVE_INFINITY);
      float dy = p.y - h;

      if (dy <= 0f) {
        // 2b) Refine crossing with a few binary steps
        float t0 = Math.max(0f, t - STEP), t1 = t;
        for (int i = 0; i < 8; i++) {
          float tm = 0.5f * (t0 + t1);
          p.set(tmpNear).mulAdd(dir, tm);
          float hm = w.sampleHeightAtWorld(p.x, p.z, Float.NEGATIVE_INFINITY);
          if (p.y - hm > 0f) {
            t0 = tm;
          } else {
            t1 = tm;
          }
        }
        float thit = 0.5f * (t0 + t1);
        out.set(tmpNear).mulAdd(dir, thit);
        // Snap Y exactly to terrain height at the hit XZ
        out.y = w.sampleHeightAtWorld(out.x, out.z, out.y);
        return true;
      }

      // If dy starts increasing, we're moving away from ground → bail to fallback
      if (dy > lastDy) break;
      lastDy = dy;
    }

    // 3) Fallback: intersect with ground plane y=0, then snap Y to terrain height there
    Plane ground = new Plane(new Vector3(0f, 1f, 0f), 0f); // y = 0
    Ray ray = new Ray(tmpNear, dir);
    if (Intersector.intersectRayPlane(ray, ground, out)) {
      out.y = w.sampleHeightAtWorld(out.x, out.z, out.y);
      return true;
    }

    return false;
  }
}
