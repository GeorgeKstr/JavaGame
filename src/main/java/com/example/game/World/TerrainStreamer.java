package com.example.game.World;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntSet;

import java.util.Arrays;

final class TerrainStreamer {
  private final TerrainWorld w;

  TerrainStreamer(TerrainWorld w) { this.w = w; }

  void updateAndStream(float wx, float wz) {
    final float size = w.chunkWorldSize();
    float radiusMeters = Math.max(0f, w.viewRadius) * size;

    int pcx = (int)Math.floor(wx / size);
    int pcz = (int)Math.floor(wz / size);
    int rChunks = Math.max(0, (int)Math.ceil(radiusMeters / size));

    com.badlogic.gdx.utils.Array<Integer> needed = new com.badlogic.gdx.utils.Array<>();

    for (int dz = -rChunks; dz <= rChunks; dz++) {
      for (int dx = -rChunks; dx <= rChunks; dx++) {
        int cx = pcx + dx, cz = pcz + dz;
        if (!isChunkWithinRadius(cx, cz, wx, wz, radiusMeters)) continue;

        int k = TerrainWorld.key(cx, cz);
        needed.add(k);
        if (!w.chunks.containsKey(k) && !w.pending.contains(k)) enqueueLoad(cx, cz);
      }
    }

    drainCompleted();

    // Unload
    com.badlogic.gdx.utils.Array<Integer> toRemove = new com.badlogic.gdx.utils.Array<>();
    for (IntMap.Entry<TerrainChunk> e : w.chunks) {
      if (!needed.contains(e.key, false)) toRemove.add(e.key);
    }
    for (Integer k : toRemove) {
      TerrainChunk c = w.chunks.remove(k);
      if (c != null) c.dispose();
      w.defaultFlatChunks.remove(k);
      // w.lastLod.remove(k); // optional
    }
  }

  private boolean isChunkWithinRadius(int cx, int cz, float px, float pz, float rMeters) {
    float size = w.chunkWorldSize();
    float hw = size * 0.5f;
    float centerX = cx * size + hw;
    float centerZ = cz * size + hw;
    float dx = centerX - px;
    float dz = centerZ - pz;
    return (dx * dx + dz * dz) <= (rMeters * rMeters);
  }

  private void enqueueLoad(int cx, int cz) {
    final int k = TerrainWorld.key(cx, cz);
    w.pending.add(k);

    final float ox = cx * (w.S - 1) * w.tile;
    final float oz = cz * (w.S - 1) * w.tile;

    w.pool.submit(() -> {
      try {
        byte[] hmx = w.map.readChunkHmx(cx, cz);
        if (hmx == null) {
          int dt = w.map.getManifest().defaultTexture;
          short[] h = new short[w.S * w.S];
          byte[]  t = new byte[w.S * w.S];
          byte def = (byte)(dt & 0xFF);
          if (def != 0) Arrays.fill(t, def);
          w.completed.add(new TerrainWorld.Result(k, ox, oz, h, t, true, null));
        } else {
          var lc = w.provider.loadFromBytes(hmx);
          w.completed.add(new TerrainWorld.Result(k, ox, oz, lc.heights, lc.texIds, false, null));
        }
      } catch (Throwable ex) {
        w.completed.add(new TerrainWorld.Result(k, ox, oz, null, null, false, ex));
      }
    });
  }

  private void drainCompleted() {
    for (TerrainWorld.Result r; (r = w.completed.poll()) != null; ) {
      w.pending.remove(r.k);
      if (r.err != null || r.h == null || r.t == null) {
        Gdx.app.error("TerrainSystem", "Chunk load failed k=" + r.k, r.err);
        continue;
      }
      var tc = new TerrainChunk(w.S, w.tile, w.yScale, r.ox, r.oz, r.h, r.t);
      tc.buildLods(w.MAX_LOD);
      w.chunks.put(r.k, tc);
      if (r.defaultFlat) w.defaultFlatChunks.add(r.k); else w.defaultFlatChunks.remove(r.k);
    }
  }
}
