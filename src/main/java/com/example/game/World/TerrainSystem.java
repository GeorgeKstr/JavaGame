package com.example.game.World;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.TextureArray;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntSet;
import com.example.game.Render.SimpleShader;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * TerrainSystem (threaded streaming + skip-empty-save)
 */
public class TerrainSystem implements HeightSampler {

  // ---- Config (from map manifest) ----
  private final int S;          // samples per side
  private final float tile;     // meters per cell
  private final float yScale;   // meters per height unit
  private int viewRadius = 3;   // in chunk coords

  // ---- Data ----
  private final MapResource map;
  private final SparseChunkProvider provider;
  private final IntMap<TerrainChunk> chunks = new IntMap<>();

  // Track which loaded coords were created as "flat default" (file missing).
  // If such a chunk is never edited (isDirty=false), we won't persist it.
  private final IntSet defaultFlatChunks = new IntSet();

  // ---- Streaming (threading) ----
  private final ExecutorService loaderPool;
  private final ConcurrentLinkedQueue<Result> completed = new ConcurrentLinkedQueue<>();
  private final IntSet pendingLoads = new IntSet(); // avoid duplicate enqueues

  // ---- Rendering (textures) ----
  private TextureArray groundTA;
  private final float[] uvScales = new float[256];

  public TerrainSystem(MapResource map) {
    this.map = map;
    var mf = map.getManifest();
    this.S = mf.samplesPerSide;
    this.tile = mf.tile;
    this.yScale = mf.yScale;
    this.provider = new SparseChunkProvider("ignored", S);

    // modest pool; tune as needed
    int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    this.loaderPool = Executors.newFixedThreadPool(threads, r -> {
      Thread t = new Thread(r, "ChunkLoader");
      t.setDaemon(true);
      return t;
    });

    // Build texture array & uvScales if we have ground textures
    if (!mf.groundTextures.isEmpty()) {
      this.groundTA = map.buildGroundTextureArray();
      for (MapResource.TextureRef tr : mf.groundTextures) {
        int id = tr.id;
        if (id >= 0 && id < uvScales.length) {
          uvScales[id] = (tr.uvScale == 0f ? 1f : tr.uvScale);
        }
      }
    }
  }

  // =========================================================
  // Streaming
  // =========================================================

  private static int key(int cx, int cz) { return (cx & 0xFFFF) | (cz << 16); }
  private int cellsPerChunk() { return S - 1; }

  /** Call each frame or on movement. */
  public void updateAndStream(float worldX, float worldZ) {
    final int cpc = cellsPerChunk();
    int cx = (int) Math.floor(worldX / (cpc * tile));
    int cz = (int) Math.floor(worldZ / (cpc * tile));

    Array<Integer> needed = new Array<>();
    for (int dz = -viewRadius; dz <= viewRadius; dz++) {
      for (int dx = -viewRadius; dx <= viewRadius; dx++) {
        int kc = key(cx + dx, cz + dz);
        needed.add(kc);
        if (!chunks.containsKey(kc) && !pendingLoads.contains(kc)) {
          enqueueLoad(cx + dx, cz + dz);
        }
      }
    }

    // Drain worker completions (instantiate GL stuff here on main thread)
    drainCompleted();

    // Unload out-of-range chunks
    Array<Integer> toRemove = new Array<>();
    for (IntMap.Entry<TerrainChunk> e : chunks) {
      if (!needed.contains(e.key, false)) toRemove.add(e.key);
    }
    for (Integer k : toRemove) {
      TerrainChunk c = chunks.remove(k);
      if (c != null) c.dispose();
      defaultFlatChunks.remove(k); // clean metadata
    }
  }

