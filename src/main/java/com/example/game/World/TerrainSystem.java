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
import java.util.*;
import java.util.concurrent.*;

public class TerrainSystem implements HeightSampler {

  // ---- Config ----
  private final int S;
  private final float tile, yScale;
  private int viewRadius = 3;

  // ---- Data ----
  private final MapResource map;
  private final SparseChunkProvider provider;
  private final IntMap<TerrainChunk> chunks = new IntMap<>();
  private final IntSet defaultFlatChunks = new IntSet(); // auto-made, untouched → don’t save

  // ---- Streaming ----
  private final ExecutorService pool;
  private final ConcurrentLinkedQueue<Result> completed = new ConcurrentLinkedQueue<>();
  private final IntSet pending = new IntSet();

  // ---- Rendering (textures) ----
  private TextureArray groundTA;
  private final float[] uvScales = new float[256];

  // ---- LOD selection ----
  private int MAX_LOD = 0; // computed from S so that (S-1) % (1<<MAX_LOD) == 0
  private float[] lodDistances = new float[]{ 50f, 120f, 260f, 520f }; // thresholds for LOD1..n
  private float hysteresis = 12f;
  private final IntMap<Integer> lastLod = new IntMap<>();

  public TerrainSystem(MapResource map) {
    this.map = map;
    var mf = map.getManifest();
    this.S = mf.samplesPerSide;
    this.tile = mf.tile;
    this.yScale = mf.yScale;
    this.provider = new SparseChunkProvider("ignored", S);

    // compute MAX_LOD so that step divides (S-1)
    int cells = S - 1, lod = 0;
    while (((cells % (1 << (lod + 1))) == 0) && lod < 6) lod++;
    this.MAX_LOD = lod; // e.g., S=65 => cells=64 => MAX_LOD=6 (0..6)

    int n = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    this.pool = Executors.newFixedThreadPool(n, r -> { var t = new Thread(r, "ChunkIO"); t.setDaemon(true); return t; });

    if (!mf.groundTextures.isEmpty()) {
      this.groundTA = map.buildGroundTextureArray();
      for (MapResource.TextureRef tr : mf.groundTextures) {
        if (tr.id >= 0 && tr.id < uvScales.length) uvScales[tr.id] = (tr.uvScale == 0f ? 1f : tr.uvScale);
      }
    }
  }

  // ---------- Streaming ----------

  private static int key(int cx, int cz) { return (cx & 0xFFFF) | (cz << 16); }
  private int cellsPerChunk() { return S - 1; }

  /** Call per frame or on movement. */
  public void updateAndStream(float worldX, float worldZ) {
    final int cpc = cellsPerChunk();
    int cx = (int)Math.floor(worldX / (cpc * tile));
    int cz = (int)Math.floor(worldZ / (cpc * tile));

    Array<Integer> needed = new Array<>();
    for (int dz = -viewRadius; dz <= viewRadius; dz++) {
      for (int dx = -viewRadius; dx <= viewRadius; dx++) {
        int k = key(cx + dx, cz + dz);
        needed.add(k);
        if (!chunks.containsKey(k) && !pending.contains(k)) enqueueLoad(cx + dx, cz + dz);
      }
    }

    drainCompleted(); // GL create on main thread

    Array<Integer> toRemove = new Array<>();
    for (IntMap.Entry<TerrainChunk> e : chunks) if (!needed.contains(e.key, false)) toRemove.add(e.key);
    for (Integer k : toRemove) {
      TerrainChunk c = chunks.remove(k);
      if (c != null) c.dispose();
      defaultFlatChunks.remove(k);
      lastLod.remove(k);
    }
  }

