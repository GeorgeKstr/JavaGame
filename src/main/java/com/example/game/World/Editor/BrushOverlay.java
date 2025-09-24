package com.example.game.World.Editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.example.game.World.HeightSampler;

/**
 * Terrain brush overlay (classic mode, like your old TerrainSystem circle).
 * - Uses Camera.getPickRay(sx,sy) (no manual Y flip).
 * - March + binary refine to hit the heightfield via HeightSampler.
 * - Conforms the ring to the terrain by sampling at each ring vertex.
 * - Keeps GL state simple: depth disabled while drawing lines; re-enabled after.
 */
public final class BrushOverlay {
  private final ShapeRenderer sr = new ShapeRenderer();

  private final int segments;
  private final float[] rx, ry, rz;   // ring vertices
  private final Vector3 center = new Vector3();
  private boolean hasHit = false;
  private float radiusWorld = 1f;

  public BrushOverlay(int segments) {
    this.segments = Math.max(16, segments);
    this.rx = new float[this.segments];
    this.ry = new float[this.segments];
    this.rz = new float[this.segments];
  }

  /** Update from mouse → world; brushRadiusTiles is in *cells*, tileSize in *meters*. */
  public void update(Camera cam, HeightSampler sampler, int brushRadiusTiles, float tileSize) {
    // Get a pick ray in window coords (libGDX handles viewport + Y orientation)
    int sx = Gdx.input.getX();
    int sy = Gdx.input.getY();
    Ray ray = ((PerspectiveCamera) cam).getPickRay(sx, sy);

    // Intersect against the terrain heightfield
    Vector3 hit = rayIntersectTerrain(ray, sampler, Math.max(0.25f, tileSize * 0.5f), 8);
    hasHit = (hit != null);
    if (!hasHit) return;

    center.set(hit.x, hit.y + 0.02f, hit.z);
    radiusWorld = Math.max(1, brushRadiusTiles) * tileSize;

    // Build ring by re-sampling height along the circle in XZ
    for (int i = 0; i < segments; i++) {
      float a = (float)(i * Math.PI * 2.0 / segments);
      float x = center.x + radiusWorld * (float)Math.cos(a);
      float z = center.z + radiusWorld * (float)Math.sin(a);
      float y = sampler.heightAt(x, z, center.y);
      if (Float.isNaN(y)) y = center.y; // guard against unloaded cells
      rx[i] = x; ry[i] = y + 0.02f; rz[i] = z;
    }
  }

  /** Draw the ring on top of the world. Call after your terrain/sky pipeline, before UI. */
  public void draw(Camera cam) {
    if (!hasHit) return;

    // Make sure we’re drawing to the backbuffer & full viewport (safe across passes)
    int w = Gdx.graphics.getWidth();
    int h = Gdx.graphics.getHeight();
    if (Gdx.gl30 != null) {
      Gdx.gl30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    } else {
      Gdx.gl20.glBindFramebuffer(GL20.GL_FRAMEBUFFER, 0);
    }
    Gdx.gl.glViewport(0, 0, w, h);

    // Draw lines on top
    Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    Gdx.gl.glDisable(GL20.GL_CULL_FACE);
    Gdx.gl.glLineWidth(2f);

    sr.setProjectionMatrix(cam.combined);
    sr.begin(ShapeRenderer.ShapeType.Line);
    sr.setColor(1f, 1f, 0.2f, 0.95f);
    for (int i = 0; i < segments; i++) {
      int j = (i + 1) % segments;
      sr.line(rx[i], ry[i], rz[i], rx[j], ry[j], rz[j]);
    }
    sr.end();

    Gdx.gl.glLineWidth(1f);
    Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
  }

  public void dispose() { sr.dispose(); }

  // --------------------------------------------------------------------------
  // Classic heightfield raycast: march forward and binary refine at sign change
  // --------------------------------------------------------------------------
  private static Vector3 rayIntersectTerrain(Ray ray, HeightSampler s, float step, int refineIters) {
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
