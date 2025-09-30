package com.example.game.World.Editor.Tools.Terrain;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.utils.TimeUtils;
import com.example.game.World.TerrainSystem;
import com.example.game.World.Editor.Tools.AbstractBrushTool;
import com.example.game.World.Editor.Tools.BrushSettings;

/**
 * Continuous Raise/Lower brush with per-ring accumulation (works even with tiny dt).
 *
 * Strength mapping:
 *   strength = 0.5 -> no change
 *   strength > 0.5 -> raise (rate ∝ strength-0.5)
 *   strength < 0.5 -> lower (rate ∝ 0.5-strength)
 *
 * Uses only: terrain.getTileSize(), terrain.editHeightsCircle(...), terrain.rebuildDirtyChunks()
 */
public final class RaiseLowerTool extends AbstractBrushTool {

  // ===== Tuning =====
  private static final float BASE_UNITS_PER_SEC_AT_FULL = 90f;    // units/sec at |strength-0.5|==1 and small brush
  private static final float STRENGTH_REF_RADIUS_CELLS  = 16f;    // radius softening pivot (cells)
  private static final float STRENGTH_RADIUS_EXP        = 1.0f;

  // Falloff via layered rings (outer→inner). TARGET_W are cumulative (edge≈0 → center≈1).
  private static final float[] RADIUS_FRAC = { 1.00f, 0.75f, 0.50f, 0.25f };
  private static final float[] TARGET_W    = { 0.10f, 0.35f, 0.70f, 1.00f };

  // Subtle variation (sign-preserving)
  private static final float NOISE_AMOUNT = 0.08f; // ±8%

  // Lazy mouse (large brushes)
  private static final float SMOOTH_MIN_RADIUS = 6f;   // cells
  private static final float SMOOTH_MAX_RADIUS = 64f;  // cells
  private static final float SMOOTH_MIN_ALPHA  = 0.00f;
  private static final float SMOOTH_MAX_ALPHA  = 0.75f;

  // Safety cap for extremely small dt to avoid starvation
  private static final float MIN_EFFECTIVE_DT = 1f / 120f;

  // ===== State =====
  private boolean pointerActive = false;

  private final Vector3 targetWorld = new Vector3();
  private final Vector3 smoothWorld = new Vector3();
  private final Vector3 lastStampWorld = new Vector3();
  private long lastAppliedNs = 0L;

  // Per-ring fractional carry (height units) so small deltas accumulate
  private final float[] ringCarry = new float[RADIUS_FRAC.length];

  public RaiseLowerTool(TerrainSystem terrain, BrushSettings sharedBrush) { super(sharedBrush, terrain); }

  @Override public String name() { return "Raise / Lower (Continuous, Accumulated)"; }

  @Override
  public void buildUI(Table parent, Skin skin) {
    addBrushUI(parent, skin);
    parent.add(new Label("Hold & drag: strength 0.5 flat • >0.5 raise • <0.5 lower", skin))
          .left().padTop(6).row();
  }

  // ===== Pointer =====

  @Override
  public void onPointerDown(Vector3 world, int button, boolean shift, boolean ctrl) {
    pointerActive = true;
    targetWorld.set(world);
    smoothWorld.set(world);
    lastStampWorld.set(world);
    lastAppliedNs = TimeUtils.nanoTime();

    // reset ring accumulators
    for (int i = 0; i < ringCarry.length; i++) ringCarry[i] = 0f;

    // Click feedback: apply a small immediate tick
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

  // ===== Update-driven sculpting =====
  // Call every frame from your editor loop.
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

  // ===== Core =====

  private void applyAt(Vector3 wpos, float dt) {
    // Signed strength around 0.5
    float s = clamp01(brush.getStrength());
    float signed = (s - 0.5f) * 2f;     // [-1..+1]
    if (signed == 0f) return;

    // Radius scaling (big brush gentler)
    int baseRadius = Math.max(1, (int)brush.getRadius());
    float radiusScale = (float)Math.pow(
        STRENGTH_REF_RADIUS_CELLS / (STRENGTH_REF_RADIUS_CELLS + Math.max(1f, baseRadius)),
        STRENGTH_RADIUS_EXP
    );

    // Center delta units for this tick
    float centerUnits = signed * BASE_UNITS_PER_SEC_AT_FULL * radiusScale * dt;

    // Subtle deterministic noise; preserve sign
    centerUnits *= (1f + NOISE_AMOUNT * pseudoNoise(wpos));

    // Grid coords
    float tile = terrain.getTileSize();
    int gx = (int)Math.floor(wpos.x / tile);
    int gz = (int)Math.floor(wpos.z / tile);

    // Convert cumulative weights to incremental weights (outer→inner)
    float prev = 0f;
    float[] inc = new float[TARGET_W.length];
    for (int i = 0; i < TARGET_W.length; i++) {
      float w = clamp01(TARGET_W[i]);
      inc[i] = Math.max(0f, w - prev);
      prev = w;
    }

    boolean any = false;

    // Apply layered rings with per-ring accumulation so tiny deltas still produce integer steps over time
    for (int i = 0; i < RADIUS_FRAC.length; i++) {
      int r = Math.max(1, Math.round(baseRadius * RADIUS_FRAC[i]));
      float part = centerUnits * inc[i];

      // accumulate fractional part
      float acc = ringCarry[i] + part;

      // extract integer delta without losing sign; keep remainder in carry
      int deltaInt = (acc > 0f) ? (int)Math.floor(acc) : (int)Math.ceil(acc);

      // update carry with leftover after taking integer portion
      ringCarry[i] = acc - deltaInt;

      if (deltaInt == 0) continue;

      short delta = (short)Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, deltaInt));
      if (delta == 0) continue;

      terrain.editHeightsCircle(gx, gz, r, delta);
      any = true;
    }

    if (any) {
      terrain.rebuildDirtyChunks();
      lastStampWorld.set(wpos);
      lastAppliedNs = TimeUtils.nanoTime();
    }
  }

  // ===== Helpers =====

  private float smoothingAlpha(int radiusCells) {
    float r = clamp(SMOOTH_MIN_RADIUS, SMOOTH_MAX_RADIUS, radiusCells);
    float t = (r - SMOOTH_MIN_RADIUS) / (SMOOTH_MAX_RADIUS - SMOOTH_MIN_RADIUS);
    t = clamp01(t);
    return lerp(SMOOTH_MIN_ALPHA, SMOOTH_MAX_ALPHA, t);
  }

  // Tiny deterministic hash noise in [-1..1]
  private float pseudoNoise(Vector3 wpos) {
    float tile = terrain.getTileSize();
    int ix = (int)Math.floor(wpos.x / tile);
    int iz = (int)Math.floor(wpos.z / tile);
    int h = ix * 374761393 + iz * 668265263;
    h = (h ^ (h >>> 13)) * 1274126177;
    h ^= (h >>> 16);
    return ((h & 0xFFFF) / 32767.5f) - 1f; // [-1..1]
  }

  private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
  private static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }
  private static float clamp(float lo, float hi, float v) { return v < lo ? lo : (v > hi ? hi : v); }
}
