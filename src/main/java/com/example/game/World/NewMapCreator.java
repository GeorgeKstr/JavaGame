package com.example.game.World;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class NewMapCreator {

  // Creates maps/MyLevel.map with a single 64x64 chunk at (0,0)
  public static void main(String[] args) {
    try {
      createBlankMap("maps/MyLevel.map", 64, 1f, 0.5f, 0, 0, 1, 1);
      System.out.println("Created maps/MyLevel.map");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /** Create a blank map with a rectangle of chunks starting at (startCx,startCz) of size (w x h). */
  public static void createBlankMap(String path, int S, float tile, float yScale,
                                    int startCx, int startCz, int w, int h) throws IOException {
    // manifest
    MapResource.Manifest man = new MapResource.Manifest();
    man.version = 1;
    man.samplesPerSide = S;
    man.tile = tile;
    man.yScale = yScale;
    man.defaultTexture = 0;
    man.chunks = new ArrayList<>();

    // blank chunk data
    int samples = S * S;
    short[] heights = new short[samples]; // all zeros (baseline)
    byte[] texIds   = new byte[samples];  // all zeros (default texture)

    // encode once; reuse for each chunk because it's identical
    byte[] hmx = SparseChunkWriter.encodeToBytes(S, heights, texIds);

    // collect chunk files
    Map<String, byte[]> files = new HashMap<>();
    for (int dz = 0; dz < h; dz++) {
      for (int dx = 0; dx < w; dx++) {
        int cx = startCx + dx, cz = startCz + dz;
        man.chunks.add(new int[]{cx, cz});
        files.put(cx + "_" + cz + ".hmx", hmx);
      }
    }
    man.chunkCount = man.chunks.size();

    // ensure folder exists then save
    Files.createDirectories(Paths.get("maps"));
    MapResource.save(path, man, files);
  }
}