  private void enqueueLoad(int cx, int cz) {
    final int k = key(cx, cz);
    pendingLoads.add(k);

    final float originX = cx * (S - 1) * tile;
    final float originZ = cz * (S - 1) * tile;

    // Package the background job
    Supplier<Result> job = () -> {
      try {
        byte[] hmx = map.readChunkHmx(cx, cz);
        short[] heights;
        byte[] texIds;
        boolean defaultFlat;

        if (hmx == null) {
          int defaultTex = map.getManifest().defaultTexture;
          var raw = createFlatRaw(defaultTex);
          heights = raw.heights;
          texIds  = raw.texIds;
          defaultFlat = true;
        } else {
          var lc = provider.loadFromBytes(hmx);
          heights = lc.heights;
          texIds  = lc.texIds;
          defaultFlat = false;
        }
        return new Result(k, originX, originZ, heights, texIds, defaultFlat, null);
      } catch (Throwable t) {
        return new Result(k, originX, originZ, null, null, false, t);
      }
    };

    loaderPool.submit(() -> completed.add(job.get()));
  }

  /** Called from update to create TerrainChunk + mesh on main thread. */
  private void drainCompleted() {
    Result r;
    while ((r = completed.poll()) != null) {
      pendingLoads.remove(r.k);

      if (r.error != null) {
        // Log and skip
        Gdx.app.error("TerrainSystem", "Chunk load failed for key=" + r.k, r.error);
        continue;
      }
      if (r.heights == null || r.texIds == null) {
        Gdx.app.error("TerrainSystem", "Chunk load returned null arrays for key=" + r.k, null);
        continue;
      }

      TerrainChunk tc = new TerrainChunk(S, tile, yScale, r.originX, r.originZ, r.heights, r.texIds);
      tc.buildMesh();
      chunks.put(r.k, tc);

      if (r.defaultFlat) {
        defaultFlatChunks.add(r.k);
      } else {
        defaultFlatChunks.remove(r.k);
      }
    }
  }

  /** Data passed from worker to main thread. */
  private static final class Result {
    final int k;
    final float originX, originZ;
    final short[] heights;
    final byte[] texIds;
    final boolean defaultFlat;
    final Throwable error;
    Result(int k, float ox, float oz, short[] h, byte[] t, boolean def, Throwable err) {
      this.k = k; this.originX = ox; this.originZ = oz;
      this.heights = h; this.texIds = t; this.defaultFlat = def; this.error = err;
    }
  }

  // Build arrays only (no GL)
  private RawChunk createFlatRaw(int defaultTexture) {
    short[] heights = new short[S * S]; // zeroes
    byte[] texIds   = new byte[S * S];
    byte def = (byte)(defaultTexture & 0xFF);
    if (def != 0) for (int i = 0; i < texIds.length; i++) texIds[i] = def;
    return new RawChunk(heights, texIds);
  }

  private static final class RawChunk {
    final short[] heights; final byte[] texIds;
    RawChunk(short[] h, byte[] t) { heights = h; texIds = t; }
  }

  private TerrainChunk getChunk(int cx, int cz) { return chunks.get(key(cx, cz)); }

  // =========================================================
  // Sampling
  // =========================================================

  @Override
  public float heightAt(float x, float z, float defaultHeight) {
    final int cpc = cellsPerChunk();
    int cx = (int) Math.floor(x / (cpc * tile));
    int cz = (int) Math.floor(z / (cpc * tile));
    TerrainChunk c = chunks.get(key(cx, cz));
    if (c == null) return defaultHeight;
    float h = c.heightAtWorld(x, z);
    return Float.isNaN(h) ? defaultHeight : h;
  }

  @Override public int getSamplesPerSide() { return S; }
  @Override public float getTileSize() { return tile; }
  @Override public float getYScale() { return yScale; }

  // =========================================================
  // Editing (with seam mirroring) — unchanged logic
  // =========================================================

  private static class Index {
    int cx, cz, lx, lz;
    void set(int cx, int cz, int lx, int lz) { this.cx=cx; this.cz=cz; this.lx=lx; this.lz=lz; }
  }

  private boolean primaryIndex(int gx, int gz, Index out) {
    int cpc = cellsPerChunk();
    int cx = (int)Math.floor((float)gx / cpc);
    int cz = (int)Math.floor((float)gz / cpc);
    int lx = gx - cx * cpc;
    int lz = gz - cz * cpc;
    if (lx < 0 || lz < 0 || lx >= S || lz >= S) return false;
    out.cx = cx; out.cz = cz; out.lx = lx; out.lz = lz;
    return true;
  }

