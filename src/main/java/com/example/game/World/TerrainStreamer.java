package com.example.game.World;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.IntMap;

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
        final int samples = w.S * w.S;

        if (hmx == null) {
          // ----- default flat chunk -----
          int dt = w.map.getManifest().defaultTexture;
          short[] h = new short[samples];

          // default: id0 = defaultTexture, w0 = 255; others 0
          byte[] id0 = new byte[samples];
          byte[] id1 = new byte[samples];
          byte[] id2 = new byte[samples];
          byte[] w0  = new byte[samples];
          byte[] w1  = new byte[samples];
          byte[] w2  = new byte[samples];

          byte defId = (byte)(dt & 0xFF);
          Arrays.fill(id0, defId);
          Arrays.fill(w0, (byte)255);
          // id1/id2/w1/w2 left at 0

          // Preserve a baseline copy before post-process (original terrain)
          short[] baseH = java.util.Arrays.copyOf(h, h.length);

          // Optional background post-process (e.g., rivers bake) before passing to render thread
          if (w.chunkPost != null) {
            int cx2 = (int)Math.floor(ox / ((w.S - 1) * w.tile));
            int cz2 = (int)Math.floor(oz / ((w.S - 1) * w.tile));
            try { w.chunkPost.processChunk(cx2, cz2, ox, oz, h, baseH, w.S, w.tile, w.yScale); } catch (Throwable ignored) {}
          }

          w.completed.add(new TerrainWorld.Result(k, ox, oz, h, baseH, id0, id1, id2, w0, w1, w2, true, null));
          return;
        }

        // ----- load from HMXS -----
        var lc = w.provider.loadFromBytes(hmx);

        // If your provider is the v2 one I gave you, it returns the six arrays already.
        // If you still have legacy texIds[], up-convert here.
        byte[] id0, id1, id2, w0, w1, w2;
        short[] h = lc.heights;

        if (lc instanceof SparseChunkProvider.LoadedChunk) {
          // Using the v2 LoadedChunk with six arrays
          id0 = ((SparseChunkProvider.LoadedChunk) lc).id0;
          id1 = ((SparseChunkProvider.LoadedChunk) lc).id1;
          id2 = ((SparseChunkProvider.LoadedChunk) lc).id2;
          w0  = ((SparseChunkProvider.LoadedChunk) lc).w0;
          w1  = ((SparseChunkProvider.LoadedChunk) lc).w1;
          w2  = ((SparseChunkProvider.LoadedChunk) lc).w2;
        } else {
          // Fallback: legacy `texIds` only → make w0=255, others 0
          // (If your provider class name differs, adjust this block or remove it.)
          byte[] texIds = (byte[])TerrainReflection.get(lc, "texIds"); // tiny helper; or just change your provider.
          id0 = new byte[samples];
          id1 = new byte[samples];
          id2 = new byte[samples];
          w0  = new byte[samples];
          w1  = new byte[samples];
          w2  = new byte[samples];
          System.arraycopy(texIds, 0, id0, 0, samples);
          Arrays.fill(w0, (byte)255);
        }

        // Preserve a baseline copy before post-process (original terrain)
        short[] baseH = java.util.Arrays.copyOf(h, h.length);

        // Optional background post-process (e.g., rivers bake) before passing to render thread
        if (w.chunkPost != null) {
          int cx2 = (int)Math.floor(ox / ((w.S - 1) * w.tile));
          int cz2 = (int)Math.floor(oz / ((w.S - 1) * w.tile));
          try { w.chunkPost.processChunk(cx2, cz2, ox, oz, h, baseH, w.S, w.tile, w.yScale); } catch (Throwable ignored) {}
        }

        w.completed.add(new TerrainWorld.Result(k, ox, oz, h, baseH, id0, id1, id2, w0, w1, w2, false, null));

      } catch (Throwable ex) {
        // Pass null arrays to signal failure
        w.completed.add(new TerrainWorld.Result(k, ox, oz, null, null, null, null, null, null, null, null, false, ex));
      }
    });
  }

  private void drainCompleted() {
    for (TerrainWorld.Result r; (r = w.completed.poll()) != null; ) {
      w.pending.remove(r.k);
      if (r.err != null || r.h == null || r.id0 == null || r.w0 == null) {
        Gdx.app.error("TerrainSystem", "Chunk load failed k=" + r.k, r.err);
        continue;
      }

      // Prefer a constructor that accepts the six arrays.
      // If your TerrainChunk lacks it, construct with heights-only and then set arrays via setters.
      TerrainChunk tc = new TerrainChunk(
          w.S, w.tile, w.yScale, r.ox, r.oz,
          r.h, r.id0, r.id1, r.id2, r.w0, r.w1, r.w2
      );
      // Install baseline so tools/ribbons can sample original terrain
      if (r.baseH != null) tc.setBaselineHeights(r.baseH);

      tc.buildLods(w.MAX_LOD);
      w.chunks.put(r.k, tc);
      if (r.defaultFlat) w.defaultFlatChunks.add(r.k); else w.defaultFlatChunks.remove(r.k);
      // Notify listeners (render thread) that chunks changed so tools/ribbons can resample
      if (w.onChunksChanged != null) try { w.onChunksChanged.run(); } catch (Throwable ignored) {}
    }
  }

  // --- optional tiny helper if you briefly need reflection fallback (remove once all code is v2) ---
  private static final class TerrainReflection {
    static Object get(Object o, String field) {
      try {
        var f = o.getClass().getDeclaredField(field);
        f.setAccessible(true);
        return f.get(o);
      } catch (Throwable t) {
        return null;
      }
    }
  }
}
