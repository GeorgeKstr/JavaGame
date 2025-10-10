package com.example.game.World.Splines;

import com.badlogic.gdx.math.Vector3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A spline path laid on terrain. XZ define the path; Y can be sampled from terrain or overridden per-node. */
public class TerrainSpline {
  public enum Type { ROAD, RIVER }

  private final Type type;
  private final ArrayList<TerrainSplineNode> nodes = new ArrayList<>();

  // visual/emit properties (basic; extend later or move to per-type)
  public float width = 6f;       // meters (road half-width ~ width/2)
  public float riverDepth = 2f;  // meters
  public float bankFalloff = 0f; // 0..1 (0=round bowl, 1=sharper)
  public int textureId = 0;      // index into Splines texture list (separate from ground textures)
  public float transparency = 1.0f; // 0..1 alpha multiplier for ribbon rendering
  public float heightOffset = 0f;   // meters; rendering-only vertical offset for ribbon
  public boolean textured = true;   // whether to render with texture; if false, solid color helper ribbon
  public float ribbonWidthRatio = 1.0f; // scales visual ribbon width relative to river width (1=match, >1 overflow)

  public TerrainSpline(Type type) { this.type = type; }
  public Type getType() { return type; }

  public List<TerrainSplineNode> getNodes() { return Collections.unmodifiableList(nodes); }
  public void addNode(TerrainSplineNode n) { nodes.add(n); ribbonDirty = true; }
  public void insertNode(int index, TerrainSplineNode n) { nodes.add(Math.max(0, Math.min(index, nodes.size())), n); ribbonDirty = true; }
  public void removeNode(int index) { if (index>=0 && index<nodes.size()) { nodes.remove(index); ribbonDirty = true; } }
  public void setNodePos(int idx, Vector3 p) { if (idx>=0 && idx<nodes.size()) { nodes.get(idx).pos.set(p); ribbonDirty = true; } }
  public int size() { return nodes.size(); }

  // Cached ribbon geometry (left/right edge vertices sampled onto terrain)
  private volatile boolean ribbonDirty = true;
  private volatile boolean ribbonRebuilding = false;
  private float[] lx, ly, lz, rx, ry, rz; // length = sample count
  private int triPairs = 0; // number of quad segments (n-1)
  private volatile long lastRibbonRebuildMs = 0L;

  public void markRibbonDirty() { ribbonDirty = true; /* preserve existing arrays so rendering continues until rebuild publishes */ }
  public boolean isRibbonDirty() { return ribbonDirty; }
  public boolean isRibbonRebuilding() { return ribbonRebuilding; }

  public boolean hasRibbon() { return lx != null && rx != null && triPairs > 0; }

  public float[] getLX() { return lx; } public float[] getLY() { return ly; } public float[] getLZ() { return lz; }
  public float[] getRX() { return rx; } public float[] getRY() { return ry; } public float[] getRZ() { return rz; }
  public int getTriPairs() { return triPairs; }

  /** Build or rebuild cached ribbon from current spline and terrain sampler. */
  public void rebuildRibbon(com.example.game.World.HeightSampler terrain, int samplesPerSegment) {
    List<Vector3> pts = samplePositions(Math.max(2, samplesPerSegment));
    int n = pts.size();
    if (n < 2) { lx = ly = lz = rx = ry = rz = null; triPairs = 0; ribbonDirty = false; return; }
    final float SENTINEL = Float.NEGATIVE_INFINITY;
    float halfW = Math.max(0.1f, width * 0.5f);
    lx = new float[n]; ly = new float[n]; lz = new float[n];
    rx = new float[n]; ry = new float[n]; rz = new float[n];
    for (int i = 0; i < n; i++) {
      Vector3 p = pts.get(i);
      float tx, tz;
      if (i == 0) { Vector3 p1 = pts.get(1); tx = p1.x - p.x; tz = p1.z - p.z; }
      else if (i == n - 1) { Vector3 pm = pts.get(n-2); tx = p.x - pm.x; tz = p.z - pm.z; }
      else { Vector3 pm = pts.get(i-1), p1 = pts.get(i+1); tx = (p1.x - pm.x)*0.5f; tz = (p1.z - pm.z)*0.5f; }
      float tlen = (float)Math.sqrt(tx*tx + tz*tz); if (tlen < 1e-4f){ tx=1; tz=0; tlen=1; }
      tx/=tlen; tz/=tlen; float nx = -tz, nz = tx;
      float lpx = p.x + nx*halfW, lpz = p.z + nz*halfW;
      float rpx = p.x - nx*halfW, rpz = p.z - nz*halfW;
      // Sample centerline height first; if terrain not ready, abort this rebuild and retry later
      float cY = terrain.heightAt(p.x, p.z, SENTINEL);
      if (cY == SENTINEL) { ribbonDirty = true; return; }
      lx[i] = lpx; lz[i] = lpz; ly[i] = terrain.heightAt(lpx, lpz, cY);
      rx[i] = rpx; rz[i] = rpz; ry[i] = terrain.heightAt(rpx, rpz, cY);
    }
    triPairs = n - 1; ribbonDirty = false;
  }

