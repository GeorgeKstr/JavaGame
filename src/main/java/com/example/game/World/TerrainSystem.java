package com.example.game.World;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.example.game.Render.SimpleShader;

import java.util.Map;

/**
 * TerrainSystem (facade)
 * ----------------------
 * Thin coordinator that exposes the public API used by the editor/tools, while delegating
 * real work to the split classes:
 *
 *   - TerrainWorld     : shared state (chunks, textures, manifest refs, queues, config)
 *   - TerrainStreamer  : load/unload chunks around a focus point
 *   - TerrainEditor    : height/texture edits + dirty rebuilds
 *   - LodController    : LOD selection with hysteresis
 *   - TerrainRenderer  : GL draws and ground TextureArray bindings
 *   - TerrainIO        : manifest merge, encode, delta-save
 *   - TerrainPicking   : screen→world picking against terrain height
 *
 * This keeps the rest of your project calling the same methods as before.
 */
public class TerrainSystem implements HeightSampler {

  // ---- Modules ----
  final TerrainWorld world;
  private final TerrainStreamer streamer;
  private final TerrainEditor editor;
  private final TerrainRenderer renderer;
  private final TerrainIO io;
  private final TerrainPicking picking;

  public TerrainSystem(MapResource map) {
    this.world    = new TerrainWorld(map);
    this.streamer = new TerrainStreamer(world);
    this.editor   = new TerrainEditor(world);
    this.renderer = new TerrainRenderer(world);
    this.io       = new TerrainIO(world);
    this.picking  = new TerrainPicking(world);
  }

  // ---------------- Async in-place reload support ----------------
  private static final class ReloadResult {
    final int key; final int cx; final int cz; final boolean defaultFlat;
    final short[] heights; final short[] baseHeights;
    final byte[] id0, id1, id2, w0, w1, w2;
    ReloadResult(int key, int cx, int cz, boolean df, short[] h, short[] base, byte[] id0, byte[] id1, byte[] id2, byte[] w0, byte[] w1, byte[] w2) {
      this.key=key; this.cx=cx; this.cz=cz; this.defaultFlat=df; this.heights=h; this.baseHeights=base; this.id0=id0; this.id1=id1; this.id2=id2; this.w0=w0; this.w1=w1; this.w2=w2;
    }
  }
  private final java.util.concurrent.ConcurrentLinkedQueue<ReloadResult> reloadQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();
  private final java.util.concurrent.atomic.AtomicInteger reloadSubmitted = new java.util.concurrent.atomic.AtomicInteger(0);
  private final java.util.concurrent.atomic.AtomicInteger reloadApplied = new java.util.concurrent.atomic.AtomicInteger(0);

  /** Kick off background loads for all loaded chunks; call applyPendingReloads() on render thread to apply. */
  public void reloadAllChunksAsyncInPlace() {
    final int S = world.S;
    final float tile = world.tile;
    final float chunkWorldSize = (S - 1) * tile;
    reloadSubmitted.set(0); reloadApplied.set(0);
    // Submit a task per loaded chunk
    for (com.badlogic.gdx.utils.IntMap.Entry<TerrainChunk> e : world.chunks) {
      TerrainChunk c = e.value; if (c == null) continue;
      int cx = (int)Math.floor(c.getOriginX() / chunkWorldSize);
      int cz = (int)Math.floor(c.getOriginZ() / chunkWorldSize);
      int key = e.key;
      reloadSubmitted.incrementAndGet();
      world.pool.submit(() -> {
        try {
          byte[] hmx = world.map.readChunkHmx(cx, cz);
          final int samples = S * S;
          boolean defaultFlat = false;
          short[] h; byte[] id0, id1, id2, w0, w1, w2; short[] baseH;
          if (hmx == null) {
            defaultFlat = true;
            h = new short[samples];
            id0 = new byte[samples]; id1 = new byte[samples]; id2 = new byte[samples];
            w0  = new byte[samples]; w1  = new byte[samples]; w2  = new byte[samples];
            byte defId = (byte)(world.map.getManifest().defaultTexture & 0xFF);
            java.util.Arrays.fill(id0, defId);
            java.util.Arrays.fill(w0, (byte)255);
            baseH = java.util.Arrays.copyOf(h, h.length);
          } else {
            var lc = world.provider.loadFromBytes(hmx);
            if (lc instanceof SparseChunkProvider.LoadedChunk) {
              h   = ((SparseChunkProvider.LoadedChunk) lc).heights;
              id0 = ((SparseChunkProvider.LoadedChunk) lc).id0;
              id1 = ((SparseChunkProvider.LoadedChunk) lc).id1;
              id2 = ((SparseChunkProvider.LoadedChunk) lc).id2;
              w0  = ((SparseChunkProvider.LoadedChunk) lc).w0;
              w1  = ((SparseChunkProvider.LoadedChunk) lc).w1;
              w2  = ((SparseChunkProvider.LoadedChunk) lc).w2;
            } else {
              h = lc.heights;
              byte[] texIds = (byte[])reflectGet(lc, "texIds");
              id0 = new byte[samples]; id1 = new byte[samples]; id2 = new byte[samples];
              w0  = new byte[samples]; w1  = new byte[samples]; w2  = new byte[samples];
              System.arraycopy(texIds, 0, id0, 0, samples);
              java.util.Arrays.fill(w0, (byte)255);
            }
            baseH = java.util.Arrays.copyOf(h, h.length);
          }
          // Optional post-process in background (thread-safe implementations only)
          if (world.chunkPost != null) {
            float ox = cx * chunkWorldSize, oz = cz * chunkWorldSize;
            try { world.chunkPost.processChunk(cx, cz, ox, oz, h, baseH, S, tile, world.yScale); } catch (Throwable ignored) {}
          }
          reloadQueue.add(new ReloadResult(key, cx, cz, defaultFlat, h, baseH, id0, id1, id2, w0, w1, w2));
        } catch (Throwable t) {
          // Skip on error; still count as applied to not stall
          reloadApplied.incrementAndGet();
        }
      });
    }
  }

