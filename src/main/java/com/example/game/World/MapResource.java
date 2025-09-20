package com.example.game.World;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.TextureArray;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Portable world .map file (ZIP) with:
 *  - manifest.json
 *  - chunks/*.hmx
 *  - textures/ground/*.png (optional)
 *
 * Manifest v1: original fields only
 * Manifest v2: adds textures.ground[] list
 *
 * This version owns:
 *  - Texture importing (adds to manifest + stages bytes in-memory)
 *  - Providing texture bytes (prefers staged over ZIP)
 *  - Convenience save that includes staged textures
 */
public class MapResource {

  // ----------------------------
  // Manifest & model
  // ----------------------------

  /** Ground texture reference inside the map ZIP. */
  public static class TextureRef {
    public int id;           // array layer index
    public String name;      // human readable
    public String file;      // "textures/ground/xxx.png"
    public float uvScale = 1f;

    @Override public String toString() {
      return "TextureRef{id=" + id + ", name='" + name + "', file='" + file + "', uvScale=" + uvScale + "}";
    }
  }

  public static class Manifest {
    public int version = 1;
    public int samplesPerSide, chunkCount;
    public float tile, yScale;
    public int defaultTexture;
    public List<int[]> chunks = new ArrayList<>();              // [cx,cz]
    public List<TextureRef> groundTextures = new ArrayList<>(); // v2+
  }

  private final Manifest manifest;
  private byte[] bytes; // entire .map in memory (simple & fast)

  // NEW: staged textures not yet written to the .map file on disk
  private final Map<String, byte[]> stagedTextures = new LinkedHashMap<>();

  private MapResource(Manifest m, byte[] bytes) {
    this.manifest = m;
    this.bytes = bytes;
  }

  public Manifest getManifest() { return manifest; }

  // ----------------------------
  // Loading / parsing
  // ----------------------------

  public static MapResource loadFromFile(String path) {
    try {
      byte[] data = Files.readAllBytes(Paths.get(path));
      return fromBytes(data);
    } catch (IOException e) {
      throw new RuntimeException("Failed to read map: " + path, e);
    }
  }

