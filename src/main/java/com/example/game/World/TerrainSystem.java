package com.example.game.World;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.example.game.Render.SimpleShader;

import java.util.Map;

/**
 * TerrainSystem (facade)
 * ----------------------
 * Thin coordinator that exposes the public API used by the editor/tools, while delegating
 * real work to the split classes:
 *
 *   - TerrainWorld     : shared state (chunks, textures, manifest refs, queues, config)
 *   - TerrainStreamer  : load/unload chunks around a focus point
 *   - TerrainEditor    : height/texture edits + dirty rebuilds
 *   - LodController    : LOD selection with hysteresis
 *   - TerrainRenderer  : GL draws and ground TextureArray bindings
 *   - TerrainIO        : manifest merge, encode, delta-save
 *   - TerrainPicking   : screen→world picking against terrain height
 *
 * This keeps the rest of your project calling the same methods as before.
 */
public class TerrainSystem implements HeightSampler {

  // ---- Modules ----
  final TerrainWorld world;
  private final TerrainStreamer streamer;
  private final TerrainEditor editor;
  private final LodController lod;
  private final TerrainRenderer renderer;
  private final TerrainIO io;
  private final TerrainPicking picking;

  public TerrainSystem(MapResource map) {
    this.world    = new TerrainWorld(map);
    this.lod      = new LodController(world);
    this.streamer = new TerrainStreamer(world);
    this.editor   = new TerrainEditor(world);
    this.renderer = new TerrainRenderer(world, lod);
    this.io       = new TerrainIO(world);
    this.picking  = new TerrainPicking(world);
  }

  /** Desktop file chooser that imports a texture into a specific subfolder under textures/ground/. */
  public int importGroundTextureDesktopInto(String subfolder, float uvScale) {
    if (Gdx.app.getType() != com.badlogic.gdx.Application.ApplicationType.Desktop) return -1;

    final String[] abs = { null };
    try {
      javax.swing.SwingUtilities.invokeAndWait(() -> {
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        fc.setDialogTitle("Import Texture");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Images", "png", "jpg", "jpeg", "bmp"));
        if (fc.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
          abs[0] = fc.getSelectedFile().getAbsolutePath();
        }
      });
    } catch (Exception ignored) {}

    if (abs[0] == null) return -1;

    var fh = Gdx.files.absolute(abs[0]);
    if (!fh.exists()) return -1;

    byte[] data = fh.readBytes();
    MapResource.TextureRef tr = world.map.importGroundTextureIntoFolder(fh.name(), data, subfolder);
    tr.uvScale = uvScale;
    // Do NOT reload here (GL calls must happen on the render thread).
    // Caller (e.g., TextureBrowser) will post a reload to the GL thread.
    return tr.id;
  }

  // ----------------------------------------------------------------------------
  // Streaming / Update
  // ----------------------------------------------------------------------------
  /** Load/unload chunks around the given world XZ focus (call each frame or on movement). */
  public void updateAndStream(float worldX, float worldZ) { streamer.updateAndStream(worldX, worldZ); }

  /** Set circular view radius in CHUNKS (not meters). */
  public void setViewRadius(int r) { world.viewRadius = Math.max(0, r); }

  // ----------------------------------------------------------------------------
  // Rendering
  // ----------------------------------------------------------------------------
  public void render(SimpleShader shader, Camera cam) { renderer.render(shader, cam); }

  // Rendering toggles
  public void setWireframe(boolean on) { renderer.setWireframe(on); }
  public void setChunkGrid(boolean on) { renderer.setDrawGrid(on); }


  /** Paint a texture id into a circular region (byte id, 0..255). */

  /** Rebuild meshes/LODs for any chunks that were marked dirty after edits. */
  public void rebuildDirtyChunks() { editor.rebuildDirtyChunks(); }

  // ----------------------------------------------------------------------------
  // Textures (UI helpers + runtime refresh)
  // ----------------------------------------------------------------------------
  /** Human-friendly list of ground texture names (manifest order == layer id). */
  public String[] listGroundTextureNames() {
    var mf = world.map.getManifest();
    if (mf.groundTextures == null || mf.groundTextures.isEmpty()) return new String[0];

    String[] out = new String[mf.groundTextures.size()];
    for (int i = 0; i < out.length; i++) {
      MapResource.TextureRef tr = mf.groundTextures.get(i);
      String label = (tr.name != null && !tr.name.isEmpty()) ? tr.name : tr.file;
      int slash = Math.max(label.lastIndexOf('/'), label.lastIndexOf('\\'));
      out[i] = (slash >= 0) ? label.substring(slash + 1) : label;
    }
    return out;
  }