  /** Apply up to maxPerFrame pending reloads to existing chunks (render thread). Returns applied count. */
  public int applyPendingReloads(int maxPerFrame) {
    int applied = 0;
    final int S = world.S;
    while (applied < Math.max(1, maxPerFrame)) {
      ReloadResult r = reloadQueue.poll();
      if (r == null) break;
      TerrainChunk c = world.chunks.get(r.key);
      if (c != null) {
        // Copy arrays into existing chunk
        short[] ch = c.getHeightsArray(); if (ch != null && ch.length == r.heights.length) { System.arraycopy(r.heights, 0, ch, 0, ch.length); c.setDirty(true);} else { c.setHeights(r.heights); }
        byte[] a0 = c.getTexId0Array();  if (a0 != null && a0.length == r.id0.length) { System.arraycopy(r.id0, 0, a0, 0, a0.length); c.setDirty(true);} else { c.setTexIds(r.id0); }
        byte[] a1 = c.getTexId1Array();  if (a1 != null && a1.length == r.id1.length) { System.arraycopy(r.id1, 0, a1, 0, a1.length); c.setDirty(true);} else { c.setTexId1Array(r.id1); }
        byte[] a2 = c.getTexId2Array();  if (a2 != null && a2.length == r.id2.length) { System.arraycopy(r.id2, 0, a2, 0, a2.length); c.setDirty(true);} else { c.setTexId2Array(r.id2); }
        byte[] wA = c.getTexW0Array();   if (wA != null && wA.length == r.w0.length) { System.arraycopy(r.w0, 0, wA, 0, wA.length); c.setDirty(true);} else { c.setTexW0Array(r.w0); }
        byte[] wB = c.getTexW1Array();   if (wB != null && wB.length == r.w1.length) { System.arraycopy(r.w1, 0, wB, 0, wB.length); c.setDirty(true);} else { c.setTexW1Array(r.w1); }
        byte[] wC = c.getTexW2Array();   if (wC != null && wC.length == r.w2.length) { System.arraycopy(r.w2, 0, wC, 0, wC.length); c.setDirty(true);} else { c.setTexW2Array(r.w2); }
        c.setBaselineHeights(r.baseHeights);
        c.buildLods(world.MAX_LOD);
        if (r.defaultFlat) world.defaultFlatChunks.add(r.key); else world.defaultFlatChunks.remove(r.key);
      }
      applied++;
      reloadApplied.incrementAndGet();
    }
    // Notify listeners if we applied any this frame
    if (applied > 0 && world.onChunksChanged != null) try { world.onChunksChanged.run(); } catch (Throwable ignored) {}
    return applied;
  }

  /** Optional: expose approximate progress 0..1 for UI during async reload. */
  public float getReloadProgress01() {
    int sub = Math.max(1, reloadSubmitted.get());
    int app = Math.min(sub, reloadApplied.get());
    return app / (float)sub;
  }

  // ---------------- Non-destructive height modifiers (stacking) ----------------
  /** Height modifier that samples MountainStamp supplier (live) and returns additive delta. */
  public static class MountainHeightModifier implements HeightModifier {
    private final java.util.function.Supplier<java.util.List<MountainStamp>> supplier;
    public MountainHeightModifier(java.util.function.Supplier<java.util.List<MountainStamp>> supplier){ this.supplier = supplier; }
    @Override public float deltaAt(float x, float z) {
      java.util.List<MountainStamp> list = supplier.get();
      if (list == null || list.isEmpty()) return 0f;
      float total = 0f;
      for (MountainStamp s : list) {
        if (s == null || s.heightmap == null) continue;
        float cx = s.centerX, cz = s.centerZ;
        float sin = (float)Math.sin(Math.toRadians(s.rotationDeg));
        float cos = (float)Math.cos(Math.toRadians(s.rotationDeg));
        float lx = x - cx, lz = z - cz;
        float rx =  lx * cos + lz * sin;
        float rz = -lx * sin + lz * cos;
        float ax = Math.abs(rx) / Math.max(1e-6f, s.halfW);
        float az = Math.abs(rz) / Math.max(1e-6f, s.halfH);
        if (ax > 1f || az > 1f) continue;
        float u = (rx * 0.5f / s.halfW) + 0.5f;
        float v = (rz * 0.5f / s.halfH) + 0.5f;
        float lum = sampleLumBilinear(s.heightmap, u, v);
        float ff = clamp01(s.edgeFalloff);
        // Superellipse metric for rounder ramps (p≈2.5)
        float p = 2.5f;
        float t = (float)Math.pow(Math.pow(ax, p) + Math.pow(az, p), 1.0 / p);
        float bandStart = 1f - ff; // portion preserved fully; ff=0 -> 1, ff=1 -> 0
        float denom = Math.max(1e-6f, 1f - bandStart);
        float tEdge = clamp01((t - bandStart) / denom); // 0 inside preserved area, 1 at edge
        float shapedEdge = 1f - smoothstep01(tEdge); // 1 inside, smoothly to 0 at edges
        float sign = s.inverted ? -1f : 1f;
        total += sign * s.intensity * lum * shapedEdge;
      }
      return total;
    }
  }

  /** Compose two modifiers by summing their deltas. */
  public static class CombinedHeightModifier implements HeightModifier {
    private final HeightModifier a, b;
    public CombinedHeightModifier(HeightModifier a, HeightModifier b){ this.a=a; this.b=b; }
    @Override public float deltaAt(float x, float z) { float da = (a!=null?a.deltaAt(x,z):0f); float db=(b!=null?b.deltaAt(x,z):0f); return da+db; }
  }

  /** Install an additional modifier by composing with the current one. */
  public void addHeightModifier(HeightModifier extra) {
    if (extra == null) return;
    HeightModifier cur = world.heightModifier;
    world.heightModifier = (cur == null) ? extra : new CombinedHeightModifier(cur, extra);
    // mark all loaded chunks dirty so meshes rebuild with modifier-applied heights
    for (com.badlogic.gdx.utils.IntMap.Entry<TerrainChunk> e : world.chunks) e.value.setDirty(true);
  }