  private void enqueueLoad(int cx, int cz) {
    final int k = key(cx, cz);
    pending.add(k);

    final float ox = cx * (S - 1) * tile;
    final float oz = cz * (S - 1) * tile;

    pool.submit(() -> {
      try {
        byte[] hmx = map.readChunkHmx(cx, cz);
        if (hmx == null) {
          int dt = map.getManifest().defaultTexture;
          short[] h = new short[S * S];
          byte[]  t = new byte[S * S];
          byte def = (byte)(dt & 0xFF);
          if (def != 0) Arrays.fill(t, def);
          completed.add(new Result(k, ox, oz, h, t, true, null));
        } else {
          var lc = provider.loadFromBytes(hmx);
          completed.add(new Result(k, ox, oz, lc.heights, lc.texIds, false, null));
        }
      } catch (Throwable ex) {
        completed.add(new Result(k, ox, oz, null, null, false, ex));
      }
    });
  }

  private void drainCompleted() {
    for (Result r; (r = completed.poll()) != null; ) {
      pending.remove(r.k);
      if (r.err != null || r.h == null || r.t == null) {
        Gdx.app.error("TerrainSystem", "Chunk load failed k=" + r.k, r.err);
        continue;
      }
      var tc = new TerrainChunk(S, tile, yScale, r.ox, r.oz, r.h, r.t);
      // Build multi-LOD meshes once on load (render thread)
      tc.buildLods(MAX_LOD);
      chunks.put(r.k, tc);
      if (r.defaultFlat) defaultFlatChunks.add(r.k); else defaultFlatChunks.remove(r.k);
    }
  }

  private static final class Result {
    final int k; final float ox, oz; final short[] h; final byte[] t; final boolean defaultFlat; final Throwable err;
    Result(int k, float ox, float oz, short[] h, byte[] t, boolean df, Throwable e) { this.k=k; this.ox=ox; this.oz=oz; this.h=h; this.t=t; this.defaultFlat=df; this.err=e; }
  }

  private TerrainChunk getChunk(int cx, int cz) { return chunks.get(key(cx, cz)); }

  // ---------- Sampling ----------

  @Override public float heightAt(float x, float z, float defaultHeight) {
    int cpc = cellsPerChunk();
    int cx = (int)Math.floor(x / (cpc * tile));
    int cz = (int)Math.floor(z / (cpc * tile));
    TerrainChunk c = chunks.get(key(cx, cz));
    if (c == null) return defaultHeight;
    float h = c.heightAtWorld(x, z);
    return Float.isNaN(h) ? defaultHeight : h;
  }

  @Override public int getSamplesPerSide() { return S; }
  @Override public float getTileSize() { return tile; }
  @Override public float getYScale() { return yScale; }

  // ---------- Editing (unchanged) ----------

  private static class Index { int cx, cz, lx, lz; void set(int cx,int cz,int lx,int lz){this.cx=cx;this.cz=cz;this.lx=lx;this.lz=lz;} }
  private int cells() { return S - 1; }

  private boolean primaryIndex(int gx, int gz, Index out) {
    int cpc = cells();
    int cx = (int)Math.floor((float)gx / cpc), cz = (int)Math.floor((float)gz / cpc);
    int lx = gx - cx * cpc, lz = gz - cz * cpc;
    if (lx < 0 || lz < 0 || lx >= S || lz >= S) return false;
    out.set(cx, cz, lx, lz); return true;
  }
  private int neighborsForSample(Index p, Index[] o) {
    int c=0; if (p.lx==S-1) o[c++].set(p.cx+1,p.cz,0,p.lz);
            if (p.lx==0)   o[c++].set(p.cx-1,p.cz,S-1,p.lz);
            if (p.lz==S-1) o[c++].set(p.cx,p.cz+1,p.lx,0);
            if (p.lz==0)   o[c++].set(p.cx,p.cz-1,p.lx,S-1);
            if (p.lx==S-1&&p.lz==S-1) o[c++].set(p.cx+1,p.cz+1,0,0);
            if (p.lx==0  &&p.lz==S-1) o[c++].set(p.cx-1,p.cz+1,S-1,0);
            if (p.lx==S-1&&p.lz==0)   o[c++].set(p.cx+1,p.cz-1,0,S-1);
            if (p.lx==0  &&p.lz==0)   o[c++].set(p.cx-1,p.cz-1,S-1,S-1);
    return c;
  }

