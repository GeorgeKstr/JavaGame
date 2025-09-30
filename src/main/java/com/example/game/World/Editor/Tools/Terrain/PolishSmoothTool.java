package com.example.game.World.Editor.Tools.Terrain;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.utils.TimeUtils;
import com.example.game.World.TerrainSystem;
import com.example.game.World.Editor.Tools.AbstractBrushTool;
import com.example.game.World.Editor.Tools.BrushSettings;

/**
 * Smooth (Local Average) — ZBrush-like polish that rounds steep edges without drifting.
 *
 * How it works:
 *  - Split the brush into 4 bands (center disk + 3 rings).
 *  - Compute each band's average height.
 *  - Do one explicit smoothing step: each band moves toward the average of its neighbors.
 *  - Normalize deltas so the area-weighted sum is zero (no sinking/raising).
 *  - Apply with annulus stamps using editHeightsCircle().
 *
 * Strength in [0..1] sets smoothing amount per second (0 = none, 1 = strong).
 * Requires only: getTileSize(), sampleHeightShort(int,int), editHeightsCircle(...), rebuildDirtyChunks().
 */
public final class PolishSmoothTool extends AbstractBrushTool {

  // ===== Tuning =====
  private static final float BASE_UNITS_PER_SEC_AT_FULL = 110f; // intensity at strength=1 and small brush
  private static final float STRENGTH_REF_RADIUS_CELLS  = 16f;  // radius softening pivot (cells)
  private static final float STRENGTH_RADIUS_EXP        = 1.0f; // curve for radius softening

  // Band radii as fractions of brush radius
  private static final float R1_F = 0.25f; // center disk outer radius
  private static final float R2_F = 0.50f; // ring1 outer radius
  private static final float R3_F = 0.75f; // ring2 outer radius
  private static final float R4_F = 1.00f; // ring3 (full brush) outer radius

  // Neighbor mix (how much each band listens to neighbors during the explicit step)
  // End bands use a single neighbor; inner bands use average of two neighbors.
  private static final float NEIGHBOR_MIX = 1.0f;      // 1.0 = full neighbor average; lower for gentler smoothing

  // Cursor smoothing for large brushes (“lazy mouse”)
  private static final float SMOOTH_MIN_RADIUS = 6f, SMOOTH_MAX_RADIUS = 64f;
  private static final float SMOOTH_MIN_ALPHA  = 0.00f, SMOOTH_MAX_ALPHA = 0.75f;

  // Spacing / throttling
  private static final float MIN_STAMP_DT_SEC    = 1f / 90f;
  private static final float MIN_STAMP_DIST_FRAC = 0.25f;

  // dt floor so accumulators progress
  private static final float MIN_EFFECTIVE_DT = 1f / 240f;

  // ===== State =====
  private boolean pointerActive = false;
  private final Vector3 targetWorld = new Vector3();
  private final Vector3 smoothWorld = new Vector3();
  private final Vector3 lastStampWorld = new Vector3();
  private long lastStampNs = 0L;

  // Per-band carry (center + ring1 + ring2 + ring3)
  private float carryC = 0f, carryR1 = 0f, carryR2 = 0f, carryR3 = 0f;

  public PolishSmoothTool(TerrainSystem terrain, BrushSettings sharedBrush) { super(sharedBrush, terrain); }

  @Override public String name() { return "Smooth (Local Average)"; }

  @Override
  public void buildUI(Table parent, Skin skin) {
    addBrushUI(parent, skin);
    parent.add(new Label("Hold & drag to polish: rounds steep edges via local averaging. Strength = smoothing amount.", skin))
          .left().padTop(6).row();
  }

  // ===== Pointer =====

  @Override
  public void onPointerDown(Vector3 world, int button, boolean shift, boolean ctrl) {
    pointerActive = true;
    targetWorld.set(world);
    smoothWorld.set(world);
    lastStampWorld.set(world);
    lastStampNs = TimeUtils.nanoTime();
    carryC = carryR1 = carryR2 = carryR3 = 0f;

    applyAt(smoothWorld, Math.max(MIN_EFFECTIVE_DT, 1f/60f)); // immediate feedback
  }

  @Override
  public void onPointerDrag(Vector3 world, int button, boolean shift, boolean ctrl) {
    targetWorld.set(world);
  }

