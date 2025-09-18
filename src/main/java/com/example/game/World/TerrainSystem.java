package com.example.game.World;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.example.game.Render.SimpleShader;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * TerrainSystem
 *  - Streams TerrainChunk instances from a MapResource (.map ZIP)
 *  - Chunks are stored in sparse HMXS format (global S/tile/yScale)
 *  - Provides height sampling, editing (raise/lower + paint) with seam mirroring,
 *    mesh rebuild, and save helpers.
 *  - If a chunk file is missing, it creates a flat in-memory chunk using the map's default texture.
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

  public TerrainSystem(MapResource map) {
    this.map = map;
    var mf = map.getManifest();
    this.S = mf.samplesPerSide;
    this.tile = mf.tile;
    this.yScale = mf.yScale;
    this.provider = new SparseChunkProvider("ignored", S);
  }

  // =========================================================
  // Streaming
  // =========================================================

  private static int key(int cx, int cz) { return (cx & 0xFFFF) | (cz << 16); }
  private int cellsPerChunk() { return S - 1; }

  /** Load/unload chunks around a world position. Call each frame or on movement. */
  public void updateAndStream(float worldX, float worldZ) {
    final int cpc = cellsPerChunk();
    int cx = (int) Math.floor(worldX / (cpc * tile));
    int cz = (int) Math.floor(worldZ / (cpc * tile));

    Array<Integer> needed = new Array<>();
    for (int dz = -viewRadius; dz <= viewRadius; dz++) {
      for (int dx = -viewRadius; dx <= viewRadius; dx++) {
        int k = key(cx + dx, cz + dz);
        needed.add(k);
        if (!chunks.containsKey(k)) loadChunk(cx + dx, cz + dz);
      }
    }

    Array<Integer> toRemove = new Array<>();
    for (IntMap.Entry<TerrainChunk> e : chunks) {
      if (!needed.contains(e.key, false)) toRemove.add(e.key);
    }
    for (Integer k : toRemove) {
      TerrainChunk c = chunks.remove(k);
      if (c != null) c.dispose();
    }
  }

  /** Load chunk from map; if missing, instantiate a flat default chunk. */
  private void loadChunk(int cx, int cz) {
    byte[] hmx = map.readChunkHmx(cx, cz);

    final float originX = cx * (S - 1) * tile;
    final float originZ = cz * (S - 1) * tile;

    TerrainChunk tc;
    if (hmx == null) {
      // Fallback: create a flat chunk (heights=0) with default texture id
      int defaultTex = map.getManifest().defaultTexture;
      tc = createFlatChunk(originX, originZ, defaultTex);
    } else {
      var lc = provider.loadFromBytes(hmx);
      tc = new TerrainChunk(S, tile, yScale, originX, originZ, lc.heights, lc.texIds);
    }

    tc.buildMesh();
    chunks.put(key(cx, cz), tc);
  }

  /** Create a flat S×S chunk (heights=0), texIds filled with defaultTexture if non-zero. */
  private TerrainChunk createFlatChunk(float originX, float originZ, int defaultTexture) {
    short[] heights = new short[S * S];             // baseline=0
    byte[] texIds   = new byte[S * S];              // 0 = default texture
    byte def = (byte)(defaultTexture & 0xFF);
    if (def != 0) {
      for (int i = 0; i < texIds.length; i++) texIds[i] = def;
    }
    return new TerrainChunk(S, tile, yScale, originX, originZ, heights, texIds);
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
  // Editing (with seam mirroring)
  // =========================================================

  /** Small mutable index holder to avoid allocations. */
  private static class Index {
    int cx, cz, lx, lz;
    void set(int cx, int cz, int lx, int lz) { this.cx=cx; this.cz=cz; this.lx=lx; this.lz=lz; }
  }

  /** Compute owning chunk/local index for a global grid sample (gx,gz). */
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

  /**
   * For edge/corner samples, enumerate neighbor chunk/local index pairs that
   * share the exact same world grid vertex.
   * @return count of neighbors filled into outs[]
   */
  private int neighborsForSample(Index primary, Index[] outs) {
    int count = 0;
    // Right neighbor shares lx=0 when primary at lx=S-1
    if (primary.lx == S - 1) outs[count++].set(primary.cx + 1, primary.cz, 0, primary.lz);
    // Left neighbor shares lx=S-1 when primary at lx=0
    if (primary.lx == 0)     outs[count++].set(primary.cx - 1, primary.cz, S - 1, primary.lz);
    // Down neighbor (positive z) shares lz=0 when primary at lz=S-1
    if (primary.lz == S - 1) outs[count++].set(primary.cx, primary.cz + 1, primary.lx, 0);
    // Up neighbor (negative z) shares lz=S-1 when primary at lz=0
    if (primary.lz == 0)     outs[count++].set(primary.cx, primary.cz - 1, primary.lx, S - 1);

    // Corners: combine both axes
    if (primary.lx == S - 1 && primary.lz == S - 1) outs[count++].set(primary.cx + 1, primary.cz + 1, 0, 0);
    if (primary.lx == 0     && primary.lz == S - 1) outs[count++].set(primary.cx - 1, primary.cz + 1, S - 1, 0);
    if (primary.lx == S - 1 && primary.lz == 0)     outs[count++].set(primary.cx + 1, primary.cz - 1, 0, S - 1);
    if (primary.lx == 0     && primary.lz == 0)     outs[count++].set(primary.cx - 1, primary.cz - 1, S - 1, S - 1);
    return count;
  }

  /** Raise/lower heights within a circular brush, mirroring edge samples into neighbors. */
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

        // mirror into neighbors if this sample lies on edges/corners
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

  /** Paint texture IDs in a circular brush, mirroring edge samples into neighbors. */
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

  /** Rebuild meshes for chunks that have been marked dirty by edits. */
  public void rebuildDirtyChunks() {
    for (IntMap.Entry<TerrainChunk> e : chunks) {
      TerrainChunk c = e.value;
      if (c.isDirty()) {
        c.buildMesh();
        c.setDirty(false);
      }
    }
  }

  // =========================================================
  // Render
  // =========================================================

  public void render(SimpleShader shader, Camera cam) {
    // (Optional) add frustum culling here if desired
    for (IntMap.Entry<TerrainChunk> e : chunks) e.value.render(shader);
  }

  // =========================================================
  // Save helpers
  // =========================================================

  public MapResource.Manifest buildManifest() {
    MapResource.Manifest m = new MapResource.Manifest();
    m.version = 1;
    m.samplesPerSide = S;
    m.tile = tile;
    m.yScale = yScale;
    m.defaultTexture = map.getManifest().defaultTexture;
    m.chunks = new java.util.ArrayList<>();
    for (IntMap.Entry<TerrainChunk> e : chunks) {
      int cx = Math.round((float) e.value.getOriginX() / ((S - 1) * tile));
      int cz = Math.round((float) e.value.getOriginZ() / ((S - 1) * tile));
      m.chunks.add(new int[]{cx, cz});
    }
    m.chunkCount = m.chunks.size();
    return m;
  }

  /** Encode loaded chunks back into HMXS blobs ready for MapResource.save(). */
  public Map<String, byte[]> encodeChunksToHmx() {
    Map<String, byte[]> out = new HashMap<>();
    for (IntMap.Entry<TerrainChunk> e : chunks) {
      TerrainChunk c = e.value;
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

  // =========================================================
  // Accessors & cleanup
  // =========================================================

  public void setViewRadius(int r) { this.viewRadius = Math.max(0, r); }
  public boolean hasChunk(int cx, int cz) { return chunks.containsKey(key(cx, cz)); }

  public void dispose() {
    for (IntMap.Entry<TerrainChunk> e : chunks) e.value.dispose();
    chunks.clear();
  }
  
}
