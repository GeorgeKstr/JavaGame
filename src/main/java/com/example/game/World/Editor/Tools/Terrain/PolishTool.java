package com.example.game.World.Editor.Tools.Terrain;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.utils.TimeUtils;
import com.example.game.World.TerrainSystem;
import com.example.game.World.Editor.Tools.AbstractBrushTool;
import com.example.game.World.Editor.Tools.BrushSettings;

/**
 * Smooth/Polish brush:
 * - Calls terrain.getEditor().polishCircle(gx,gz,r, effectiveStrength)
 * - effectiveStrength encodes "passes per second", softened by radius, and scaled by dt.
 *   This avoids large burst loops while preserving overall rate.
 */
public final class PolishTool extends AbstractBrushTool {

  // Target rate for a small brush at strength=1.0
  private static final float BASE_PASSES_PER_SEC_AT_FULL = 40f;
  private static final float REF_RADIUS_CELLS = 16f; // big brushes gentler

  // Pointer smoothing (large brushes)
  private static final float SMOOTH_MIN_RADIUS = 6f;
  private static final float SMOOTH_MAX_RADIUS = 64f;
  private static final float SMOOTH_MIN_ALPHA  = 0.00f;
  private static final float SMOOTH_MAX_ALPHA  = 0.75f;

  // Avoid starvation on tiny dt; also gives click feedback on press
  private static final float MIN_EFFECTIVE_DT = 1f / 120f;

  private boolean pointerActive = false;

  private final Vector3 targetWorld = new Vector3();
  private final Vector3 smoothWorld = new Vector3();

  public PolishTool(TerrainSystem terrain, BrushSettings sharedBrush) { super(sharedBrush, terrain); }

  @Override public String name() { return "Smooth"; }

  @Override
  public void buildUI(Table parent, Skin skin) {
    addBrushUI(parent, skin);
    parent.add(new Label("Hold & drag to smooth. Strength = how fast it relaxes.", skin))
          .left().padTop(6).row();
  }

  @Override
  public void onPointerDown(Vector3 world, int button, boolean shift, boolean ctrl) {
    pointerActive = true;
    targetWorld.set(world);
    smoothWorld.set(world);

    // tiny immediate tick for snappy feedback
    applyAt(smoothWorld, Math.max(MIN_EFFECTIVE_DT, 1f/60f));
  }

  @Override
  public void onPointerDrag(Vector3 world, int button, boolean shift, boolean ctrl) {
    targetWorld.set(world);
  }

  @Override
  public void onPointerUp(Vector3 world, int button, boolean shift, boolean ctrl) {
    pointerActive = false;
  }

  // IMPORTANT: match RaiseLowerTool’s signature so the editor actually calls us.
  @Override
  public void update(float dt, Camera cam) {
    super.update(dt, cam);
    if (!pointerActive) return;

    // Smooth the cursor for large brushes
    float alpha = smoothingAlpha((int)brush.getRadius());
    smoothWorld.x = lerp(smoothWorld.x, targetWorld.x, 1f - alpha);
    smoothWorld.y = lerp(smoothWorld.y, targetWorld.y, 1f - alpha);
    smoothWorld.z = lerp(smoothWorld.z, targetWorld.z, 1f - alpha);

    applyAt(smoothWorld, Math.max(dt, MIN_EFFECTIVE_DT));
  }

  private void applyAt(Vector3 wpos, float dt) {
    float s = clamp01(brush.getStrength());
    if (s <= 0f) return;

    int rCells = Math.max(1, (int)brush.getRadius());

    // passes/sec, softened by radius
    float radiusScale = REF_RADIUS_CELLS / (REF_RADIUS_CELLS + rCells);

    // Convert that into a single effective strength for this frame: [0..1]
    // Multiple small lerps ≈ repeated passes; this is stable and avoids bursts.
    float passesThisFrame = BASE_PASSES_PER_SEC_AT_FULL * radiusScale * dt;
    float effectiveStrength = clamp01(s * passesThisFrame);

    float tile = terrain.getTileSize();
    int gx = (int)Math.floor(wpos.x / tile);
    int gz = (int)Math.floor(wpos.z / tile);

    if (terrain.getEditor().polishCircle(gx, gz, rCells/2, effectiveStrength)) {
      terrain.rebuildDirtyChunks();
    }
  }

  private float smoothingAlpha(int radiusCells) {
    float r = clamp(SMOOTH_MIN_RADIUS, SMOOTH_MAX_RADIUS, radiusCells);
    float t = (r - SMOOTH_MIN_RADIUS) / (SMOOTH_MAX_RADIUS - SMOOTH_MIN_RADIUS);
    t = clamp01(t);
    return lerp(SMOOTH_MIN_ALPHA, SMOOTH_MAX_ALPHA, t);
  }

  private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
  private static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }
  private static float clamp(float lo, float hi, float v) { return v < lo ? lo : (v > hi ? hi : v); }
}