  /** Rebuild the TextureArray from the current manifest (staged-or-zip), and refresh UV scales. */
  public void reloadGroundTextures() {
    if (world.groundTA != null) { world.groundTA.dispose(); world.groundTA = null; }
    var mf = world.map.getManifest();
    if (mf.groundTextures == null || mf.groundTextures.isEmpty()) return;

    world.groundTA = world.map.buildGroundTextureArray();
    java.util.Arrays.fill(world.uvScales, 1f);
    for (MapResource.TextureRef tr : mf.groundTextures) {
      if (tr.id >= 0 && tr.id < world.uvScales.length) {
        world.uvScales[tr.id] = (tr.uvScale == 0f ? 1f : tr.uvScale);
      }
    }
  }

  /**
   * Desktop file chooser → MapResource.importGroundTexture(...) → rebuild TA.
   * Returns new texture id or -1 if canceled/not desktop.
   */
  public int importGroundTextureDesktop(float uvScale) {
    if (Gdx.app.getType() != com.badlogic.gdx.Application.ApplicationType.Desktop) return -1;

    final String[] abs = { null };
    try {
      javax.swing.SwingUtilities.invokeAndWait(() -> {
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        fc.setDialogTitle("Import Texture");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Images", "png", "jpg", "jpeg", "bmp"));
        if (fc.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
          abs[0] = fc.getSelectedFile().getAbsolutePath();
        }
      });
    } catch (Exception ignored) {}

    if (abs[0] == null) return -1;

    var fh = Gdx.files.absolute(abs[0]);
    if (!fh.exists()) return -1;

    byte[] data = fh.readBytes();
    MapResource.TextureRef tr = world.map.importGroundTexture(fh.name(), data);
    tr.uvScale = uvScale;

    reloadGroundTextures();
    return tr.id;
  }

  // ----------------------------------------------------------------------------
  // Save / IO
  // ----------------------------------------------------------------------------
  /** Back-compat shim (old code): same as buildManifestMerged(). */
  public MapResource.Manifest buildManifest() { return io.buildManifestMerged(); }

  /** Build a manifest that merges the current on-disk set with any in-memory chunks you own. */
  public MapResource.Manifest buildManifestMerged() { return io.buildManifestMerged(); }

  /** Encode only the chunks you intend to persist (delta-save friendly). */
  public Map<String, byte[]> encodeChunksToHmx() { return io.encodeChunksToHmx(); }

  /** Save manifest + chunks + any textures (staged or existing) referenced by the manifest. */
  public void saveAll(String path) { io.saveAll(path); }

  // ----------------------------------------------------------------------------
  // HeightSampler (for generic sampling clients)
  // ----------------------------------------------------------------------------
  @Override public float heightAt(float x, float z, float defaultHeight) {
    float base = world.sampleHeightAtWorld(x, z, defaultHeight);
    HeightModifier hm = world.heightModifier;
    return (hm != null) ? base + hm.deltaAt(x, z) : base;
  }
  @Override public int getSamplesPerSide() { return world.S; }
  @Override public float getTileSize() { return world.tile; }
  @Override public float getYScale() { return world.yScale; }
  public TerrainEditor getEditor() { return editor; }
  public TerrainPicking getPicker() { return picking; }

  /** HeightSampler that uses each chunk's baseline heights if available, otherwise current heights.
   * Ignores non-destructive modifiers. Useful for ribbons that should follow the base map. */
  public HeightSampler getBaseHeightSampler() {
    return new HeightSampler() {
      @Override public float heightAt(float x, float z, float defaultHeight) {
        return sampleBaseHeightAtWorld(x, z, defaultHeight);
      }
      @Override public int getSamplesPerSide() { return world.S; }
      @Override public float getTileSize() { return world.tile; }
      @Override public float getYScale() { return world.yScale; }
    };
  }

  private float sampleBaseHeightAtWorld(float x, float z, float def) {
    int S = world.S;
    int cpc = S - 1;
    int cx = (int)Math.floor(x / (cpc * world.tile));
    int cz = (int)Math.floor(z / (cpc * world.tile));
    TerrainChunk c = world.chunks.get(TerrainWorld.key(cx, cz));
    if (c == null) return def;
    float lx = (x - c.getOriginX()) / c.getTile();
    float lz = (z - c.getOriginZ()) / c.getTile();
    if (lx < 0f || lz < 0f || lx > S - 1.001f || lz > S - 1.001f) return def;
    int x0 = (int)Math.floor(lx), z0 = (int)Math.floor(lz);
    int x1 = x0 + 1, z1 = z0 + 1;
    float tx = lx - x0, tz = lz - z0;
    short[] base = c.getBaselineArray();
    short[] arr = (base != null) ? base : c.getHeightsArray();
    float ys = c.getYScale();
    float h00 = arr[z0 * S + x0] * ys;
    float h10 = arr[z0 * S + x1] * ys;
    float h01 = arr[z1 * S + x0] * ys;
    float h11 = arr[z1 * S + x1] * ys;
    float hx0 = h00 + (h10 - h00) * tx;
    float hx1 = h01 + (h11 - h01) * tx;
    return hx0 + (hx1 - hx0) * tz;
  }