  // ---------------- Async rebuild support ----------------
  private static final java.util.concurrent.ExecutorService RIBBON_POOL = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "RibbonRebuild-Worker");
    t.setDaemon(true);
    try { t.setPriority(Thread.MIN_PRIORITY); } catch (Throwable ignored) {}
    return t;
  });

  public void rebuildRibbonAsync(final com.example.game.World.HeightSampler terrain, final int samplesPerSegment) {
    if (!ribbonDirty || ribbonRebuilding) return;
    // Throttle rebuilds to avoid overwhelming the renderer during drags
    long now = System.currentTimeMillis();
    if (now - lastRibbonRebuildMs < 120) return; // try again next frame
    lastRibbonRebuildMs = now;
    ribbonRebuilding = true;
    RIBBON_POOL.submit(() -> {
      try {
        // Compute locally, then publish atomically to fields
        java.util.List<Vector3> pts = samplePositions(Math.max(2, samplesPerSegment));
        int n = pts.size();
        float[] lxL, lyL, lzL, rxL, ryL, rzL; int triPairsL;
        if (n < 2) {
          lxL = lyL = lzL = rxL = ryL = rzL = null; triPairsL = 0;
        } else {
          float halfW = Math.max(0.1f, width * 0.5f);
          lxL = new float[n]; lyL = new float[n]; lzL = new float[n];
          rxL = new float[n]; ryL = new float[n]; rzL = new float[n];
          final float SENTINEL = Float.NEGATIVE_INFINITY;
          for (int i = 0; i < n; i++) {
            Vector3 p = pts.get(i);
            float tx, tz;
            if (i == 0) { Vector3 p1 = pts.get(1); tx = p1.x - p.x; tz = p1.z - p.z; }
            else if (i == n - 1) { Vector3 pm = pts.get(n-2); tx = p.x - pm.x; tz = p.z - pm.z; }
            else { Vector3 pm = pts.get(i-1), p1 = pts.get(i+1); tx = (p1.x - pm.x)*0.5f; tz = (p1.z - pm.z)*0.5f; }
            float tlen = (float)Math.sqrt(tx*tx + tz*tz); if (tlen < 1e-4f){ tx=1; tz=0; tlen=1; }
            tx/=tlen; tz/=tlen; float nx = -tz, nz = tx;
            float lpx = p.x + nx*halfW, lpz = p.z + nz*halfW;
            float rpx = p.x - nx*halfW, rpz = p.z - nz*halfW;
            float cY = terrain.heightAt(p.x, p.z, SENTINEL);
            if (cY == SENTINEL) { ribbonDirty = true; return; }
            lxL[i] = lpx; lzL[i] = lpz; lyL[i] = terrain.heightAt(lpx, lpz, cY);
            rxL[i] = rpx; rzL[i] = rpz; ryL[i] = terrain.heightAt(rpx, rpz, cY);
          }
          triPairsL = n - 1;
        }
        // Publish
        this.lx = lxL; this.ly = lyL; this.lz = lzL; this.rx = rxL; this.ry = ryL; this.rz = rzL; this.triPairs = triPairsL;
        this.ribbonDirty = false;
      } catch (Throwable ignored) {
        // Keep dirty so we can retry next time
      } finally {
        ribbonRebuilding = false;
      }
    });
  }

  /** Sample a Catmull-Rom (centripetal) spline on XZ; robust at endpoints; ignores Y for parameterization. */
  public List<Vector3> samplePositions(int samplesPerSegment) {
    ArrayList<Vector3> out = new ArrayList<>();
    if (nodes.isEmpty()) return out;
    if (nodes.size() == 1) { out.add(new Vector3(nodes.get(0).pos)); return out; }

    final int segs = nodes.size() - 1;
    int baseSpp = Math.max(2, samplesPerSegment);

    for (int i = 0; i < segs; i++) {
      Vector3 p0 = getClamped(i - 1);
      Vector3 p1 = nodes.get(i).pos;
      Vector3 p2 = nodes.get(i + 1).pos;
      Vector3 p3 = getClamped(i + 2);

      // Adaptive sampling density by XZ length (keeps spacing uniform in meters)
      float lenXZ = (float)Math.sqrt((p2.x - p1.x)*(p2.x - p1.x) + (p2.z - p1.z)*(p2.z - p1.z));
      int spp = Math.max(4, Math.round(baseSpp * Math.max(0.5f, lenXZ / 5f))); // ~5m reference

      // Include endpoint only on the last segment to avoid duplicate points between segments
      int steps = (i == segs - 1) ? spp : (spp - 1);
      for (int s = 0; s <= steps; s++) {
        float t = s / (float) spp; // 0..1 within [p1,p2]
        Vector3 v = hermiteCentripetalXZ(p0, p1, p2, p3, t, new Vector3());
        // Preserve provided Y by interpolating only XZ; keep Y from p1->p2 linear
        float y = p1.y + (p2.y - p1.y) * t;
        v.y = y;
        out.add(v);
      }
    }
    return out;
  }

  private Vector3 getClamped(int idx) {
    if (nodes.isEmpty()) return null;
    if (idx < 0) return nodes.get(0).pos;
    if (idx >= nodes.size()) return nodes.get(nodes.size()-1).pos;
    return nodes.get(idx).pos;
  }

  // Hermite interpolation with centripetal Catmull-Rom tangents on XZ (stable, minimal overshoot)
  private static Vector3 hermiteCentripetalXZ(Vector3 p0, Vector3 p1, Vector3 p2, Vector3 p3, float s, Vector3 out) {
    final double alpha = 0.5; // centripetal
    final double EPS = 1e-6;

    double t0 = 0.0;
    double t1 = tjXZ(t0, p0, p1, alpha);
    double t2 = tjXZ(t1, p1, p2, alpha);
    double t3 = tjXZ(t2, p2, p3, alpha);

    // Guard degenerate parameterization
    if (Math.abs(t1 - t0) < EPS || Math.abs(t2 - t1) < EPS || Math.abs(t3 - t2) < EPS) {
      return lerpXZ(p1, p2, s, out);
    }

    // Tangents at p1 and p2 (XZ only), scaled to param interval [t1,t2]
    float m1x = (float)((p2.x - p0.x) * ((t2 - t1) / (t2 - t0)));
    float m1z = (float)((p2.z - p0.z) * ((t2 - t1) / (t2 - t0)));
    float m2x = (float)((p3.x - p1.x) * ((t2 - t1) / (t3 - t1)));
    float m2z = (float)((p3.z - p1.z) * ((t2 - t1) / (t3 - t1)));

    float s2 = s * s;
    float s3 = s2 * s;
    float h00 =  2*s3 - 3*s2 + 1;
    float h10 =      s3 - 2*s2 + s;
    float h01 = -2*s3 + 3*s2;
    float h11 =      s3 -   s2;

    float x = h00 * p1.x + h10 * m1x + h01 * p2.x + h11 * m2x;
    float z = h00 * p1.z + h10 * m1z + h01 * p2.z + h11 * m2z;
    out.set(x, p1.y, z);
    return out;
  }

  private static double tjXZ(double ti, Vector3 pi, Vector3 pj, double alpha) {
    double dx = pj.x - pi.x, dz = pj.z - pi.z;
    double dist = Math.sqrt(dx*dx + dz*dz);
    return ti + Math.pow(dist, alpha);
  }

  private static Vector3 lerp(Vector3 a, Vector3 b, float t) { return new Vector3(a).lerp(b, t); }
  private static Vector3 lerpXZ(Vector3 a, Vector3 b, float t, Vector3 out) {
    float x = a.x + (b.x - a.x) * t;
    float z = a.z + (b.z - a.z) * t;
    out.set(x, a.y, z);
    return out;
  }
  private static double lerp(double a, double b, float t) { return a + (b-a)*t; }
}
