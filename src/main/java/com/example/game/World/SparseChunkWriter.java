package com.example.game.World;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * SparseChunkWriterV2
 * Writes HMXS chunk files.
 *
 * Version 2 Format:
 *   u32 'HMXS'
 *   u8  version = 2
 *   u8  h_bitmap[ceil(S*S/8)]
 *   i16 h_values[popcount(h_bitmap)]
 *   u8  t_bitmap[ceil(S*S/8)]
 *   struct TexRec { u8 id0,id1,id2, u8 w0,w1,w2; } t_values[popcount(t_bitmap)]
 *
 * Rules:
 * - heights baseline = 0 (only non-zero written)
 * - textures baseline = id0=0,w0=255; id1=0,w1=0; id2=0,w2=0
 *   We write a TexRec only if the sample differs from baseline.
 *
 * NOTE: A legacy convenience method is kept for (S, heights, texIds) calls.
 *       It encodes them as full id0 with w0=255, others zero (v2 container).
 */
public class SparseChunkWriter {

  /** Write to a .hmx file on disk (v2). */
  public static void write(String path, int S, short[] heights,
                           byte[] id0, byte[] id1, byte[] id2,
                           byte[] w0, byte[] w1, byte[] w2) throws IOException {
    byte[] blob = encodeToBytes(S, heights, id0, id1, id2, w0, w1, w2);
    Files.createDirectories(Paths.get(path).getParent());
    Files.write(Paths.get(path), blob);
  }

  /** Encode to byte array (v2). */
  public static byte[] encodeToBytes(int S, short[] heights,
                                     byte[] id0, byte[] id1, byte[] id2,
                                     byte[] w0, byte[] w1, byte[] w2) throws IOException {
    int samples = S * S;
    if (heights.length != samples ||
        id0.length != samples || id1.length != samples || id2.length != samples ||
        w0.length  != samples || w1.length  != samples || w2.length  != samples) {
      throw new IllegalArgumentException("Array length mismatch");
    }

    byte[] hbm = new byte[(samples + 7) >> 3];
    byte[] tbm = new byte[(samples + 7) >> 3];

    // Heights bitmap
    for (int i = 0; i < samples; i++) {
      if (heights[i] != 0) hbm[i >> 3] |= (1 << (i & 7));
    }

    // Textures bitmap: mark only if differs from baseline (id0=0,w0=255; id1=0,w1=0; id2=0,w2=0)
    for (int i = 0; i < samples; i++) {
      int bId0 = id0[i] & 0xFF, bId1 = id1[i] & 0xFF, bId2 = id2[i] & 0xFF;
      int bw0 = w0[i] & 0xFF, bw1 = w1[i] & 0xFF, bw2 = w2[i] & 0xFF;
      boolean isDefault = (bId0 == 0 && bId1 == 0 && bId2 == 0 && bw0 == 255 && bw1 == 0 && bw2 == 0);
      if (!isDefault) tbm[i >> 3] |= (1 << (i & 7));
    }

    ByteArrayOutputStream raw = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(raw);

    // magic 'HMXS' little-endian
    out.writeInt(Integer.reverseBytes(0x484D5853));
    out.writeByte(2); // version

    // heights
    out.write(hbm);
    for (int i = 0; i < samples; i++) {
      if ((hbm[i >> 3] & (1 << (i & 7))) != 0)
        out.writeShort(Short.reverseBytes(heights[i]));
    }

    // textures
    out.write(tbm);
    for (int i = 0; i < samples; i++) {
      if ((tbm[i >> 3] & (1 << (i & 7))) != 0) {
        out.writeByte(id0[i] & 0xFF);
        out.writeByte(id1[i] & 0xFF);
        out.writeByte(id2[i] & 0xFF);
        out.writeByte(w0[i] & 0xFF);
        out.writeByte(w1[i] & 0xFF);
        out.writeByte(w2[i] & 0xFF);
      }
    }

    out.flush();
    return raw.toByteArray();
  }

  // ---------- Legacy convenience (keeps your old call-sites compiling) ----------

  /** Write using legacy single-texId arrays (wrapped into v2 encoding with w0=255, others 0). */
  public static void write(String path, int S, short[] heights, byte[] texIds) throws IOException {
    byte[] blob = encodeToBytes(S, heights, texIds);
    Files.createDirectories(Paths.get(path).getParent());
    Files.write(Paths.get(path), blob);
  }

  /** Encode using legacy single-texId arrays (v2 container). */
  public static byte[] encodeToBytes(int S, short[] heights, byte[] texIds) throws IOException {
    int samples = S * S;
    if (heights.length != samples || texIds.length != samples)
      throw new IllegalArgumentException("Array length mismatch");

    // Up-convert to v2 buffers
    byte[] id0 = new byte[samples];
    byte[] id1 = new byte[samples];
    byte[] id2 = new byte[samples];
    byte[] w0  = new byte[samples];
    byte[] w1  = new byte[samples];
    byte[] w2  = new byte[samples];

    for (int i = 0; i < samples; i++) {
      id0[i] = texIds[i];
      id1[i] = 0; id2[i] = 0;
      w0[i] = (byte)255; w1[i] = 0; w2[i] = 0;
    }

    return encodeToBytes(S, heights, id0, id1, id2, w0, w1, w2);
  }
}