  // ----------------------------------------------------------------------------
  // Misc
  // ----------------------------------------------------------------------------
  public boolean hasChunk(int cx, int cz) { return world.chunks.containsKey(TerrainWorld.key(cx, cz)); }
  public MapResource getMap() { return world.map; }

  /** Install a non-destructive height modifier (e.g., rivers). Triggers LOD rebuild next frame. */
  public void setHeightModifier(HeightModifier m) {
    world.heightModifier = m;
    // Mark all chunks dirty so LOD meshes rebuild with modifier-applied vertex heights
    for (com.badlogic.gdx.utils.IntMap.Entry<TerrainChunk> e : world.chunks) e.value.setDirty(true);
  }

  /**
   * Bake river depths into the terrain heightmap by stamping circles along each river spline.
   * This permanently modifies the CPU height arrays and triggers LOD rebuilds.
   *
   * @param rivers list of splines (will process only those with type RIVER)
   * @param samplesPerSegment sampling density along each spline segment
   */
  public void bakeRiversDepth(java.util.List<com.example.game.World.Splines.TerrainSpline> rivers, int samplesPerSegment) {
    if (rivers == null || rivers.isEmpty()) return;
    final float tile = world.tile;
    final float yScale = world.yScale;

    // One-time: ensure every loaded chunk has a baseline to restore from (does not overwrite if already present)
    for (com.badlogic.gdx.utils.IntMap.Entry<TerrainChunk> e : world.chunks) e.value.ensureBaseline();

    // Filter rivers: RIVER type and depth > 0
    java.util.ArrayList<com.example.game.World.Splines.TerrainSpline> toBake = new java.util.ArrayList<>();
    for (com.example.game.World.Splines.TerrainSpline s : rivers) {
      if (s.getType() == com.example.game.World.Splines.TerrainSpline.Type.RIVER && s.riverDepth > 0f) {
        toBake.add(s);
      }
    }
    if (toBake.isEmpty()) return; // nothing to do

    // Compute union of affected chunks for ALL rivers, then restore baseline ONCE
    com.badlogic.gdx.utils.IntSet affectedAll = new com.badlogic.gdx.utils.IntSet();
    int cpc = world.S - 1;
    for (com.example.game.World.Splines.TerrainSpline s : toBake) {
      java.util.List<com.badlogic.gdx.math.Vector3> pts = s.samplePositions(Math.max(2, samplesPerSegment));
      if (pts.size() < 2) continue;
      int radiusCells = Math.max(1, Math.round((s.width * 0.5f) / tile));
      for (com.badlogic.gdx.math.Vector3 p : pts) {
        int minCx = (int)Math.floor((p.x - radiusCells * tile) / (cpc * tile));
        int maxCx = (int)Math.floor((p.x + radiusCells * tile) / (cpc * tile));
        int minCz = (int)Math.floor((p.z - radiusCells * tile) / (cpc * tile));
        int maxCz = (int)Math.floor((p.z + radiusCells * tile) / (cpc * tile));
        for (int cz = minCz; cz <= maxCz; cz++)
          for (int cx = minCx; cx <= maxCx; cx++)
            affectedAll.add(TerrainWorld.key(cx, cz));
      }
    }
    for (com.badlogic.gdx.utils.IntSet.IntSetIterator it = affectedAll.iterator(); it.hasNext; ) {
      int k = it.next();
      TerrainChunk c = world.chunks.get(k);
      if (c != null) c.restoreAllFromBaseline();
    }

    // Now stamp all rivers (depth>0) over the restored baseline
    for (com.example.game.World.Splines.TerrainSpline s : toBake) {
      java.util.List<com.badlogic.gdx.math.Vector3> pts = s.samplePositions(Math.max(2, samplesPerSegment));
      if (pts.size() < 2) continue;
      int radiusCells = Math.max(1, Math.round((s.width * 0.5f) / tile));
      for (com.badlogic.gdx.math.Vector3 p : pts) {
        int gx = Math.round(p.x / tile);
        int gz = Math.round(p.z / tile);
        editor.applyRiverDepthCircleProfiledClamped(gx, gz, radiusCells, s.riverDepth, s.bankFalloff);
      }
    }
    // Rebuild meshes to reflect changes
    editor.rebuildDirtyChunks();
  }

  public void dispose() {
    renderer.dispose();
    world.disposeWorld();
  }
}
