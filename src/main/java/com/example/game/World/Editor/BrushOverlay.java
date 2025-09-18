package com.example.game.World.Editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.example.game.World.HeightSampler;

/**
 * Renders a circular brush overlay on the terrain under the mouse.
 */
public class BrushOverlay {
  private final ShapeRenderer sr = new ShapeRenderer();
  private final Vector3 center = new Vector3();

  private boolean hasHit = false;
  private float[] rx, ry, rz;
  private final int segments;
  private float radiusWorld;

  public BrushOverlay(int segments) {
    this.segments = Math.max(16, segments);
    rx = new float[this.segments];
    ry = new float[this.segments];
    rz = new float[this.segments];
  }

  /**
   * Update the overlay by casting a ray from the mouse into the terrain.
   */
  public void update(Camera cam, HeightSampler sampler, int brushRadiusTiles, float tileSize) {
    float mx = Gdx.input.getX();
    float my = Gdx.input.getY(); // raw Y (don’t flip, Gdx already handles viewport coords)
    Ray ray = ((com.badlogic.gdx.graphics.PerspectiveCamera) cam).getPickRay(mx, my);

    Vector3 hit = rayIntersectTerrain(ray, sampler, 0.75f, 8);
    hasHit = (hit != null);
    if (!hasHit) return;

    center.set(hit.x, hit.y + 0.02f, hit.z);
    radiusWorld = brushRadiusTiles * tileSize;

    for (int i = 0; i < segments; i++) {
      float a = (float) (i * Math.PI * 2.0 / segments);
      float x = center.x + radiusWorld * (float) Math.cos(a);
      float z = center.z + radiusWorld * (float) Math.sin(a);
      float y = sampler.heightAt(x, z, center.y) + 0.02f;
      rx[i] = x;
      ry[i] = y;
      rz[i] = z;
    }
  }

  /** Draw the overlay ring. */
  public void draw(Camera cam) {
    if (!hasHit) return;
    Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    sr.setProjectionMatrix(cam.combined);
    sr.begin(ShapeRenderer.ShapeType.Line);
    sr.setColor(1f, 1f, 0.2f, 1f);

    for (int i = 0; i < segments; i++) {
      int j = (i + 1) % segments;
      sr.line(rx[i], ry[i], rz[i], rx[j], ry[j], rz[j]);
    }

    // center cross
    float s = Math.max(0.2f, radiusWorld * 0.05f);
    sr.line(center.x - s, center.y, center.z, center.x + s, center.y, center.z);
    sr.line(center.x, center.y, center.z - s, center.x, center.y, center.z + s);

    sr.end();
  }

  public void dispose() { sr.dispose(); }

  /**
   * Ray vs. heightfield intersection. Marches forward and binary-refines.
   */
  public static Vector3 rayIntersectTerrain(Ray ray, HeightSampler s, float step, int refineIters) {
    float tPrev = 0f, diffPrev = Float.NaN;
    Vector3 p = new Vector3();

    for (float t = step; t <= 2000f; t += step) {
      p.set(ray.origin).mulAdd(ray.direction, t);
      float h = s.heightAt(p.x, p.z, Float.NaN);
      if (Float.isNaN(h)) { diffPrev = Float.NaN; tPrev = t; continue; }

      float diff = p.y - h;
      if (!Float.isNaN(diffPrev) && Math.signum(diffPrev) != Math.signum(diff)) {
        float a = tPrev, b = t;
        for (int i = 0; i < refineIters; i++) {
          float m = 0.5f * (a + b);
          Vector3 pm = new Vector3(ray.origin).mulAdd(ray.direction, m);
          float hm = s.heightAt(pm.x, pm.z, Float.NaN);
          float dm = pm.y - hm;
          if (dm > 0) a = m; else b = m;
        }
        float m = 0.5f * (a + b);
        Vector3 hit = new Vector3(ray.origin).mulAdd(ray.direction, m);
        hit.y = s.heightAt(hit.x, hit.z, hit.y);
        return hit;
      }
      diffPrev = diff; tPrev = t;
    }
    return null;
  }
}
