package com.example.game.World;

import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntSet;

import java.io.IOException;
import java.util.*;

final class TerrainIO {
  private final TerrainWorld w;
  TerrainIO(TerrainWorld w){ this.w = w; }

  MapResource.Manifest buildManifestMerged() {
    MapResource.Manifest src = w.map.getManifest();
    MapResource.Manifest m = new MapResource.Manifest();
    m.version = Math.max(src.version, src.groundTextures.isEmpty()?1:2);
    m.samplesPerSide = w.S; m.tile = w.tile; m.yScale = w.yScale; m.defaultTexture = src.defaultTexture;
    m.groundTextures = new ArrayList<>(src.groundTextures);

    m.chunks = new ArrayList<>(src.chunks);
    IntSet listed = new IntSet();
    for (int[] c : m.chunks) listed.add(TerrainWorld.key(c[0], c[1]));

    for (IntMap.Entry<TerrainChunk> e : w.chunks) {
      if (!shouldPersist(e.key, e.value)) continue;
      int cx = Math.round(e.value.getOriginX() / ((w.S - 1) * w.tile));
      int cz = Math.round(e.value.getOriginZ() / ((w.S - 1) * w.tile));
      int k = TerrainWorld.key(cx, cz);
      if (!listed.contains(k)) { m.chunks.add(new int[]{cx, cz}); listed.add(k); }
    }
    m.chunkCount = m.chunks.size();
    return m;
  }

  java.util.Map<String, byte[]> encodeChunksToHmx() {
    Map<String, byte[]> out = new LinkedHashMap<>();
    for (IntMap.Entry<TerrainChunk> e : w.chunks) {
      if (!shouldPersist(e.key, e.value)) continue;
      TerrainChunk c = e.value;
      int cx = Math.round(c.getOriginX() / ((w.S - 1) * w.tile));
      int cz = Math.round(c.getOriginZ() / ((w.S - 1) * w.tile));
      try {
        // Save baseline (pre-bake) heights so rivers remain runtime offsets. If no baseline exists yet, fallback to current heights.
        short[] base = c.getBaselineArray();
        short[] toWrite = (base != null && base.length == w.S * w.S) ? base : c.getHeightsArray();
        out.put(cx + "_" + cz + ".hmx", SparseChunkWriter.encodeToBytes(w.S, toWrite, c.getTexId0Array(), c.getTexId1Array(), c.getTexId2Array(), c.getTexW0Array(), c.getTexW1Array(), c.getTexW2Array()));
      } catch (IOException ex) {
        throw new RuntimeException("Encode failed " + cx + "," + cz, ex);
      }
    }
    return out;
  }

  void saveAll(String path) {
    var manifest = buildManifestMerged();
    var chunksToWrite = encodeChunksToHmx();
    w.map.saveDeltaIncludingStaged(path, manifest, chunksToWrite);
  }

  private boolean shouldPersist(int k, TerrainChunk c) {
    if (c == null) return false;
    if (c.isDirty()) return true;
    return !w.defaultFlatChunks.contains(k);
  }
}