  /** Reloads the raw ZIP bytes (useful during editing). (Staged textures remain untouched.) */
  public void reload(String path) {
    try {
      bytes = Files.readAllBytes(Paths.get(path));
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static MapResource fromBytes(byte[] data) {
    try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(data))) {
      Manifest m;
      Map<String, byte[]> entries = new HashMap<>();
      ZipEntry e;
      while ((e = zin.getNextEntry()) != null) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(4096, (int)Math.min(1<<20, e.getSize())));
        zin.transferTo(out);
        entries.put(e.getName(), out.toByteArray());
      }
      byte[] man = entries.get("manifest.json");
      if (man == null) throw new IOException("manifest.json missing");
      JsonValue root = new JsonReader().parse(new String(man, StandardCharsets.UTF_8));
      m = new Manifest();
      m.version = root.getInt("version", 1);
      m.samplesPerSide = root.getInt("samplesPerSide");
      m.tile = root.getFloat("tile");
      m.yScale = root.getFloat("yScale");
      m.defaultTexture = root.getInt("defaultTexture", 0);
      m.chunkCount = root.getInt("chunkCount", 0);
      JsonValue chunks = root.get("chunks");
      if (chunks != null) {
        for (JsonValue c : chunks) {
          m.chunks.add(new int[]{ c.getInt("cx"), c.getInt("cz") });
        }
      }
      // v2+: textures.ground[]
      JsonValue textures = root.get("textures");
      if (textures != null) {
        JsonValue ground = textures.get("ground");
        if (ground != null) {
          for (JsonValue t : ground) {
            TextureRef tr = new TextureRef();
            tr.id = t.getInt("id");
            tr.name = t.getString("name", "tex" + tr.id);
            tr.file = t.getString("file");
            tr.uvScale = t.getFloat("uvScale", 1f);
            m.groundTextures.add(tr);
          }
        }
      }
      return new MapResource(m, data);
    } catch (IOException ex) {
      throw new RuntimeException("Invalid .map file", ex);
    }
  }

  // ----------------------------
  // ZIP/staged entry reads
  // ----------------------------

  /** Reads a chunk entry (HMXS) by coord; returns null if not present. */
  public byte[] readChunkHmx(int cx, int cz) {
    String want = "chunks/" + cx + "_" + cz + ".hmx";
    return readEntry(want);
  }

  /** Read from staged if present, else from the .map zip; returns null if not found. */
  public byte[] getTextureBytes(String pathInZip) {
    byte[] s = stagedTextures.get(pathInZip);
    if (s != null) return s;
    return readEntry(pathInZip);
  }

  /** Read an arbitrary entry from the .map zip; returns null if not found. */
  public byte[] readEntry(String pathInZip) {
    try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(bytes))) {
      ZipEntry e;
      while ((e = zin.getNextEntry()) != null) {
        if (e.getName().equals(pathInZip)) {
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

  // ----------------------------
  // Texture importing / staging
  // ----------------------------

  /**
   * Import a ground texture from raw bytes and original filename.
   * - Adds an entry to manifest.groundTextures with a unique path and id.
   * - Stages the bytes in-memory so the editor can use them immediately.
   * @return the created TextureRef
   */
  public TextureRef importGroundTexture(String originalFilename, byte[] data) {
    if (data == null || data.length == 0) {
      throw new IllegalArgumentException("Empty texture data");
    }
    String safeName = sanitizeFilename(originalFilename != null ? originalFilename : "texture.png");
    String pathInZip = uniqueTexturePath(manifest, "textures/ground/" + safeName);
    int id = nextTextureId(manifest);

    TextureRef tr = new TextureRef();
    tr.id = id;
    tr.name = stripExtension(safeName);
    tr.file = pathInZip;
    tr.uvScale = 1f;

    manifest.groundTextures.add(tr);
    stagedTextures.put(pathInZip, data);
    // ensure version >=2 if textures exist
    System.out.println("Imported textures:");
    for (TextureRef tr2 : manifest.groundTextures) {
      System.out.println("  - " + tr2.file);
    }
    
    if (!manifest.groundTextures.isEmpty()) manifest.version = Math.max(manifest.version, 2);
    return tr;
  }

  /** List of staged texture paths (for diagnostics/UI). */
  public Set<String> getStagedTexturePaths() {
    return Collections.unmodifiableSet(stagedTextures.keySet());
  }

  /** Access a copy of staged textures (path->bytes) for saving or inspection. */
  public Map<String, byte[]> getStagedTexturesCopy() {
    return new LinkedHashMap<>(stagedTextures);
  }

  /** Clears staged textures (call after a successful save that included them). */
  public void clearStagedTextures() {
    stagedTextures.clear();
  }

  // ----------------------------
  // TextureArray builder
  // ----------------------------

  /**
   * Builds a GL TextureArray from manifest textures.
   * Uses temp PNG files and the FileHandle-based constructor (works across libGDX versions).
   */
  public TextureArray buildGroundTextureArray() {
    if (manifest.groundTextures.isEmpty())
      throw new IllegalStateException("No ground textures listed in manifest (version " + manifest.version + ").");

    // Load first as canonical (from staged or zip)
    TextureRef t0 = manifest.groundTextures.get(0);
    Pixmap base = loadPixmap(t0.file);
    final int w = base.getWidth();
    final int h = base.getHeight();
    final Pixmap.Format canonicalFmt = Pixmap.Format.RGBA8888;

    List<File> tempFiles = new ArrayList<>();
    List<FileHandle> fileHandles = new ArrayList<>();

    try {
      for (int i = 0; i < manifest.groundTextures.size(); i++) {
        TextureRef tr = manifest.groundTextures.get(i);
        Pixmap src = (i == 0) ? base : loadPixmap(tr.file);

        Pixmap fixed = new Pixmap(w, h, canonicalFmt);
        fixed.drawPixmap(src, 0, 0, src.getWidth(), src.getHeight(), 0, 0, w, h);
        if (src != base) src.dispose();

        Path tmp = Files.createTempFile("map_ground_layer_" + i + "_", ".png");
        File f = tmp.toFile();
        f.deleteOnExit();
        PixmapIO.writePNG(new FileHandle(f), fixed);
        fixed.dispose();

        tempFiles.add(f);
        fileHandles.add(new FileHandle(f));
      }

      TextureArray ta = new TextureArray(true, Pixmap.Format.RGBA8888, fileHandles.toArray(new FileHandle[0]));
      ta.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
      ta.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
      return ta;

    } catch (IOException io) {
      throw new RuntimeException("Failed to create temporary files for TextureArray.", io);
    } finally {
      if (base != null) base.dispose();
      // temp files left for JVM exit; deleting immediately can be risky on some drivers
    }
  }

  private Pixmap loadPixmap(String pathInZip) {
    byte[] data = getTextureBytes(pathInZip);
    if (data == null)
      throw new RuntimeException("Texture not found in map: " + pathInZip);
    return new Pixmap(data, 0, data.length);
  }

  // ----------------------------
  // Save
  // ----------------------------

  /** Backward-compatible save: only chunks (like your original). */
  public static void save(String path, Manifest m, Map<String, byte[]> chunkFiles) {
    save(path, m, chunkFiles, Collections.emptyMap());
  }

  /**
   * Full save: chunks + textures.
   * @param chunkFiles map of "cx_cz.hmx" -> bytes (writer will prefix with "chunks/")
   * @param textureFiles map with full ZIP paths e.g. "textures/ground/grass.png" -> bytes
   */
  public static void save(String path, Manifest m,
                          Map<String, byte[]> chunkFiles,
                          Map<String, byte[]> textureFiles) {
    try (ZipOutputStream zout = new ZipOutputStream(new BufferedOutputStream(
            Files.newOutputStream(Paths.get(path))))) {

      // write manifest
      zout.putNextEntry(new ZipEntry("manifest.json"));
      String json = toJson(m);
      zout.write(json.getBytes(StandardCharsets.UTF_8));
      zout.closeEntry();

      // write chunks
      for (Map.Entry<String, byte[]> en : chunkFiles.entrySet()) {
        zout.putNextEntry(new ZipEntry("chunks/" + en.getKey()));
        zout.write(en.getValue());
        zout.closeEntry();
      }

      // write textures (entries should include "textures/ground/...")
      for (Map.Entry<String, byte[]> en : textureFiles.entrySet()) {
        System.out.println("Saving texture: " + en.getKey());
        zout.putNextEntry(new ZipEntry(en.getKey()));
        zout.write(en.getValue());
        zout.closeEntry();
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to save map " + path, e);
    }
  }

  /** Convenience: save including any textures staged via importGroundTexture(). */
  /** Save chunks + ALL textures referenced by the manifest (staged first, else from current ZIP). */
  public void saveIncludingStaged(String path, Map<String, byte[]> chunkFiles) {
    Map<String, byte[]> allTextures = new LinkedHashMap<>();

    // Pull bytes for each manifest texture (prefers staged; else from loaded ZIP)
    for (TextureRef tr : manifest.groundTextures) {
      byte[] data = getTextureBytes(tr.file);
      if (data == null) throw new RuntimeException("Missing texture: " + tr.file);
      allTextures.put(tr.file, data);
    }

    // (Optional) include any staged entries not referenced in manifest
    for (Map.Entry<String, byte[]> e : stagedTextures.entrySet()) {
      allTextures.putIfAbsent(e.getKey(), e.getValue());
    }

    MapResource.save(path, manifest, chunkFiles, allTextures);
    clearStagedTextures();
  }


  // ----------------------------
  // Manifest JSON writer
  // ----------------------------

  private static String toJson(Manifest m) {
    int version = m.groundTextures.isEmpty() ? Math.max(1, m.version) : Math.max(2, m.version);

    StringBuilder sb = new StringBuilder(1024);
    sb.append("{\"version\":").append(version)
      .append(",\"samplesPerSide\":").append(m.samplesPerSide)
      .append(",\"tile\":").append(m.tile)
      .append(",\"yScale\":").append(m.yScale)
      .append(",\"defaultTexture\":").append(m.defaultTexture)
      .append(",\"chunkCount\":").append(m.chunks.size())
      .append(",\"chunks\":[");
    for (int i = 0; i < m.chunks.size(); i++) {
      int[] c = m.chunks.get(i);
      if (i > 0) sb.append(',');
      sb.append("{\"cx\":").append(c[0]).append(",\"cz\":").append(c[1]).append('}');
    }
    sb.append(']');

    if (!m.groundTextures.isEmpty()) {
      sb.append(",\"textures\":{\"ground\":[");
      for (int i = 0; i < m.groundTextures.size(); i++) {
        TextureRef t = m.groundTextures.get(i);
        if (i > 0) sb.append(',');
        sb.append("{\"id\":").append(t.id)
          .append(",\"name\":\"").append(escape(t.name)).append('"')
          .append(",\"file\":\"").append(escape(t.file)).append('"')
          .append(",\"uvScale\":").append(t.uvScale)
          .append('}');
      }
      sb.append("]}");
    }

    sb.append('}');
    return sb.toString();
  }

  // ----------------------------
  // Helpers
  // ----------------------------

  private static String sanitizeFilename(String name) {
    return name.replace('\\','_').replace('/','_').replace(' ','_');
  }

  private static String stripExtension(String filename) {
    int dot = filename.lastIndexOf('.');
    return dot > 0 ? filename.substring(0, dot) : filename;
  }

  private static String uniqueTexturePath(Manifest man, String preferred) {
    Set<String> existing = new HashSet<>();
    for (TextureRef t : man.groundTextures) existing.add(t.file);
    if (!existing.contains(preferred)) return preferred;
    String stem = preferred;
    String ext = "";
    int dot = preferred.lastIndexOf('.');
    if (dot >= 0) { stem = preferred.substring(0, dot); ext = preferred.substring(dot); }
    for (int i = 1; i < 10000; i++) {
      String cand = stem + "_" + i + ext;
      if (!existing.contains(cand)) return cand;
    }
    return stem + "_import" + ext;
  }

  private static int nextTextureId(Manifest man) {
    int max = -1;
    for (TextureRef t : man.groundTextures) max = Math.max(max, t.id);
    return max + 1;
  }

  /** Minimal JSON string escaper for quotes/backslashes. */
  private static String escape(String s) {
    if (s == null) return "";
    StringBuilder out = new StringBuilder(s.length() + 8);
    for (int i = 0; i < s.length(); i++) {
      char ch = s.charAt(i);
      switch (ch) {
        case '\\': out.append("\\\\"); break;
        case '"':  out.append("\\\""); break;
        case '\b': out.append("\\b");  break;
        case '\f': out.append("\\f");  break;
        case '\n': out.append("\\n");  break;
        case '\r': out.append("\\r");  break;
        case '\t': out.append("\\t");  break;
        default:
          if (ch < 0x20) out.append(String.format("\\u%04x", (int) ch));
          else out.append(ch);
      }
    }
    return out.toString();
  }
}
