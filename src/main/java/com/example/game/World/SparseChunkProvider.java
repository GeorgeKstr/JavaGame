package com.example.game.World;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

import java.io.*;

/**
 * SparseChunkProviderV2
 * Reads simplified HMXS (HeightMap eXtra Sparse) chunk files.
 *
 * Format (little-endian):
 *   u32 magic = 'HMXS'
 *   u8  version = 1
 *   u8  h_bitmap[ceil(S*S/8)]
 *   i16 h_values[popcount(h_bitmap)]
 *   u8  t_bitmap[ceil(S*S/8)]
 *   u8  t_values[popcount(t_bitmap)]
 *
 * - S, tile, yScale are global (passed in constructor).
 * - baseline = 0
 * - 0 = default texture
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
      return new LoadedChunk(new short[S*S], new byte[S*S]);
    }
    return loadFromBytes(fh.readBytes());
  }

  /** Load from a raw byte array (e.g. from MapResource ZIP). */
  public LoadedChunk loadFromBytes(byte[] bytes) {
    try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
      int magic = Integer.reverseBytes(in.readInt());
      if (magic != 0x484D5853) throw new IOException("Bad magic (expected HMXS)");
      int version = in.readUnsignedByte();
      if (version != 1) throw new IOException("Unsupported version: " + version);

      int samples = S * S;

      // Heights
      byte[] hbm = in.readNBytes((samples + 7) >> 3);
      short[] heights = new short[samples];
      for (int i = 0; i < samples; i++) {
        if (((hbm[i >> 3] >> (i & 7)) & 1) != 0) {
          short v = Short.reverseBytes(in.readShort());
          heights[i] = v;
        }
      }

      // Textures
      byte[] tbm = in.readNBytes((samples + 7) >> 3);
      byte[] texIds = new byte[samples];
      for (int i = 0; i < samples; i++) {
        if (((tbm[i >> 3] >> (i & 7)) & 1) != 0) {
          texIds[i] = (byte) in.readUnsignedByte();
        }
      }

      return new LoadedChunk(heights, texIds);
    } catch (IOException e) {
      throw new RuntimeException("Failed to load HMXS chunk", e);
    }
  }

  /** Holds decoded chunk arrays. */
  public static class LoadedChunk {
    public final short[] heights; // length = S*S
    public final byte[] texIds;   // length = S*S
    public LoadedChunk(short[] h, byte[] t) { this.heights = h; this.texIds = t; }
  }

}
