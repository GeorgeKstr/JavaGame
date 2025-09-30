package com.example.game.World;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.TextureArray;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.*;

final class TerrainWorld {
  // Config
  final int S;
  final float tile, yScale;
  volatile int viewRadius = 3;

  // Data
  final MapResource map;
  final SparseChunkProvider provider;
  final IntMap<TerrainChunk> chunks = new IntMap<>();
  final IntSet defaultFlatChunks = new IntSet(); // auto-made, untouched → don’t save

  // Streaming
  final ExecutorService pool;
  final ConcurrentLinkedQueue<Result> completed = new ConcurrentLinkedQueue<>();
  final IntSet pending = new IntSet();

  // Rendering (textures)
  TextureArray groundTA;
  final float[] uvScales = new float[256];

  // LOD
  int MAX_LOD = 0;
  float[] lodDistances = new float[]{ 50f, 130f, 260f, 520f };
  float hysteresis = 12f;
  final IntMap<Integer> lastLod = new IntMap<>();

  TerrainWorld(MapResource map) {
    this.map = map;
    var mf = map.getManifest();
    this.S = mf.samplesPerSide;
    this.tile = mf.tile;
    this.yScale = mf.yScale;
    this.provider = new SparseChunkProvider("ignored", S);

    // Compute MAX_LOD so that (S-1) % (1<<lod) == 0
    int cells = S - 1, lod = 0;
    while (((cells % (1 << (lod + 1))) == 0) && lod < 6) lod++;
    this.MAX_LOD = lod;

    int n = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    this.pool = Executors.newFixedThreadPool(n, r -> { var t = new Thread(r, "ChunkIO"); t.setDaemon(true); return t; });

    if (!mf.groundTextures.isEmpty()) {
      this.groundTA = map.buildGroundTextureArray();
      Arrays.fill(uvScales, 1f);
      for (MapResource.TextureRef tr : mf.groundTextures) {
        if (tr.id >= 0 && tr.id < uvScales.length) uvScales[tr.id] = (tr.uvScale == 0f ? 1f : tr.uvScale);
      }
    }
  }

  static int key(int cx, int cz) { return (cx & 0xFFFF) | (cz << 16); }
  int cellsPerChunk() { return S - 1; }
  float chunkWorldSize() { return (S - 1) * tile; }

  float sampleHeightAtWorld(float x, float z, float def) {
    int cpc = cellsPerChunk();
    int cx = (int)Math.floor(x / (cpc * tile));
    int cz = (int)Math.floor(z / (cpc * tile));
    TerrainChunk c = chunks.get(key(cx, cz));
    if (c == null) return def;
    float h = c.heightAtWorld(x, z);
    return Float.isNaN(h) ? def : h;
  }

  void disposeWorld() {
    for (IntMap.Entry<TerrainChunk> e : chunks) e.value.dispose();
    chunks.clear(); defaultFlatChunks.clear(); pending.clear(); completed.clear();
    if (groundTA != null) { groundTA.dispose(); groundTA = null; }
    pool.shutdownNow();
  }

  public static final class Result {
    public final int k; public final float ox, oz;
    public final short[] h;
    public final byte[] id0, id1, id2, w0, w1, w2;
    public final boolean defaultFlat;
    public final Throwable err;

    public Result(int k, float ox, float oz,
                  short[] h,
                  byte[] id0, byte[] id1, byte[] id2,
                  byte[] w0,  byte[] w1,  byte[] w2,
                  boolean defaultFlat, Throwable err) {
      this.k=k; this.ox=ox; this.oz=oz; this.h=h;
      this.id0=id0; this.id1=id1; this.id2=id2;
      this.w0=w0;   this.w1=w1;   this.w2=w2;
      this.defaultFlat=defaultFlat; this.err=err;
    }
  }

}