  @Override
  public void onPointerUp(Vector3 world, int button, boolean shift, boolean ctrl) {
    pointerActive = false;
  }

  // ===== Update =====
  @Override
  public void update(float dt, Camera cam) {
    super.update(dt, cam);
    if (!pointerActive) return;

    long now = TimeUtils.nanoTime();
    float since = (now - lastStampNs) / 1_000_000_000f;

    float tile = terrain.getTileSize();
    int baseR = Math.max(1, (int)brush.getRadius());
    float radiusWorld = baseR * tile;

    float dx = targetWorld.x - lastStampWorld.x;
    float dz = targetWorld.z - lastStampWorld.z;
    float distXZ = (float)Math.sqrt(dx*dx + dz*dz);

    if (since < MIN_STAMP_DT_SEC && distXZ < radiusWorld * MIN_STAMP_DIST_FRAC) return;

    // lazy mouse smoothing by radius
    float a = smoothingAlpha(baseR);
    smoothWorld.x = lerp(smoothWorld.x, targetWorld.x, 1f - a);
    smoothWorld.y = lerp(smoothWorld.y, targetWorld.y, 1f - a);
    smoothWorld.z = lerp(smoothWorld.z, targetWorld.z, 1f - a);

    applyAt(smoothWorld, Math.max(dt, MIN_EFFECTIVE_DT));

    lastStampWorld.set(smoothWorld);
    lastStampNs = now;
  }

  // ===== Core: local-average smoothing step =====

  private void applyAt(Vector3 wpos, float dt) {
    float strength = clamp01(brush.getStrength());
    if (strength <= 0.001f) return;

    int baseR = Math.max(1, (int)brush.getRadius());

    // Bigger brush => gentler by default
    float radiusScale = (float)Math.pow(
        STRENGTH_REF_RADIUS_CELLS / (STRENGTH_REF_RADIUS_CELLS + Math.max(1f, baseR)),
        STRENGTH_RADIUS_EXP
    );

    // “Step size” in height units we’re willing to apply this tick
    float stepBudget = strength * BASE_UNITS_PER_SEC_AT_FULL * radiusScale * dt;

    float tile = terrain.getTileSize();
    int cx = (int)Math.floor(wpos.x / tile);
    int cz = (int)Math.floor(wpos.z / tile);

    // Discrete radii (cells)
    int r1 = Math.max(1, Math.round(baseR * R1_F));             // band C
    int r2 = Math.max(r1 + 1, Math.round(baseR * R2_F));         // band R1 outer
    int r3 = Math.max(r2 + 1, Math.round(baseR * R3_F));         // band R2 outer
    int r4 = Math.max(r3 + 1, baseR);                            // band R3 outer (full)

    // Disk stats for cumulative disks, to derive ring means & areas
    DiskStats d1 = diskStats(cx, cz, r1);
    DiskStats d2 = diskStats(cx, cz, r2);
    DiskStats d3 = diskStats(cx, cz, r3);
    DiskStats d4 = diskStats(cx, cz, r4);

    // Areas (counts) per band
    int nC  = Math.max(1, d1.count);
    int nR1 = Math.max(1, d2.count - d1.count);
    int nR2 = Math.max(1, d3.count - d2.count);
    int nR3 = Math.max(1, d4.count - d3.count);

    // Means per band
    float aC  = d1.mean();
    float aR1 = (d2.sum - d1.sum) / nR1;
    float aR2 = (d3.sum - d2.sum) / nR2;
    float aR3 = (d4.sum - d3.sum) / nR3;

    // Neighbor averages per band (boundary bands have one neighbor; inner bands average two)
    float nCavg  = aR1;                         // center listens to ring1
    float nR1avg = (aC + aR2) * 0.5f;           // ring1 listens to center & ring2
    float nR2avg = (aR1 + aR3) * 0.5f;          // ring2 listens to ring1 & ring3
    float nR3avg = aR2;                         // ring3 listens to ring2

    // Raw desired deltas toward neighbor average
    float dC  = (nCavg  - aC ) * NEIGHBOR_MIX;
    float dR1 = (nR1avg - aR1) * NEIGHBOR_MIX;
    float dR2 = (nR2avg - aR2) * NEIGHBOR_MIX;
    float dR3 = (nR3avg - aR3) * NEIGHBOR_MIX;

    // Normalize so the area-weighted sum is ~0 (no drift)
    float weightedSum = dC * nC + dR1 * nR1 + dR2 * nR2 + dR3 * nR3;
    float totalArea   = (float)(nC + nR1 + nR2 + nR3);
    float bias = weightedSum / totalArea;
    dC  -= bias;
    dR1 -= bias;
    dR2 -= bias;
    dR3 -= bias;

    // Scale to the per-tick step budget: sum of |area * delta| defines effort.
    float effort = Math.abs(dC) * nC + Math.abs(dR1) * nR1 + Math.abs(dR2) * nR2 + Math.abs(dR3) * nR3;
    if (effort < 1e-6f) return;

    float scale = stepBudget / effort;
    dC  *= scale;
    dR1 *= scale;
    dR2 *= scale;
    dR3 *= scale;

    // Convert to integers with per-band accumulators so tiny steps still apply
    int kC  = takeIntWithCarry(dC,  Band.CENTER);
    int kR1 = takeIntWithCarry(dR1, Band.RING1);
    int kR2 = takeIntWithCarry(dR2, Band.RING2);
    int kR3 = takeIntWithCarry(dR3, Band.RING3);

    boolean any = false;

    // Apply center disk
    if (kC != 0) {
      terrain.getEditor().editHeightsCircle(cx, cz, r1, (short) clampShort(kC));
      any = true;
    }
    // Apply rings as annuli: (+ on bigger disk, − on inner disk)
    if (kR1 != 0) {
      short d = (short) clampShort(kR1);
      terrain.getEditor().editHeightsCircle(cx, cz, r2, d);
      terrain.getEditor().editHeightsCircle(cx, cz, r1, (short)-d);
      any = true;
    }
    if (kR2 != 0) {
      short d = (short) clampShort(kR2);
      terrain.getEditor().editHeightsCircle(cx, cz, r3, d);
      terrain.getEditor().editHeightsCircle(cx, cz, r2, (short)-d);
      any = true;
    }
    if (kR3 != 0) {
      short d = (short) clampShort(kR3);
      terrain.getEditor().editHeightsCircle(cx, cz, r4, d);
      terrain.getEditor().editHeightsCircle(cx, cz, r3, (short)-d);
      any = true;
    }

    if (any) terrain.rebuildDirtyChunks();
  }

