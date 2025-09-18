package com.example.game.World;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * SparseChunkWriterV2
 * Writes HMXS chunk files.
 *
 * Rules:
 * - baseline = 0
 * - only non-zero heights and non-default textures are written
 * - S, tile, yScale are global constants in your map manifest
 */
public class SparseChunkWriter {

  /** Write to a .hmx file on disk. */
  public static void write(String path, int S, short[] heights, byte[] texIds) throws IOException {
    byte[] blob = encodeToBytes(S, heights, texIds);
    Files.createDirectories(Paths.get(path).getParent());
    Files.write(Paths.get(path), blob);
  }

  /** Encode to byte array (for saving into a MapResource ZIP). */
  public static byte[] encodeToBytes(int S, short[] heights, byte[] texIds) throws IOException {
    if (heights.length != S*S || texIds.length != S*S)
      throw new IllegalArgumentException("Array length mismatch");

    int samples = S * S;
    byte[] hbm = new byte[(samples + 7) >> 3];
    byte[] tbm = new byte[(samples + 7) >> 3];

    for (int i = 0; i < samples; i++) {
      if (heights[i] != 0) hbm[i >> 3] |= (1 << (i & 7));
      if ((texIds[i] & 0xFF) != 0) tbm[i >> 3] |= (1 << (i & 7));
    }

    ByteArrayOutputStream raw = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(raw);

    // magic 'HMXS' little-endian
    out.writeInt(Integer.reverseBytes(0x484D5853));
    out.writeByte(1); // version

    // heights
    out.write(hbm);
    for (int i = 0; i < samples; i++) {
      if ((hbm[i >> 3] & (1 << (i & 7))) != 0)
        out.writeShort(Short.reverseBytes(heights[i]));
    }

    // textures
    out.write(tbm);
    for (int i = 0; i < samples; i++) {
      if ((tbm[i >> 3] & (1 << (i & 7))) != 0)
        out.writeByte(texIds[i] & 0xFF);
    }

    out.flush();
    return raw.toByteArray();
  }
}
