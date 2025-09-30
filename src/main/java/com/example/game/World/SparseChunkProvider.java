package com.example.game.World;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

import java.io.*;

/**
 * SparseChunkProviderV2
 * Reads simplified HMXS (HeightMap eXtra Sparse) chunk files.
 *
 * Format v2 (little-endian):
 *   u32 magic = 'HMXS'
 *   u8  version = 2
 *   u8  h_bitmap[ceil(S*S/8)]
 *   i16 h_values[popcount(h_bitmap)]
 *   u8  t_bitmap[ceil(S*S/8)]
 *   struct TexRec { u8 id0, id1, id2, u8 w0, w1, w2; } t_values[popcount(t_bitmap)]
 *
 * Format v1 (legacy, read-only):
 *   u32 magic = 'HMXS'
 *   u8  version = 1
 *   u8  h_bitmap[ceil(S*S/8)]
 *   i16 h_values[popcount(h_bitmap)]
 *   u8  t_bitmap[ceil(S*S/8)]
 *   u8  t_values[popcount(t_bitmap)]  // single id per touched sample
 *
 * Defaults (both versions):
 *   heights baseline = 0
 *   textures baseline = id0=0,w0=255; id1=0,w1=0; id2=0,w2=0
 */
public class SparseChunkProvider {
  private final int S; // samples per side

  public SparseChunkProvider(String baseDir, int S) {
    this.S = S;
  }

  /** Load from a file on disk (classpath/local). */
  public LoadedChunk load(String path) {
    FileHandle fh = Gdx.files.internal(path);
    if (!fh.exists()) {
      return emptyChunk(S);
    }
    return loadFromBytes(fh.readBytes());
  }

  /** Load from a raw byte array (e.g. from MapResource ZIP). */
  public LoadedChunk loadFromBytes(byte[] bytes) {
    try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
      int magic = Integer.reverseBytes(in.readInt());
      if (magic != 0x484D5853) throw new IOException("Bad magic (expected HMXS)");
      int version = in.readUnsignedByte();

      int samples = S * S;

      // Heights (both versions share this part)
      byte[] hbm = readNBytes(in, (samples + 7) >> 3);
      short[] heights = new short[samples];
      for (int i = 0; i < samples; i++) {
        if (((hbm[i >> 3] >> (i & 7)) & 1) != 0) {
          short v = Short.reverseBytes(in.readShort());
          heights[i] = v;
        } else {
          heights[i] = 0; // baseline
        }
      }

      if (version == 1) {
        // --- legacy single-ID stream ---
        byte[] tbm = readNBytes(in, (samples + 7) >> 3);

        byte[] id0 = new byte[samples];
        byte[] id1 = new byte[samples];
        byte[] id2 = new byte[samples];
        byte[] w0  = new byte[samples];
        byte[] w1  = new byte[samples];
        byte[] w2  = new byte[samples];

        // defaults: id0=0,w0=255; others 0
        for (int i = 0; i < samples; i++) {
          id0[i] = 0; id1[i] = 0; id2[i] = 0;
          w0[i] = (byte)255; w1[i] = 0; w2[i] = 0;
        }

        for (int i = 0; i < samples; i++) {
          if (((tbm[i >> 3] >> (i & 7)) & 1) != 0) {
            int tex = in.readUnsignedByte();
            id0[i] = (byte)tex;
            // keep w0=255 (full coverage)
          }
        }

        return new LoadedChunk(heights, id0, id1, id2, w0, w1, w2);
      }

      if (version != 2) throw new IOException("Unsupported version: " + version);

      // --- v2 multi-layer stream ---
      byte[] tbm = readNBytes(in, (samples + 7) >> 3);

      byte[] id0 = new byte[samples];
      byte[] id1 = new byte[samples];
      byte[] id2 = new byte[samples];
      byte[] w0  = new byte[samples];
      byte[] w1  = new byte[samples];
      byte[] w2  = new byte[samples];

      // defaults: id0=0,w0=255; others 0
      for (int i = 0; i < samples; i++) {
        id0[i] = 0; id1[i] = 0; id2[i] = 0;
        w0[i] = (byte)255; w1[i] = 0; w2[i] = 0;
      }

      for (int i = 0; i < samples; i++) {
        if (((tbm[i >> 3] >> (i & 7)) & 1) != 0) {
          id0[i] = (byte) in.readUnsignedByte();
          id1[i] = (byte) in.readUnsignedByte();
          id2[i] = (byte) in.readUnsignedByte();
          w0[i]  = (byte) in.readUnsignedByte();
          w1[i]  = (byte) in.readUnsignedByte();
          w2[i]  = (byte) in.readUnsignedByte();
        }
      }

      return new LoadedChunk(heights, id0, id1, id2, w0, w1, w2);

    } catch (IOException e) {
      throw new RuntimeException("Failed to load HMXS chunk", e);
    }
  }

  private static byte[] readNBytes(DataInputStream in, int n) throws IOException {
    byte[] buf = new byte[n];
    in.readFully(buf);
    return buf;
  }

  private static LoadedChunk emptyChunk(int S) {
    int samples = S * S;
    short[] heights = new short[samples];
    byte[] id0 = new byte[samples];
    byte[] id1 = new byte[samples];
    byte[] id2 = new byte[samples];
    byte[] w0  = new byte[samples];
    byte[] w1  = new byte[samples];
    byte[] w2  = new byte[samples];
    // defaults
    for (int i = 0; i < samples; i++) {
      heights[i] = 0;
      id0[i] = 0; id1[i] = 0; id2[i] = 0;
      w0[i] = (byte)255; w1[i] = 0; w2[i] = 0;
    }
    return new LoadedChunk(heights, id0, id1, id2, w0, w1, w2);
  }

  /** Holds decoded chunk arrays. */
  public static class LoadedChunk {
    public final short[] heights; // length = S*S
    public final byte[] id0, id1, id2;
    public final byte[] w0, w1, w2;

    public LoadedChunk(short[] heights, byte[] id0, byte[] id1, byte[] id2, byte[] w0, byte[] w1, byte[] w2) {
      this.heights = heights;
      this.id0 = id0; this.id1 = id1; this.id2 = id2;
      this.w0 = w0;   this.w1 = w1;   this.w2 = w2;
    }
  }
}
