package com.example.game.World;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.io.*;
import java.util.*;
import java.util.zip.*;

public class MapResource {
  public static class Manifest {
    public int version, samplesPerSide, chunkCount;
    public float tile, yScale;
    public int defaultTexture;
    public List<int[]> chunks = new ArrayList<>(); // [cx,cz]
  }

  private final Manifest manifest;
  private final byte[] bytes; // entire .map in memory (simple & fast)

  private MapResource(Manifest m, byte[] bytes) {
    this.manifest = m; this.bytes = bytes;
  }

  public Manifest getManifest() { return manifest; }

  public static MapResource loadFromFile(String path) {
    try {
      byte[] data = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path));
      return fromBytes(data);
    } catch (IOException e) {
      throw new RuntimeException("Failed to read map: " + path, e);
    }
  }

  public static MapResource fromBytes(byte[] data) {
    try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(data))) {
      Manifest m = null;
      Map<String, byte[]> entries = new HashMap<>();
      ZipEntry e;
      while ((e = zin.getNextEntry()) != null) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        zin.transferTo(out);
        entries.put(e.getName(), out.toByteArray());
      }
      byte[] man = entries.get("manifest.json");
      if (man == null) throw new IOException("manifest.json missing");
      JsonValue root = new JsonReader().parse(new String(man, java.nio.charset.StandardCharsets.UTF_8));
      m = new Manifest();
      m.version = root.getInt("version", 1);
      m.samplesPerSide = root.getInt("samplesPerSide");
      m.tile = root.getFloat("tile");
      m.yScale = root.getFloat("yScale");
      m.defaultTexture = root.getInt("defaultTexture", 0);
      m.chunkCount = root.getInt("chunkCount", 0);
      for (JsonValue c : root.get("chunks")) {
        m.chunks.add(new int[]{ c.getInt("cx"), c.getInt("cz") });
      }
      // Keep original bytes for saving back (optional)
      return new MapResource(m, data);
    } catch (IOException ex) {
      throw new RuntimeException("Invalid .map file", ex);
    }
  }

  /** Reads a chunk entry (HMXS) by coord; returns null if not present. */
  public byte[] readChunkHmx(int cx, int cz) {
    try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(bytes))) {
      String want = "chunks/" + cx + "_" + cz + ".hmx";
      ZipEntry e;
      while ((e = zin.getNextEntry()) != null) {
        if (e.getName().equals(want)) {
          ByteArrayOutputStream out = new ByteArrayOutputStream();
          zin.transferTo(out);
          return out.toByteArray();
        }
      }
      return null;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /** Save a new .map from current in-memory chunk data. */
  public static void save(String path, Manifest m, Map<String, byte[]> chunkFiles) {
    try (ZipOutputStream zout = new ZipOutputStream(new BufferedOutputStream(
            java.nio.file.Files.newOutputStream(java.nio.file.Paths.get(path))))) {
      // write manifest
      zout.putNextEntry(new ZipEntry("manifest.json"));
      String json = toJson(m);
      zout.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      zout.closeEntry();

      // write chunks
      for (Map.Entry<String, byte[]> en : chunkFiles.entrySet()) {
        zout.putNextEntry(new ZipEntry("chunks/" + en.getKey()));
        zout.write(en.getValue());
        zout.closeEntry();
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to save map " + path, e);
    }
  }

  private static String toJson(Manifest m) {
    StringBuilder sb = new StringBuilder(256);
    sb.append("{\"version\":").append(m.version)
      .append(",\"samplesPerSide\":").append(m.samplesPerSide)
      .append(",\"tile\":").append(m.tile)
      .append(",\"yScale\":").append(m.yScale)
      .append(",\"defaultTexture\":").append(m.defaultTexture)
      .append(",\"chunkCount\":").append(m.chunks.size())
      .append(",\"chunks\":[");
    for (int i=0;i<m.chunks.size();i++){
      int[] c=m.chunks.get(i);
      if(i>0) sb.append(',');
      sb.append("{\"cx\":").append(c[0]).append(",\"cz\":").append(c[1]).append('}');
    }
    sb.append("]}");
    return sb.toString();
  }
}