  // ---------------- Concrete baker: Mountain heightmap stamps ----------------
  /** POJO describing a rectangular heightmap stamp in world XZ at y=0. */
  public static class MountainStamp {
    public com.badlogic.gdx.graphics.Pixmap heightmap; // grayscale or RGB; only luminance used
    public float centerX, centerZ;   // world center position (y=0)
    public float halfW, halfH;       // half-size in meters along local axes
    public float rotationDeg;        // rotation about +Y, degrees
    public float intensity;          // meters of max raise when heightmap=1
    public float edgeFalloff;        // 0..1 controls softness near edges
    public boolean inverted;         // if true, apply as depth (subtract)
  }

  private static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }
  private static float smoothstep01(float x){ float t = clamp01(x); return t*t*(3f - 2f*t); }
  private static float lerp(float a, float b, float t){ return a + (b - a) * t; }

  // Bilinear sample of grayscale luminance from a Pixmap at normalized UV in [0..1].
  private static float sampleLumBilinear(com.badlogic.gdx.graphics.Pixmap pm, float u, float v) {
    if (pm == null) return 0f;
    int iw = pm.getWidth();
    int ih = pm.getHeight();
    if (iw <= 0 || ih <= 0) return 0f;
    float fx = Math.max(0f, Math.min(iw - 1f, u * (iw - 1)));
    float fy = Math.max(0f, Math.min(ih - 1f, v * (ih - 1)));
    int x0 = (int)Math.floor(fx), x1 = Math.min(iw - 1, x0 + 1);
    int y0 = (int)Math.floor(fy), y1 = Math.min(ih - 1, y0 + 1);
    float tx = fx - x0, ty = fy - y0;
    int p00 = pm.getPixel(x0, y0);
    int p10 = pm.getPixel(x1, y0);
    int p01 = pm.getPixel(x0, y1);
    int p11 = pm.getPixel(x1, y1);
    float r00 = ((p00 >>> 24) & 0xff) / 255f; float g00 = ((p00 >>> 16) & 0xff) / 255f; float b00 = ((p00 >>> 8) & 0xff) / 255f;
    float r10 = ((p10 >>> 24) & 0xff) / 255f; float g10 = ((p10 >>> 16) & 0xff) / 255f; float b10 = ((p10 >>> 8) & 0xff) / 255f;
    float r01 = ((p01 >>> 24) & 0xff) / 255f; float g01 = ((p01 >>> 16) & 0xff) / 255f; float b01 = ((p01 >>> 8) & 0xff) / 255f;
    float r11 = ((p11 >>> 24) & 0xff) / 255f; float g11 = ((p11 >>> 16) & 0xff) / 255f; float b11 = ((p11 >>> 8) & 0xff) / 255f;
    float l00 = (r00 + g00 + b00) / 3f;
    float l10 = (r10 + g10 + b10) / 3f;
    float l01 = (r01 + g01 + b01) / 3f;
    float l11 = (r11 + g11 + b11) / 3f;
    float lx0 = l00 + (l10 - l00) * tx;
    float lx1 = l01 + (l11 - l01) * tx;
    return lx0 + (lx1 - lx0) * ty;
  }

  /** Set a callback invoked on the render thread after chunks have changed (load/unload). */
  public void onChunksChanged(Runnable r) { world.onChunksChanged = r; }

  /** Add a listener invoked on the render thread after chunks change. Multiple listeners are supported by chaining. */
  public void addChunksChangedListener(Runnable r) {
    if (r == null) return;
    Runnable prev = world.onChunksChanged;
    if (prev == null) {
      world.onChunksChanged = r;
    } else {
      world.onChunksChanged = () -> { try { prev.run(); } catch (Throwable ignored) {} try { r.run(); } catch (Throwable ignored) {} };
    }
  }

  /** Snapshot of currently loaded chunk keys (cx,cz) packed via TerrainWorld.key(). */
  public java.util.Set<Integer> getLoadedChunkKeysCopy() {
    java.util.HashSet<Integer> set = new java.util.HashSet<>();
    for (com.badlogic.gdx.utils.IntMap.Entry<TerrainChunk> e : world.chunks) set.add(e.key);
    return set;
  }

  /** Query if chunk at (cx,cz) is currently loaded. */
  public boolean isChunkLoaded(int cx, int cz) {
    int k = TerrainWorld.key(cx, cz);
    return world.chunks.containsKey(k);
  }

  // ---------------- Concrete post-processor: River baker ----------------
  /** Thread-safe snapshot baker that stamps river depths into chunk height arrays. */
  public static class RiverBakePostProcessor implements ChunkHeightPostProcessor {
    // Either use a live provider or a fixed snapshot list
    private final java.util.function.Supplier<java.util.List<com.example.game.World.Splines.TerrainSpline>> provider;
    private final java.util.List<com.example.game.World.Splines.TerrainSpline> rivers;
    private final int samplesPerSegment;

    // Snapshot list constructor (back-compat)
    public RiverBakePostProcessor(java.util.List<com.example.game.World.Splines.TerrainSpline> source, int samplesPerSegment) {
      this.provider = null;
      this.rivers = new java.util.ArrayList<>(source != null ? source : java.util.Collections.emptyList());
      this.samplesPerSegment = Math.max(2, samplesPerSegment);
    }

    // Live provider constructor (preferred)
    public RiverBakePostProcessor(java.util.function.Supplier<java.util.List<com.example.game.World.Splines.TerrainSpline>> provider, int samplesPerSegment) {
      this.provider = provider;
      this.rivers = null;
      this.samplesPerSegment = Math.max(2, samplesPerSegment);
    }

    @Override
    public void processChunk(int cx, int cz, float originX, float originZ,
                             short[] heights, short[] baseHeights,
                             int S, float tile, float yScale) {
      if (heights == null) return;
      // Snapshot incoming heights so river depth is computed relative to the state before this processor
      // (prevents over-accumulating depth from multiple samples/overlaps)
      short[] startHeights = heights.clone();
      final int cellsPerChunk = S - 1;
      final float chunkSize = cellsPerChunk * tile;

      // Precompute chunk AABB in world XZ
      final float minX = originX, minZ = originZ;
      final float maxX = originX + chunkSize, maxZ = originZ + chunkSize;

      java.util.List<com.example.game.World.Splines.TerrainSpline> src = (provider != null ? provider.get() : rivers);
      if (src == null || src.isEmpty()) return;

      for (com.example.game.World.Splines.TerrainSpline s : src) {
        if (s == null) continue;
        com.example.game.World.Splines.TerrainSpline.Type ty = s.getType();
        if (ty != com.example.game.World.Splines.TerrainSpline.Type.RIVER && ty != com.example.game.World.Splines.TerrainSpline.Type.ROAD) continue;
        if (s.riverDepth <= 0f || s.width <= 0.01f) continue;

        java.util.List<com.badlogic.gdx.math.Vector3> pts = s.samplePositions(samplesPerSegment);
        if (pts == null || pts.size() < 2) continue;

        int radiusCells = Math.max(1, Math.round((s.width * 0.5f) / tile));

        // Capsule-like coverage: step along each segment with spacing ~tile*0.5 to avoid gaps
        for (int i = 0; i < pts.size() - 1; i++) {
          com.badlogic.gdx.math.Vector3 a = pts.get(i);
          com.badlogic.gdx.math.Vector3 b = pts.get(i + 1);
          float dx = b.x - a.x, dz = b.z - a.z;
          float len = (float)Math.sqrt(dx*dx + dz*dz);
          int steps = Math.max(1, Math.round(len / (tile * 0.5f)));
          for (int u = 0; u <= steps; u++) {
            float tlin = (steps == 0) ? 0f : (u / (float)steps);
            float px = a.x + dx * tlin;
            float pz = a.z + dz * tlin;
            // Quick reject if outside chunk AABB expanded by radius
            float rMeters = radiusCells * tile;
            if (px < minX - rMeters || px > maxX + rMeters || pz < minZ - rMeters || pz > maxZ + rMeters)
              continue;
            int gx = Math.round((px - originX) / tile);
            int gz = Math.round((pz - originZ) / tile);
            stampCircle(heights, startHeights, S, gx, gz, radiusCells, s.riverDepth, s.bankFalloff, yScale);
          }
        }
      }
    }

    private static void stampCircle(short[] h, short[] start, int S, int cx, int cz, int radCells, float depthMeters, float falloff01, float yScale) {
      if (h == null) return;
      int r = Math.max(1, radCells);
      int x0 = Math.max(0, cx - r), x1 = Math.min(S - 1, cx + r);
      int z0 = Math.max(0, cz - r), z1 = Math.min(S - 1, cz + r);
      float rInv = 1f / (r + 1e-6f);
      // Smooth profile using smoothstep; blend towards sharper exponential with falloff
      float ff = Math.max(0f, Math.min(1f, falloff01));
      float exp = 1.5f + 3.5f * ff; // 1.5..5.0
      float units = depthMeters / Math.max(1e-6f, yScale);
      for (int z = z0; z <= z1; z++) {
        int dz = z - cz;
        for (int x = x0; x <= x1; x++) {
          int dx = x - cx;
          float d = (float)Math.sqrt(dx*dx + dz*dz);
          if (d > r) continue;
          float t = d * rInv; // 0 center .. 1 at radius
          float s = Math.max(0f, Math.min(1f, t));
          float smooth = s*s*(3f - 2f*s); // smoothstep
          float profBase = 1f - smooth;   // gentle center-to-edge falloff
          float profSharp = (float)Math.pow(1f - s, exp);
          float prof = profBase * (1f - ff) + profSharp * ff;
          int idx = z * S + x;
          int delta = Math.round(units * prof);
          int vCandidate = (start != null && idx < start.length ? start[idx] : h[idx]) - delta;
          // combine with any previous stamps for this processor by taking min against current h
          int v = Math.min(h[idx], vCandidate);
          if (v < Short.MIN_VALUE) v = Short.MIN_VALUE; else if (v > Short.MAX_VALUE) v = Short.MAX_VALUE;
          h[idx] = (short)v;
        }
      }
    }
  }

  /** Convenience: install a river bake post-processor computed from current rivers. */
  public void installRiverBake(java.util.List<com.example.game.World.Splines.TerrainSpline> rivers, int samplesPerSegment) {
    setChunkHeightPostProcessor(new RiverBakePostProcessor(rivers, samplesPerSegment));
  }

  /** Install a live river bake post-processor that reads rivers from a Supplier at chunk load time. */
  public void installRiverBakeLive(java.util.function.Supplier<java.util.List<com.example.game.World.Splines.TerrainSpline>> riversSupplier,
                                   int samplesPerSegment) {
    setChunkHeightPostProcessor(new RiverBakePostProcessor(riversSupplier, samplesPerSegment));
  }

  // ---------------- Hook: background chunk height post-processor (e.g., rivers bake) ----------------
  /**
   * Post-process freshly loaded chunk heights on the background streaming threads.
   * Implementations must be THREAD-SAFE and not touch GL.
   */
  public interface ChunkHeightPostProcessor {
    void processChunk(int cx, int cz, float originX, float originZ,
                      short[] heights, short[] baseHeights,
                      int S, float tile, float yScale);
  }

  /** Install/uninstall a chunk height post-processor. Pass null to disable. */
  public void setChunkHeightPostProcessor(ChunkHeightPostProcessor pp) { world.chunkPost = pp; }

  /** Compose two chunk post-processors (apply both in sequence). */
  public static class CombinedChunkPost implements ChunkHeightPostProcessor {
    private final ChunkHeightPostProcessor a, b;
    public CombinedChunkPost(ChunkHeightPostProcessor a, ChunkHeightPostProcessor b){ this.a=a; this.b=b; }
    @Override public void processChunk(int cx, int cz, float originX, float originZ, short[] heights, short[] baseHeights, int S, float tile, float yScale) {
      if (a != null) a.processChunk(cx, cz, originX, originZ, heights, baseHeights, S, tile, yScale);
      if (b != null) b.processChunk(cx, cz, originX, originZ, heights, baseHeights, S, tile, yScale);
    }
  }

  /** Add a chunk post-processor composed after any existing one. */
  public void addChunkHeightPostProcessor(ChunkHeightPostProcessor extra) {
    if (extra == null) return;
    world.chunkPost = (world.chunkPost == null) ? extra : new CombinedChunkPost(world.chunkPost, extra);
  }

  /** Live bake post-processor for mountains (applied when chunks (re)load). */
  public static class MountainBakePostProcessor implements ChunkHeightPostProcessor {
    private final java.util.function.Supplier<java.util.List<MountainStamp>> supplier;
    public MountainBakePostProcessor(java.util.function.Supplier<java.util.List<MountainStamp>> supplier){ this.supplier = supplier; }
    @Override public void processChunk(int cx, int cz, float originX, float originZ, short[] heights, short[] baseHeights, int S, float tile, float yScale) {
      java.util.List<MountainStamp> stamps = supplier.get();
      if (stamps == null || stamps.isEmpty() || heights == null || baseHeights == null) return;
      final int cellsPerChunk = S - 1;
      float minX = originX, minZ = originZ;
      float maxX = originX + cellsPerChunk * tile, maxZ = originZ + cellsPerChunk * tile;
      for (MountainStamp s : stamps) {
        if (s == null || s.heightmap == null) continue;
        float cxw = s.centerX, czw = s.centerZ;
        float radius = (float)Math.hypot(s.halfW, s.halfH);
        if (cxw + radius < minX || cxw - radius > maxX || czw + radius < minZ || czw - radius > maxZ) continue;
        // Precompute rotation
        float sin = (float)Math.sin(Math.toRadians(s.rotationDeg));
        float cos = (float)Math.cos(Math.toRadians(s.rotationDeg));
        float invHalfW = 1f / Math.max(1e-6f, s.halfW);
        float invHalfH = 1f / Math.max(1e-6f, s.halfH);
        // Iterate grid cells in overlapping region
        int x0 = Math.max(0, (int)Math.floor((cxw - radius - originX) / tile));
        int x1 = Math.min(S - 1, (int)Math.ceil((cxw + radius - originX) / tile));
        int z0 = Math.max(0, (int)Math.floor((czw - radius - originZ) / tile));
        int z1 = Math.min(S - 1, (int)Math.ceil((czw + radius - originZ) / tile));
        for (int gz = z0; gz <= z1; gz++) {
          float wz = originZ + gz * tile;
          for (int gx = x0; gx <= x1; gx++) {
            float wx = originX + gx * tile;
            float deltaMetersAvg = 0f;
            for (int sy = 0; sy < 4; sy++) {
              for (int sx = 0; sx < 4; sx++) {
                float fx = (sx + 0.5f) / 4f;
                float fz = (sy + 0.5f) / 4f;
                float px = wx + fx * tile;
                float pz = wz + fz * tile;
                float lx = px - cxw, lz = pz - czw;
                float rx =  lx * cos + lz * sin;
                float rz = -lx * sin + lz * cos;
                float ax = Math.abs(rx) * invHalfW;
                float az = Math.abs(rz) * invHalfH;
                if (ax > 1f || az > 1f) continue;
                float u = (rx * 0.5f / s.halfW) + 0.5f;
                float v = (rz * 0.5f / s.halfH) + 0.5f;
                float lum = sampleLumBilinear(s.heightmap, u, v);
                float ff = clamp01(s.edgeFalloff);
                // Superellipse metric for rounder ramps (p≈2.5)
                float p = 2.5f;
                float t = (float)Math.pow(Math.pow(ax, p) + Math.pow(az, p), 1.0 / p);
                float bandStart = 1f - ff;
                float denom = Math.max(1e-6f, 1f - bandStart);
                float s2 = clamp01((t - bandStart) / denom);
                float shapedEdge = 1f - smoothstep01(s2);
                float sign = s.inverted ? -1f : 1f;
                deltaMetersAvg += sign * s.intensity * lum * shapedEdge;
              }
            }
            deltaMetersAvg *= (1f / 16f);
            int idx = gz * S + gx;
            int deltaUnits = Math.round(deltaMetersAvg / Math.max(1e-6f, yScale));
            int base = baseHeights[idx];
            // Preserve any prior processor adjustments already in heights[] regardless of order
            int preserved = heights[idx] - base; // e.g., river depth or other post effects
            int nv = base + preserved + deltaUnits; // stack mountain over prior deltas
            if (nv < Short.MIN_VALUE) nv = Short.MIN_VALUE; else if (nv > Short.MAX_VALUE) nv = Short.MAX_VALUE;
            heights[idx] = (short)nv;
          }
        }
      }
    }
  }

  /** Convenience: unload all current chunks; streamer will reload on next update. */
  public void reloadAllChunks() {
    // Reload heights/textures for all currently loaded chunks IN-PLACE to avoid GC
    // Assumes render thread (no GL calls besides mesh rebuilds below)
    final int S = world.S;
    final float tile = world.tile;
    final float chunkWorldSize = (S - 1) * tile;

    for (com.badlogic.gdx.utils.IntMap.Entry<TerrainChunk> e : world.chunks) {
      TerrainChunk c = e.value;
      if (c == null) continue;
      int cx = (int)Math.floor(c.getOriginX() / chunkWorldSize);
      int cz = (int)Math.floor(c.getOriginZ() / chunkWorldSize);

      byte[] hmx = world.map.readChunkHmx(cx, cz);
      final int samples = S * S;

      boolean defaultFlat = false;
      short[] h;
      byte[] id0, id1, id2, w0, w1, w2;
      short[] baseH;

      if (hmx == null) {
        // Default flat chunk
        defaultFlat = true;
        h = new short[samples];
        id0 = new byte[samples]; id1 = new byte[samples]; id2 = new byte[samples];
        w0  = new byte[samples]; w1  = new byte[samples]; w2  = new byte[samples];
        byte defId = (byte)(world.map.getManifest().defaultTexture & 0xFF);
        java.util.Arrays.fill(id0, defId);
        java.util.Arrays.fill(w0, (byte)255);
        baseH = java.util.Arrays.copyOf(h, h.length);
      } else {
        var lc = world.provider.loadFromBytes(hmx);
        if (lc instanceof SparseChunkProvider.LoadedChunk) {
          h   = ((SparseChunkProvider.LoadedChunk) lc).heights;
          id0 = ((SparseChunkProvider.LoadedChunk) lc).id0;
          id1 = ((SparseChunkProvider.LoadedChunk) lc).id1;
          id2 = ((SparseChunkProvider.LoadedChunk) lc).id2;
          w0  = ((SparseChunkProvider.LoadedChunk) lc).w0;
          w1  = ((SparseChunkProvider.LoadedChunk) lc).w1;
          w2  = ((SparseChunkProvider.LoadedChunk) lc).w2;
        } else {
          // Legacy fallback: texIds only
          h = lc.heights;
          byte[] texIds = (byte[])reflectGet(lc, "texIds");
          id0 = new byte[samples]; id1 = new byte[samples]; id2 = new byte[samples];
          w0  = new byte[samples]; w1  = new byte[samples]; w2  = new byte[samples];
          System.arraycopy(texIds, 0, id0, 0, samples);
          java.util.Arrays.fill(w0, (byte)255);
        }
        baseH = java.util.Arrays.copyOf(h, h.length);
      }

      // Optional background-like post-process executed synchronously here
      if (world.chunkPost != null) {
        float ox = cx * chunkWorldSize;
        float oz = cz * chunkWorldSize;
        try { world.chunkPost.processChunk(cx, cz, ox, oz, h, baseH, S, tile, world.yScale); } catch (Throwable ignored) {}
      }

      // Update existing arrays in-place to avoid allocating new TerrainChunk objects
      short[] ch = c.getHeightsArray();
      if (ch != null && ch.length == h.length) {
        System.arraycopy(h, 0, ch, 0, h.length);
        c.setDirty(true);
      } else {
        c.setHeights(h); // fallback if sizes differ
      }

      byte[] a0 = c.getTexId0Array();
      if (a0 != null && a0.length == id0.length) System.arraycopy(id0, 0, a0, 0, id0.length); else c.setTexIds(id0);
      byte[] a1 = c.getTexId1Array(); if (a1 != null && a1.length == id1.length) { System.arraycopy(id1, 0, a1, 0, id1.length); c.setDirty(true);} else c.setTexId1Array(id1);
      byte[] a2 = c.getTexId2Array(); if (a2 != null && a2.length == id2.length) { System.arraycopy(id2, 0, a2, 0, id2.length); c.setDirty(true);} else c.setTexId2Array(id2);
      byte[] wA = c.getTexW0Array();  if (wA != null && wA.length == w0.length) { System.arraycopy(w0, 0, wA, 0, w0.length); c.setDirty(true);} else c.setTexW0Array(w0);
      byte[] wB = c.getTexW1Array();  if (wB != null && wB.length == w1.length) { System.arraycopy(w1, 0, wB, 0, w1.length); c.setDirty(true);} else c.setTexW1Array(w1);
      byte[] wC = c.getTexW2Array();  if (wC != null && wC.length == w2.length) { System.arraycopy(w2, 0, wC, 0, w2.length); c.setDirty(true);} else c.setTexW2Array(w2);

      // Baseline for ribbons/tools
      c.setBaselineHeights(baseH);

      // Rebuild LODs for updated arrays
      c.buildLods(world.MAX_LOD);

      // Track default flat membership
      if (defaultFlat) world.defaultFlatChunks.add(e.key); else world.defaultFlatChunks.remove(e.key);
    }

    // Notify listeners once after bulk reload
    if (world.onChunksChanged != null) try { world.onChunksChanged.run(); } catch (Throwable ignored) {}
  }

  // Small local helper to access legacy provider fields without depending on TerrainStreamer internals
  private static Object reflectGet(Object o, String field) {
    try {
      java.lang.reflect.Field f = o.getClass().getDeclaredField(field);
      f.setAccessible(true);
      return f.get(o);
    } catch (Throwable t) {
      return null;
    }
  }

  /** Desktop file chooser that imports one or more textures into a specific subfolder under textures/ground/. */
  public int[] importGroundTexturesDesktopInto(String subfolder, float uvScale) {
    if (Gdx.app.getType() != com.badlogic.gdx.Application.ApplicationType.Desktop) return new int[0];

    final java.util.List<String> paths = new java.util.ArrayList<>();
    try {
      javax.swing.SwingUtilities.invokeAndWait(() -> {
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        fc.setDialogTitle("Import Textures");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Images", "png", "jpg", "jpeg", "bmp"));
        fc.setMultiSelectionEnabled(true);
        try {
          com.badlogic.gdx.Preferences prefs = (Gdx.app != null ? Gdx.app.getPreferences("editor_prefs") : null);
          String lastDir = (prefs != null ? prefs.getString("lastTextureDir", null) : null);
          if (lastDir != null) fc.setCurrentDirectory(new java.io.File(lastDir));
        } catch (Throwable ignored) {}
        int res = fc.showOpenDialog(null);
        if (res == javax.swing.JFileChooser.APPROVE_OPTION) {
          java.io.File[] sel = fc.getSelectedFiles();
          if (sel != null && sel.length > 0) {
            for (java.io.File f : sel) if (f != null) paths.add(f.getAbsolutePath());
            try {
              com.badlogic.gdx.Preferences prefs = (Gdx.app != null ? Gdx.app.getPreferences("editor_prefs") : null);
              if (prefs != null && sel[0] != null && sel[0].getParent() != null) { prefs.putString("lastTextureDir", sel[0].getParent()); prefs.flush(); }
            } catch (Throwable ignored) {}
          } else if (fc.getSelectedFile() != null) {
            java.io.File f = fc.getSelectedFile();
            paths.add(f.getAbsolutePath());
            try {
              com.badlogic.gdx.Preferences prefs = (Gdx.app != null ? Gdx.app.getPreferences("editor_prefs") : null);
              if (prefs != null && f.getParent() != null) { prefs.putString("lastTextureDir", f.getParent()); prefs.flush(); }
            } catch (Throwable ignored) {}
          }
        }
      });
    } catch (Exception ignored) {}

    if (paths.isEmpty()) return new int[0];

    java.util.ArrayList<Integer> ids = new java.util.ArrayList<>();
    for (String p : paths) {
      var fh = Gdx.files.absolute(p);
      if (fh == null || !fh.exists()) continue;
      byte[] data = fh.readBytes();
      MapResource.TextureRef tr = world.map.importGroundTextureIntoFolder(fh.name(), data, subfolder);
      tr.uvScale = uvScale;
      ids.add(tr.id);
    }
    // Caller (e.g., TextureBrowser) must reload GL resources on the render thread after import.
    int[] arr = new int[ids.size()]; for (int i=0;i<ids.size();i++) arr[i]=ids.get(i);
    return arr;
  }

  /** Back-compat single import that uses the multi importer and returns the first id, or -1 if none. */
  public int importGroundTextureDesktopInto(String subfolder, float uvScale) {
    int[] ids = importGroundTexturesDesktopInto(subfolder, uvScale);
    return (ids.length > 0 ? ids[0] : -1);
  }

  // ----------------------------------------------------------------------------
  // Streaming / Update
  // ----------------------------------------------------------------------------
  /** Load/unload chunks around the given world XZ focus (call each frame or on movement). */
  public void updateAndStream(float worldX, float worldZ) { streamer.updateAndStream(worldX, worldZ); }

  /** Set circular view radius in CHUNKS (not meters). */
  public void setViewRadius(int r) { world.viewRadius = Math.max(0, r); }

  // ----------------------------------------------------------------------------
  // Rendering
  // ----------------------------------------------------------------------------
  public void render(SimpleShader shader, Camera cam) { renderer.render(shader, cam); }

  // Rendering toggles
  public void setWireframe(boolean on) { renderer.setWireframe(on); }
  public void setChunkGrid(boolean on) { renderer.setDrawGrid(on); }


  /** Paint a texture id into a circular region (byte id, 0..255). */

  /** Rebuild meshes/LODs for any chunks that were marked dirty after edits. */
  public void rebuildDirtyChunks() { editor.rebuildDirtyChunks(); }

  // ----------------------------------------------------------------------------
  // Textures (UI helpers + runtime refresh)
  // ----------------------------------------------------------------------------
  /** Human-friendly list of ground texture names (manifest order == layer id). */
  public String[] listGroundTextureNames() {
    var mf = world.map.getManifest();
    if (mf.groundTextures == null || mf.groundTextures.isEmpty()) return new String[0];

    String[] out = new String[mf.groundTextures.size()];
    for (int i = 0; i < out.length; i++) {
      MapResource.TextureRef tr = mf.groundTextures.get(i);
      String label = (tr.name != null && !tr.name.isEmpty()) ? tr.name : tr.file;
      int slash = Math.max(label.lastIndexOf('/'), label.lastIndexOf('\\'));
      out[i] = (slash >= 0) ? label.substring(slash + 1) : label;
    }
    return out;
  }

  /** Rebuild the TextureArray from the current manifest (staged-or-zip), and refresh UV scales. */
  public void reloadGroundTextures() {
    if (world.groundTA != null) { world.groundTA.dispose(); world.groundTA = null; }
    var mf = world.map.getManifest();
    if (mf.groundTextures == null || mf.groundTextures.isEmpty()) return;

    world.groundTA = world.map.buildGroundTextureArray();
    java.util.Arrays.fill(world.uvScales, 1f);
    for (MapResource.TextureRef tr : mf.groundTextures) {
      if (tr.id >= 0 && tr.id < world.uvScales.length) {
        world.uvScales[tr.id] = (tr.uvScale == 0f ? 1f : tr.uvScale);
      }
    }
  }

  /**
   * Desktop file chooser → MapResource.importGroundTexture(...) → rebuild TA.
   * Returns new texture id or -1 if canceled/not desktop.
   */
  public int importGroundTextureDesktop(float uvScale) {
    if (Gdx.app.getType() != com.badlogic.gdx.Application.ApplicationType.Desktop) return -1;

    final String[] abs = { null };
    try {
      javax.swing.SwingUtilities.invokeAndWait(() -> {
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        fc.setDialogTitle("Import Texture");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Images", "png", "jpg", "jpeg", "bmp"));
        if (fc.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
          abs[0] = fc.getSelectedFile().getAbsolutePath();
        }
      });
    } catch (Exception ignored) {}

    if (abs[0] == null) return -1;

    var fh = Gdx.files.absolute(abs[0]);
    if (!fh.exists()) return -1;

    byte[] data = fh.readBytes();
    MapResource.TextureRef tr = world.map.importGroundTexture(fh.name(), data);
    tr.uvScale = uvScale;

    reloadGroundTextures();
    return tr.id;
  }

  // ----------------------------------------------------------------------------
  // Save / IO
  // ----------------------------------------------------------------------------
  /** Back-compat shim (old code): same as buildManifestMerged(). */
  public MapResource.Manifest buildManifest() { return io.buildManifestMerged(); }

  /** Build a manifest that merges the current on-disk set with any in-memory chunks you own. */
  public MapResource.Manifest buildManifestMerged() { return io.buildManifestMerged(); }

  /** Encode only the chunks you intend to persist (delta-save friendly). */
  public Map<String, byte[]> encodeChunksToHmx() { return io.encodeChunksToHmx(); }

  /** Save manifest + chunks + any textures (staged or existing) referenced by the manifest. */
  public void saveAll(String path) { io.saveAll(path); }

  // ----------------------------------------------------------------------------
  // HeightSampler (for generic sampling clients)
  // ----------------------------------------------------------------------------
  @Override public float heightAt(float x, float z, float defaultHeight) {
    float base = world.sampleHeightAtWorld(x, z, defaultHeight);
    HeightModifier hm = world.heightModifier;
    return (hm != null) ? base + hm.deltaAt(x, z) : base;
  }
  @Override public int getSamplesPerSide() { return world.S; }
  @Override public float getTileSize() { return world.tile; }
  @Override public float getYScale() { return world.yScale; }
  public TerrainEditor getEditor() { return editor; }
  public TerrainPicking getPicker() { return picking; }

  /** HeightSampler that uses each chunk's baseline heights if available, otherwise current heights.
   * Ignores non-destructive modifiers. Useful for ribbons that should follow the base map. */
  public HeightSampler getBaseHeightSampler() {
    return new HeightSampler() {
      @Override public float heightAt(float x, float z, float defaultHeight) {
        return sampleBaseHeightAtWorld(x, z, defaultHeight);
      }
      @Override public int getSamplesPerSide() { return world.S; }
      @Override public float getTileSize() { return world.tile; }
      @Override public float getYScale() { return world.yScale; }
    };
  }

  private float sampleBaseHeightAtWorld(float x, float z, float def) {
    int S = world.S;
    int cpc = S - 1;
    int cx = (int)Math.floor(x / (cpc * world.tile));
    int cz = (int)Math.floor(z / (cpc * world.tile));
    TerrainChunk c = world.chunks.get(TerrainWorld.key(cx, cz));
    if (c == null) return def;
    float lx = (x - c.getOriginX()) / c.getTile();
    float lz = (z - c.getOriginZ()) / c.getTile();
    if (lx < 0f || lz < 0f || lx > S - 1.001f || lz > S - 1.001f) return def;
    int x0 = (int)Math.floor(lx), z0 = (int)Math.floor(lz);
    int x1 = x0 + 1, z1 = z0 + 1;
    float tx = lx - x0, tz = lz - z0;
    short[] base = c.getBaselineArray();
    short[] arr = (base != null) ? base : c.getHeightsArray();
    float ys = c.getYScale();
    float h00 = arr[z0 * S + x0] * ys;
    float h10 = arr[z0 * S + x1] * ys;
    float h01 = arr[z1 * S + x0] * ys;
    float h11 = arr[z1 * S + x1] * ys;
    float hx0 = h00 + (h10 - h00) * tx;
    float hx1 = h01 + (h11 - h01) * tx;
    return hx0 + (hx1 - hx0) * tz;
  }

  // ----------------------------------------------------------------------------
  // Misc
  // ----------------------------------------------------------------------------
  public boolean hasChunk(int cx, int cz) { return world.chunks.containsKey(TerrainWorld.key(cx, cz)); }
  public MapResource getMap() { return world.map; }

  /** Install a non-destructive height modifier (e.g., rivers). Triggers LOD rebuild next frame. */
  public void setHeightModifier(HeightModifier m) {
    world.heightModifier = m;
    // Mark all chunks dirty so LOD meshes rebuild with modifier-applied vertex heights
    for (com.badlogic.gdx.utils.IntMap.Entry<TerrainChunk> e : world.chunks) e.value.setDirty(true);
  }

  /**
   * Bake river depths into the terrain heightmap by stamping circles along each river spline.
   * This permanently modifies the CPU height arrays and triggers LOD rebuilds.
   *
   * @param rivers list of splines (will process only those with type RIVER)
   * @param samplesPerSegment sampling density along each spline segment
   */
  public void bakeRiversDepth(java.util.List<com.example.game.World.Splines.TerrainSpline> rivers, int samplesPerSegment) {
    if (rivers == null || rivers.isEmpty()) return;
    final float tile = world.tile;
    final float yScale = world.yScale;

    // One-time: ensure every loaded chunk has a baseline to restore from (does not overwrite if already present)
    for (com.badlogic.gdx.utils.IntMap.Entry<TerrainChunk> e : world.chunks) e.value.ensureBaseline();

    // Filter splines: RIVER or ROAD type and depth > 0
    java.util.ArrayList<com.example.game.World.Splines.TerrainSpline> toBake = new java.util.ArrayList<>();
    for (com.example.game.World.Splines.TerrainSpline s : rivers) {
      com.example.game.World.Splines.TerrainSpline.Type ty = s.getType();
      if ((ty == com.example.game.World.Splines.TerrainSpline.Type.RIVER || ty == com.example.game.World.Splines.TerrainSpline.Type.ROAD)
          && s.riverDepth > 0f) {
        toBake.add(s);
      }
    }
    if (toBake.isEmpty()) return; // nothing to do

    // Compute union of affected chunks for ALL rivers, then restore baseline ONCE
    com.badlogic.gdx.utils.IntSet affectedAll = new com.badlogic.gdx.utils.IntSet();
    int cpc = world.S - 1;
    for (com.example.game.World.Splines.TerrainSpline s : toBake) {
      java.util.List<com.badlogic.gdx.math.Vector3> pts = s.samplePositions(Math.max(2, samplesPerSegment));
      if (pts.size() < 2) continue;
      int radiusCells = Math.max(1, Math.round((s.width * 0.5f) / tile));
      for (com.badlogic.gdx.math.Vector3 p : pts) {
        int minCx = (int)Math.floor((p.x - radiusCells * tile) / (cpc * tile));
        int maxCx = (int)Math.floor((p.x + radiusCells * tile) / (cpc * tile));
        int minCz = (int)Math.floor((p.z - radiusCells * tile) / (cpc * tile));
        int maxCz = (int)Math.floor((p.z + radiusCells * tile) / (cpc * tile));
        for (int cz = minCz; cz <= maxCz; cz++)
          for (int cx = minCx; cx <= maxCx; cx++)
            affectedAll.add(TerrainWorld.key(cx, cz));
      }
    }
    for (com.badlogic.gdx.utils.IntSet.IntSetIterator it = affectedAll.iterator(); it.hasNext; ) {
      int k = it.next();
      TerrainChunk c = world.chunks.get(k);
      if (c != null) c.restoreAllFromBaseline();
    }

    // Now stamp all rivers (depth>0) over the restored baseline
    for (com.example.game.World.Splines.TerrainSpline s : toBake) {
      java.util.List<com.badlogic.gdx.math.Vector3> pts = s.samplePositions(Math.max(2, samplesPerSegment));
      if (pts.size() < 2) continue;
      int radiusCells = Math.max(1, Math.round((s.width * 0.5f) / tile));
      for (com.badlogic.gdx.math.Vector3 p : pts) {
        int gx = Math.round(p.x / tile);
        int gz = Math.round(p.z / tile);
        editor.applyRiverDepthCircleProfiledClamped(gx, gz, radiusCells, s.riverDepth, s.bankFalloff);
      }
    }
    // Rebuild meshes to reflect changes
    editor.rebuildDirtyChunks();
  }

  public void dispose() {
    renderer.dispose();
    world.disposeWorld();
  }
}
