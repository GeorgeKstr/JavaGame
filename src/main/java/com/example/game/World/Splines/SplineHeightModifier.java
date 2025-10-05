package com.example.game.World.Splines;

import com.badlogic.gdx.math.Vector3;
import com.example.game.World.HeightModifier;

import java.util.List;
import java.util.function.Supplier;

/** Non-destructive height modifier that lowers terrain along river splines by riverDepth with bank falloff. */
public final class SplineHeightModifier implements HeightModifier {
  private final Supplier<List<TerrainSpline>> splinesSupplier;
  private final int samplesPerSegment;

  public SplineHeightModifier(Supplier<List<TerrainSpline>> splinesSupplier) {
    this(splinesSupplier, 12);
  }

  public SplineHeightModifier(Supplier<List<TerrainSpline>> splinesSupplier, int samplesPerSegment) {
    this.splinesSupplier = splinesSupplier;
    this.samplesPerSegment = Math.max(2, samplesPerSegment);
  }

  @Override public float deltaAt(float x, float z) {
    List<TerrainSpline> list = splinesSupplier.get();
    if (list == null || list.isEmpty()) return 0f;
    float total = 0f;
    for (TerrainSpline s : list) {
      if (s.getType() != TerrainSpline.Type.RIVER) continue;
      float halfW = Math.max(0.01f, s.width * 0.5f);
      if (halfW <= 0.01f || s.riverDepth <= 0f || s.size() < 2) continue;
      // sample polyline
      List<Vector3> pts = s.samplePositions(samplesPerSegment);
      float minD = Float.POSITIVE_INFINITY;
      for (int i = 0; i < pts.size() - 1; i++) {
        Vector3 a = pts.get(i), b = pts.get(i+1);
        float d = distPointToSegmentXZ(x, z, a.x, a.z, b.x, b.z);
        if (d < minD) minD = d;
      }
      if (minD <= halfW) {
        float t = 1f - (minD / halfW); // 1 at center, 0 at edge
        // Shape using bankFalloff: 0.0 -> sharper, 1.0 -> softer
        float k = 1f + 2f * clamp01(s.bankFalloff); // 1..3
        float shaped = (float)Math.pow(t, k);
        total += -s.riverDepth * shaped;
      }
    }
    return total;
  }

  private static float distPointToSegmentXZ(float px, float pz, float ax, float az, float bx, float bz) {
    float vx = bx - ax, vz = bz - az;
    float wx = px - ax, wz = pz - az;
    float vv = vx*vx + vz*vz;
    if (vv <= 1e-6f) return (float)Math.sqrt(wx*wx + wz*wz);
    float t = (wx*vx + wz*vz) / vv;
    if (t <= 0f) return (float)Math.sqrt(wx*wx + wz*wz);
    if (t >= 1f) {
      float dx = px - bx, dz = pz - bz;
      return (float)Math.sqrt(dx*dx + dz*dz);
    }
    float cx = ax + t * vx, cz = az + t * vz;
    float dx = px - cx, dz = pz - cz;
    return (float)Math.sqrt(dx*dx + dz*dz);
  }

  private static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }
}
