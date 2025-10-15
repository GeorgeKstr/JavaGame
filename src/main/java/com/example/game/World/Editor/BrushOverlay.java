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
import com.example.game.World.TerrainSystem;

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
  private final float[] frx, fry, frz; // falloff ring vertices
  private final Vector3 center = new Vector3();
  private boolean hasHit = false;
  private float radiusWorld = 1f;
  private final TerrainSystem terrain;

  public BrushOverlay(int segments, TerrainSystem terrain) {
    this.segments = Math.max(16, segments);
    this.rx = new float[this.segments];
    this.ry = new float[this.segments];
    this.rz = new float[this.segments];
    this.frx = new float[this.segments];
    this.fry = new float[this.segments];
    this.frz = new float[this.segments];
    this.terrain = terrain;
  }

  /** Update from mouse → world; brushRadiusTiles is in *cells*, tileSize in *meters*. */
  public void update(Vector3 position, int brushRadiusTiles, float brushFalloff, float tileSize) {
    

    // Intersect against the terrain heightfield using the shared picker (hill-aware),
    // fallback to fast plane if no intersection is found within range.
    hasHit = false;
    Vector3 hit = position;
    if (hit == null) return;
    hasHit = true;

    center.set(hit.x, hit.y + 0.02f, hit.z);
    radiusWorld = Math.max(1, brushRadiusTiles) * tileSize;

    // Build ring by re-sampling BASE height along the circle in XZ
    HeightSampler base = terrain.getBaseHeightSampler();
    for (int i = 0; i < segments; i++) {
      float a = (float)(i * Math.PI * 2.0 / segments);
      float x = center.x + radiusWorld * (float)Math.cos(a);
      float z = center.z + radiusWorld * (float)Math.sin(a);
      float y = base.heightAt(x, z, center.y);
      if (Float.isNaN(y)) y = center.y; // guard against unloaded cells
      rx[i] = x; ry[i] = y + 0.02f; rz[i] = z;
    }

    float radiusFallOff = radiusWorld * brushFalloff;
    //System.out.println("radiusFallOff: " + radiusFallOff);
    for (int i = 0; i < segments; i++) {
      float a = (float)(i * Math.PI * 2.0 / segments);
      float x = center.x + radiusFallOff * (float)Math.cos(a);
      float z = center.z + radiusFallOff * (float)Math.sin(a);
      float y = base.heightAt(x, z, center.y);
      if (Float.isNaN(y)) y = center.y; // guard against unloaded cells
      frx[i] = x; fry[i] = y + 0.02f; frz[i] = z;
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
    sr.setColor(1f, 1f, 0.2f, 0.95f);
    for (int i = 0; i < segments; i++) {
      int j = (i + 1) % segments;
      sr.line(frx[i], fry[i], frz[i], frx[j], fry[j], frz[j]);
    }
    sr.end();

    Gdx.gl.glLineWidth(1f);
    Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
  }

  public void dispose() { sr.dispose(); }

  // --------------------------------------------------------------------------
  // Classic heightfield raycast: march forward and binary refine at sign change
  // --------------------------------------------------------------------------
  

  public void setRadius(float radius) {
    this.radiusWorld = Math.max(1f, radius);
  }
}