  private int neighborsForSample(Index primary, Index[] outs) {
    int count = 0;
    if (primary.lx == S - 1) outs[count++].set(primary.cx + 1, primary.cz, 0, primary.lz);
    if (primary.lx == 0)     outs[count++].set(primary.cx - 1, primary.cz, S - 1, primary.lz);
    if (primary.lz == S - 1) outs[count++].set(primary.cx, primary.cz + 1, primary.lx, 0);
    if (primary.lz == 0)     outs[count++].set(primary.cx, primary.cz - 1, primary.lx, S - 1);
    if (primary.lx == S - 1 && primary.lz == S - 1) outs[count++].set(primary.cx + 1, primary.cz + 1, 0, 0);
    if (primary.lx == 0     && primary.lz == S - 1) outs[count++].set(primary.cx - 1, primary.cz + 1, S - 1, 0);
    if (primary.lx == S - 1 && primary.lz == 0)     outs[count++].set(primary.cx + 1, primary.cz - 1, 0, S - 1);
    if (primary.lx == 0     && primary.lz == 0)     outs[count++].set(primary.cx - 1, primary.cz - 1, S - 1, S - 1);
    return count;
  }

  public boolean editHeightsCircle(int gx, int gz, int radius, short delta) {
    boolean changed = false;
    Index p = new Index();
    Index[] ns = new Index[]{ new Index(), new Index(), new Index(), new Index(), new Index(), new Index(), new Index(), new Index() };

    for (int dz = -radius; dz <= radius; dz++) {
      for (int dx = -radius; dx <= radius; dx++) {
        if (dx * dx + dz * dz > radius * radius) continue;

        int gxi = gx + dx;
        int gzi = gz + dz;

        if (!primaryIndex(gxi, gzi, p)) continue;
        TerrainChunk tc = getChunk(p.cx, p.cz);
        if (tc == null) continue;

        short[] arr = tc.getHeightsArray();
        int idx = p.lz * S + p.lx;
        short before = arr[idx];
        short after = (short) (before + delta);
        if (after != before) {
          arr[idx] = after;
          tc.setDirty(true);
          changed = true;
        }

        int ncount = neighborsForSample(p, ns);
        for (int i = 0; i < ncount; i++) {
          TerrainChunk nb = getChunk(ns[i].cx, ns[i].cz);
          if (nb == null) continue;
          short[] harr = nb.getHeightsArray();
          int nidx = ns[i].lz * S + ns[i].lx;
          short b = harr[nidx];
          short a = (short)(b + delta);
          if (a != b) {
            harr[nidx] = a;
            nb.setDirty(true);
          }
        }
      }
    }
    return changed;
  }

  public boolean paintTexCircle(int gx, int gz, int radius, byte texId) {
    boolean changed = false;
    Index p = new Index();
    Index[] ns = new Index[]{ new Index(), new Index(), new Index(), new Index(), new Index(), new Index(), new Index(), new Index() };

    for (int dz = -radius; dz <= radius; dz++) {
      for (int dx = -radius; dx <= radius; dx++) {
        if (dx * dx + dz * dz > radius * radius) continue;

        int gxi = gx + dx;
        int gzi = gz + dz;

        if (!primaryIndex(gxi, gzi, p)) continue;
        TerrainChunk tc = getChunk(p.cx, p.cz);
        if (tc == null) continue;

        byte[] tex = tc.getTexIdsArray();
        int idx = p.lz * S + p.lx;
        byte before = tex[idx];
        if (before != texId) {
          tex[idx] = texId;
          tc.setDirty(true);
          changed = true;
        }

        int ncount = neighborsForSample(p, ns);
        for (int i = 0; i < ncount; i++) {
          TerrainChunk nb = getChunk(ns[i].cx, ns[i].cz);
          if (nb == null) continue;
          byte[] tarr = nb.getTexIdsArray();
          int nidx = ns[i].lz * S + ns[i].lx;
          if (tarr[nidx] != texId) {
            tarr[nidx] = texId;
            nb.setDirty(true);
          }
        }
      }
    }
    return changed;
  }