  public boolean editHeightsCircle(int gx, int gz, int radius, short delta) {
    boolean changed=false; Index p=new Index(); Index[] ns={new Index(),new Index(),new Index(),new Index(),new Index(),new Index(),new Index(),new Index()};
    for (int dz=-radius; dz<=radius; dz++) for (int dx=-radius; dx<=radius; dx++) {
      if (dx*dx+dz*dz>radius*radius) continue;
      int gxi=gx+dx, gzi=gz+dz;
      if (!primaryIndex(gxi,gzi,p)) continue;
      TerrainChunk tc=getChunk(p.cx,p.cz); if (tc==null) continue;
      short[] arr=tc.getHeightsArray(); int idx=p.lz*S+p.lx; short b=arr[idx], a=(short)(b+delta);
      if (a!=b){ arr[idx]=a; tc.setDirty(true); changed=true; }
      int ncount=neighborsForSample(p,ns);
      for(int i=0;i<ncount;i++){ TerrainChunk nb=getChunk(ns[i].cx,ns[i].cz); if(nb==null)continue;
        short[] harr=nb.getHeightsArray(); int nidx=ns[i].lz*S+ns[i].lx; short bb=harr[nidx], aa=(short)(bb+delta);
        if(aa!=bb){ harr[nidx]=aa; nb.setDirty(true); }
      }
    }
    return changed;
  }

  public boolean paintTexCircle(int gx, int gz, int radius, byte texId) {
    boolean changed=false; Index p=new Index(); Index[] ns={new Index(),new Index(),new Index(),new Index(),new Index(),new Index(),new Index(),new Index()};
    for (int dz=-radius; dz<=radius; dz++) for (int dx=-radius; dx<=radius; dx++) {
      if (dx*dx+dz*dz>radius*radius) continue;
      int gxi=gx+dx, gzi=gz+dz;
      if (!primaryIndex(gxi,gzi,p)) continue;
      TerrainChunk tc=getChunk(p.cx,p.cz); if (tc==null) continue;
      byte[] tex=tc.getTexIdsArray(); int idx=p.lz*S+p.lx; byte b=tex[idx];
      if (b!=texId){ tex[idx]=texId; tc.setDirty(true); changed=true; }
      int ncount=neighborsForSample(p,ns);
      for(int i=0;i<ncount;i++){ TerrainChunk nb=getChunk(ns[i].cx,ns[i].cz); if(nb==null)continue;
        byte[] tarr=nb.getTexIdsArray(); int nidx=ns[i].lz*S+ns[i].lx; if(tarr[nidx]!=texId){ tarr[nidx]=texId; nb.setDirty(true); }
      }
    }
    return changed;
  }

  public void rebuildDirtyChunks() {
    for (IntMap.Entry<TerrainChunk> e : chunks) {
      TerrainChunk c = e.value;
      if (c.isDirty()) {
        // Rebuild ALL LODs after edits
        c.buildLods(MAX_LOD);
        c.setDirty(false);
        defaultFlatChunks.remove(e.key); // once edited, it must persist
      }
    }
  }

  // ---------- LOD selection & render ----------