  // ===== Sampling =====

  private static final class DiskStats { long sum; int count; float mean(){ return count>0 ? (sum/(float)count) : 0f; } }

  private DiskStats diskStats(int cx, int cz, int r) {
    DiskStats s = new DiskStats();
    int r2 = r * r;
    for (int dz = -r; dz <= r; dz++) {
      int zz = cz + dz, dz2 = dz * dz;
      for (int dx = -r; dx <= r; dx++) {
        if (dx*dx + dz2 > r2) continue;
        int xx = cx + dx;
        s.sum += terrain.heightAt(xx, zz); // <-- adjust name if your sampler differs
        s.count++;
      }
    }
    return s;
  }

  // ===== Accumulators & helpers =====

  private enum Band { CENTER, RING1, RING2, RING3 }

  private int takeIntWithCarry(float perSampleDelta, Band b) {
    float acc;
    switch (b) {
      case CENTER:
        acc = carryC + perSampleDelta;
        int dC = (acc > 0f) ? (int)Math.floor(acc) : (int)Math.ceil(acc);
        carryC = acc - dC; return dC;
      case RING1:
        acc = carryR1 + perSampleDelta;
        int d1 = (acc > 0f) ? (int)Math.floor(acc) : (int)Math.ceil(acc);
        carryR1 = acc - d1; return d1;
      case RING2:
        acc = carryR2 + perSampleDelta;
        int d2 = (acc > 0f) ? (int)Math.floor(acc) : (int)Math.ceil(acc);
        carryR2 = acc - d2; return d2;
      default:
        acc = carryR3 + perSampleDelta;
        int d3 = (acc > 0f) ? (int)Math.floor(acc) : (int)Math.ceil(acc);
        carryR3 = acc - d3; return d3;
    }
  }

  private static int clampShort(int v) {
    if (v > Short.MAX_VALUE) return Short.MAX_VALUE;
    if (v < Short.MIN_VALUE) return Short.MIN_VALUE;
    return v;
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