  public void rebuildDirtyChunks() {
    for (IntMap.Entry<TerrainChunk> e : chunks) {
      TerrainChunk c = e.value;
      if (c.isDirty()) {
        c.buildMesh();
        c.setDirty(false);
        // Once edited, it's no longer "default-empty" for saving purposes
        defaultFlatChunks.remove(e.key);
      }
    }
  }

  // =========================================================
  // Render
  // =========================================================

  public void render(SimpleShader shader, Camera cam) {
    shader.bind();
    shader.setProjectionView(cam.combined);
    shader.applyUniforms();

    if (groundTA != null) {
      Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
      groundTA.bind();
      var sp = shader.getProgram();
      sp.setUniformi("u_groundTex", 0);
      sp.setUniform1fv("u_uvScales", uvScales, 0, uvScales.length);
      sp.setUniformf("u_uvWorldScale", 1.0f / getTileSize());
    }

    for (IntMap.Entry<TerrainChunk> e : chunks) {
      e.value.render(shader);
    }
  }

  // =========================================================
  // Save helpers (skip default-empty chunks)
  // =========================================================

  public MapResource.Manifest buildManifest() {
    MapResource.Manifest src = map.getManifest();

    MapResource.Manifest m = new MapResource.Manifest();
    m.version = Math.max(src.version, src.groundTextures.isEmpty() ? 1 : 2);
    m.samplesPerSide = S;
    m.tile = tile;
    m.yScale = yScale;
    m.defaultTexture = src.defaultTexture;
    m.groundTextures = new java.util.ArrayList<>(src.groundTextures);

    m.chunks = new java.util.ArrayList<>();
    for (IntMap.Entry<TerrainChunk> e : chunks) {
      // Only advertise chunks that will actually be persisted
      if (shouldPersist(e.key, e.value)) {
        int cx = Math.round((float) e.value.getOriginX() / ((S - 1) * tile));
        int cz = Math.round((float) e.value.getOriginZ() / ((S - 1) * tile));
        m.chunks.add(new int[]{cx, cz});
      }
    }
    m.chunkCount = m.chunks.size();
    return m;
  }

  public Map<String, byte[]> encodeChunksToHmx() {
    Map<String, byte[]> out = new HashMap<>();
    for (IntMap.Entry<TerrainChunk> e : chunks) {
      int k = e.key;
      TerrainChunk c = e.value;

      if (!shouldPersist(k, c)) continue; // <-- skip empty default chunks

      int cx = Math.round((float) c.getOriginX() / ((S - 1) * tile));
      int cz = Math.round((float) c.getOriginZ() / ((S - 1) * tile));
      try {
        byte[] blob = SparseChunkWriter.encodeToBytes(
            S, c.getHeightsArray(), c.getTexIdsArray());
        out.put(cx + "_" + cz + ".hmx", blob);
      } catch (IOException ex) {
        throw new RuntimeException("Failed to encode chunk " + cx + "," + cz, ex);
      }
    }
    return out;
  }

  /** A chunk should be saved if it was not a default-flat OR it has edits. */
  private boolean shouldPersist(int key, TerrainChunk c) {
    if (c == null) return false;
    if (c.isDirty()) return true;
    // If it arrived from disk (i.e., not default-flat), keep persisting it
    if (!defaultFlatChunks.contains(key)) return true;
    // It was default-flat and never edited -> do not save
    return false;
  }

  public void saveAll(String path) {
    map.saveIncludingStaged(path, encodeChunksToHmx());
  }

  // =========================================================
  // Accessors & cleanup
  // =========================================================

  public void setViewRadius(int r) { this.viewRadius = Math.max(0, r); }
  public boolean hasChunk(int cx, int cz) { return chunks.containsKey(key(cx, cz)); }
  public MapResource getMap() { return map; }

  public void dispose() {
    for (IntMap.Entry<TerrainChunk> e : chunks) e.value.dispose();
    chunks.clear();
    defaultFlatChunks.clear();
    pendingLoads.clear();
    completed.clear();
    if (groundTA != null) { groundTA.dispose(); groundTA = null; }
    loaderPool.shutdownNow();
  }
}