  private int selectLod(float camX, float camZ, TerrainChunk c) {
    float cx = c.getOriginX() + (c.getSize()-1) * c.getTile() * 0.5f;
    float cz = c.getOriginZ() + (c.getSize()-1) * c.getTile() * 0.5f;
    float dx = camX - cx, dz = camZ - cz;
    float dist = (float)Math.sqrt(dx*dx + dz*dz);

    int desired = 0;
    int maxCheck = Math.min(MAX_LOD, lodDistances.length);
    for (int i = 0; i < maxCheck; i++) {
      if (dist > lodDistances[i]) desired = i + 1; else break;
    }

    // hysteresis per chunk key
    int k = key(Math.round(c.getOriginX() / ((S-1)*tile)), Math.round(c.getOriginZ() / ((S-1)*tile)));
    Integer prev = lastLod.get(k);
    if (prev != null) {
      if (desired > prev) {
        float back = (desired-1 < lodDistances.length ? lodDistances[desired-1] : lodDistances[lodDistances.length-1]);
        if (dist < back + hysteresis) desired = prev;
      } else if (desired < prev) {
        float next = (desired < lodDistances.length ? lodDistances[desired] : Float.MAX_VALUE);
        if (dist > next - hysteresis) desired = prev;
      }
    }
    lastLod.put(k, desired);
    return Math.min(desired, MAX_LOD);
  }

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
      sp.setUniformf("u_uvWorldScale", 1f / tile);
    }

    float camX = cam.position.x, camZ = cam.position.z;
    for (IntMap.Entry<TerrainChunk> e : chunks) {
      TerrainChunk c = e.value;
      if (!c.hasLods(MAX_LOD)) c.buildLods(MAX_LOD);
      int lod = selectLod(camX, camZ, c);
      c.renderLod(shader, lod);
      c.renderLodSkirt(shader, lod);
    }
  }

  // ---------- Save helpers (delta-preserving, unchanged from earlier refactor) ----------

  // Back-compat shim if older code calls buildManifest()
  public MapResource.Manifest buildManifest() { return buildManifestMerged(); }

  /** Keep manifest stable; we only add coords we’re about to persist. */
  public MapResource.Manifest buildManifestMerged() {
    MapResource.Manifest src = map.getManifest();
    MapResource.Manifest m = new MapResource.Manifest();
    m.version = Math.max(src.version, src.groundTextures.isEmpty()?1:2);
    m.samplesPerSide = S; m.tile = tile; m.yScale = yScale; m.defaultTexture = src.defaultTexture;
    m.groundTextures = new ArrayList<>(src.groundTextures);

    m.chunks = new ArrayList<>(src.chunks);
    IntSet listed = new IntSet();
    for (int[] c : m.chunks) listed.add(key(c[0], c[1]));

    for (IntMap.Entry<TerrainChunk> e : chunks) {
      if (!shouldPersist(e.key, e.value)) continue;
      int cx = Math.round(e.value.getOriginX() / ((S - 1) * tile));
      int cz = Math.round(e.value.getOriginZ() / ((S - 1) * tile));
      int k = key(cx, cz);
      if (!listed.contains(k)) { m.chunks.add(new int[]{cx, cz}); listed.add(k); }
    }
    m.chunkCount = m.chunks.size();
    return m;
  }

  /** Only returns HMXS for chunks we actually want to persist. */
  public Map<String, byte[]> encodeChunksToHmx() {
    Map<String, byte[]> out = new LinkedHashMap<>();
    for (IntMap.Entry<TerrainChunk> e : chunks) {
      if (!shouldPersist(e.key, e.value)) continue;
      TerrainChunk c = e.value;
      int cx = Math.round(c.getOriginX() / ((S - 1) * tile));
      int cz = Math.round(c.getOriginZ() / ((S - 1) * tile));
      try {
        out.put(cx + "_" + cz + ".hmx", SparseChunkWriter.encodeToBytes(S, c.getHeightsArray(), c.getTexIdsArray()));
      } catch (IOException ex) {
        throw new RuntimeException("Encode failed " + cx + "," + cz, ex);
      }
    }
    return out;
  }

  private boolean shouldPersist(int k, TerrainChunk c) {
    if (c == null) return false;
    if (c.isDirty()) return true;
    return !defaultFlatChunks.contains(k);
  }

  /** Delta save: only write/overwrite provided chunks/textures; keep everything else in the ZIP. */
  public void saveAll(String path) {
    var manifest = buildManifestMerged();
    var chunksToWrite = encodeChunksToHmx();
    map.saveDeltaIncludingStaged(path, manifest, chunksToWrite);
  }

  // ---------- Accessors / cleanup ----------

  public void setViewRadius(int r) { this.viewRadius = Math.max(0, r); }
  public boolean hasChunk(int cx, int cz) { return chunks.containsKey(key(cx, cz)); }
  public MapResource getMap() { return map; }

  public void dispose() {
    for (IntMap.Entry<TerrainChunk> e : chunks) e.value.dispose();
    chunks.clear(); defaultFlatChunks.clear(); pending.clear(); completed.clear();
    if (groundTA != null) { groundTA.dispose(); groundTA = null; }
    pool.shutdownNow();
  }
}
