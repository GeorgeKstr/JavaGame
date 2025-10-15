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
  private final Vector3 e1      = new Vector3();
  private final Vector3 e2      = new Vector3();
  private final Vector3 pvec    = new Vector3();
  private final Vector3 tvec    = new Vector3();
  private final Vector3 qvec    = new Vector3();

  TerrainPicking(TerrainWorld w) {
    this.w = w;
  }

  /**
   * Raycast a world-space ray to the terrain using cellwise DDA and exact triangle tests.
   * Returns the hit world position, or null if it missed loaded terrain.
   */
  public Vector3 rayIntersectTerrain(Ray ray, HeightSampler s) {
    float tile = s.getTileSize();
    if (!(tile > 0f)) tile = 1f;
    float maxT = 4000f;

    float dx = ray.direction.x;
    float dz = ray.direction.z;
    if (Math.abs(dx) < 1e-8f && Math.abs(dz) < 1e-8f) {
      return rayIntersectTerrainFastPlane(ray, s);
    }

    int gx = (int)Math.floor(ray.origin.x / tile);
    int gz = (int)Math.floor(ray.origin.z / tile);
    int stepX = dx > 0f ? 1 : -1;
    int stepZ = dz > 0f ? 1 : -1;

    float nextGridX = (gx + (dx > 0f ? 1f : 0f)) * tile;
    float nextGridZ = (gz + (dz > 0f ? 1f : 0f)) * tile;

    float tMaxX = (Math.abs(dx) > 1e-8f) ? (nextGridX - ray.origin.x) / dx : Float.POSITIVE_INFINITY;
    float tMaxZ = (Math.abs(dz) > 1e-8f) ? (nextGridZ - ray.origin.z) / dz : Float.POSITIVE_INFINITY;
    if (tMaxX < 0f) tMaxX = 0f;
    if (tMaxZ < 0f) tMaxZ = 0f;

    float tDeltaX = (Math.abs(dx) > 1e-8f) ? (tile / Math.abs(dx)) : Float.POSITIVE_INFINITY;
    float tDeltaZ = (Math.abs(dz) > 1e-8f) ? (tile / Math.abs(dz)) : Float.POSITIVE_INFINITY;

    float tCurr = 0f;
    float bestT = Float.POSITIVE_INFINITY;
    Vector3 best = null;
    int maxSteps = 4096;
    for (int steps = 0; steps < maxSteps && tCurr <= maxT; steps++) {
      float x0 = gx * tile;
      float z0 = gz * tile;
      float x1 = x0 + tile;
      float z1 = z0 + tile;

      float tEnter = tCurr;
      float tExit = Math.min(tMaxX, tMaxZ);
      if (tExit < tEnter) tExit = tEnter;

      float h00 = s.heightAt(x0, z0, Float.NaN);
      float h10 = s.heightAt(x1, z0, Float.NaN);
      float h01 = s.heightAt(x0, z1, Float.NaN);
      float h11 = s.heightAt(x1, z1, Float.NaN);

      if (!(Float.isNaN(h00) || Float.isNaN(h10) || Float.isNaN(h01) || Float.isNaN(h11))) {
        float tHit = Float.POSITIVE_INFINITY;

        Vector3 v00 = tmpA.set(x0, h00, z0);
        Vector3 v10 = tmpB.set(x1, h10, z0);
        Vector3 v11 = tmpP.set(x1, h11, z1);
        float th = rayTriangleT(ray, v00, v10, v11);
        if (th >= tEnter - 1e-6f && th <= tExit + 1e-6f && th >= 0f) tHit = Math.min(tHit, th);

        Vector3 v01 = e1.set(x0, h01, z1);
        th = rayTriangleT(ray, v00, v11, v01);
        if (th >= tEnter - 1e-6f && th <= tExit + 1e-6f && th >= 0f) tHit = Math.min(tHit, th);

        if (tHit != Float.POSITIVE_INFINITY) {
          if (tHit < bestT) {
            bestT = tHit;
            if (best == null) best = new Vector3();
            best.set(ray.origin).mulAdd(ray.direction, tHit);
          }
        }

        // Fallback: bilinear surface root-find within the cell segment [tEnter, tExit]
        // Helps when triangle tests miss due to degeneracy/precision near edges
        // Define f(t) = y_ray(t) - h_bilinear(u(t), v(t)) and solve f(t)=0 by bisection
        float y0 = ray.origin.y + tEnter * ray.direction.y;
        float x0r = ray.origin.x + tEnter * ray.direction.x;
        float z0r = ray.origin.z + tEnter * ray.direction.z;
        float u0 = (x0r - x0) / tile; float v0 = (z0r - z0) / tile;
        // Clamp slightly inside [0,1] to avoid sampling just outside cell due to float error
        u0 = Math.max(0f, Math.min(1f, u0)); v0 = Math.max(0f, Math.min(1f, v0));
        float hb0 = h00*(1f-u0)*(1f-v0) + h10*u0*(1f-v0) + h01*(1f-u0)*v0 + h11*u0*v0;
        float f0 = y0 - hb0;

        float y1 = ray.origin.y + tExit * ray.direction.y;
        float x1r = ray.origin.x + tExit * ray.direction.x;
        float z1r = ray.origin.z + tExit * ray.direction.z;
        float u1 = (x1r - x0) / tile; float v1 = (z1r - z0) / tile;
        u1 = Math.max(0f, Math.min(1f, u1)); v1 = Math.max(0f, Math.min(1f, v1));
        float hb1 = h00*(1f-u1)*(1f-v1) + h10*u1*(1f-v1) + h01*(1f-u1)*v1 + h11*u1*v1;
        float f1 = y1 - hb1;

        // If signs differ or one endpoint is very close to zero, do bisection
        if (!(Float.isNaN(f0) || Float.isNaN(f1))) {
          final float EPS = 1e-5f;
          boolean nearZero0 = Math.abs(f0) <= EPS;
          boolean nearZero1 = Math.abs(f1) <= EPS;
          if (nearZero0 || nearZero1 || f0 * f1 < 0f) {
            float a = tEnter, b = tExit;
            float fa = f0, fb = f1;
            // Tighten bracket if endpoints are outside due to precision
            for (int it = 0; it < 18; it++) {
              float mid = 0.5f * (a + b);
              float ym = ray.origin.y + mid * ray.direction.y;
              float xm = ray.origin.x + mid * ray.direction.x;
              float zm = ray.origin.z + mid * ray.direction.z;
              float um = (xm - x0) / tile; float vm = (zm - z0) / tile;
              um = Math.max(0f, Math.min(1f, um)); vm = Math.max(0f, Math.min(1f, vm));
              float hbm = h00*(1f-um)*(1f-vm) + h10*um*(1f-vm) + h01*(1f-um)*vm + h11*um*vm;
              float fm = ym - hbm;
              if (Math.abs(fm) <= EPS) { a = b = mid; fa = fb = fm; break; }
              // Prefer side that brackets zero; if not, choose the side with smaller |f|
              if (fa * fm <= 0f) { b = mid; fb = fm; }
              else if (fm * fb <= 0f) { a = mid; fa = fm; }
              else {
                if (Math.abs(fa) < Math.abs(fb)) { b = mid; fb = fm; } else { a = mid; fa = fm; }
              }
            }
            float tRoot = 0.5f * (a + b);
            if (tRoot >= tEnter - 1e-6f && tRoot <= tExit + 1e-6f && tRoot >= 0f) {
              if (tRoot < bestT) {
                bestT = tRoot;
                if (best == null) best = new Vector3();
                best.set(ray.origin).mulAdd(ray.direction, tRoot);
              }
            }
          }
        }
      }

      if (tMaxX < tMaxZ) {
        gx += stepX;
        tCurr = tMaxX;
        tMaxX += tDeltaX;
      } else {
        gz += stepZ;
        tCurr = tMaxZ;
        tMaxZ += tDeltaZ;
      }

      // If we've advanced beyond the current best intersection along the ray, we can stop
      if (bestT != Float.POSITIVE_INFINITY && tCurr > bestT + 1e-6f) break;
    }
    return best;
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

  private float rayTriangleT(Ray ray, Vector3 v0, Vector3 v1, Vector3 v2) {
    e1.set(v1).sub(v0);
    e2.set(v2).sub(v0);
    pvec.set(ray.direction).crs(e2);
    float det = e1.dot(pvec);
    if (det > -1e-8f && det < 1e-8f) return Float.POSITIVE_INFINITY;
    float invDet = 1f / det;
    tvec.set(ray.origin).sub(v0);
    float u = tvec.dot(pvec) * invDet;
    if (u < 0f || u > 1f) return Float.POSITIVE_INFINITY;
    qvec.set(tvec).crs(e1);
    float v = ray.direction.dot(qvec) * invDet;
    if (v < 0f || u + v > 1f) return Float.POSITIVE_INFINITY;
    float t = e2.dot(qvec) * invDet;
    return t;
  }
}
