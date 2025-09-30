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

  // ----------------------------------------------------------------------------
  // Picking
  // ----------------------------------------------------------------------------
  /** Ray-pick the terrain from screen space; writes hit to 'out'. Returns true on hit. */
  public Vector3 raycastToGround(Ray ray, float step, int refineIters) {
    return picking.rayIntersectTerrain(ray, this, step, refineIters);
  }


  // ----------------------------------------------------------------------------
  // Editing (grid-space APIs; keep your existing tools working)
  // ----------------------------------------------------------------------------
  /** Raise/lower integer height samples inside a circle. */
  public boolean editHeightsCircle(int gx, int gz, int radius, short delta) {
    return editor.editHeightsCircle(gx, gz, radius, delta);
  }

  /** Paint a texture id into a circular region (byte id, 0..255). */
  public boolean paintTexCircle(int gx, int gz, int radius, byte texId) {
    return editor.paintTexCircle(gx, gz, radius, texId);
  }

  /** Paint with strength (0..1) and falloff (0..1, 1=soft). */
  public boolean paintTexCircle(int gx, int gz, int radius, byte texId, float strength, float falloff) {
    return editor.paintTexCircle(gx, gz, radius, texId, strength, falloff);
  }

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
    return world.sampleHeightAtWorld(x, z, defaultHeight);
  }
  @Override public int getSamplesPerSide() { return world.S; }
  @Override public float getTileSize() { return world.tile; }
  @Override public float getYScale() { return world.yScale; }

  // ----------------------------------------------------------------------------
  // Misc
  // ----------------------------------------------------------------------------
  public boolean hasChunk(int cx, int cz) { return world.chunks.containsKey(TerrainWorld.key(cx, cz)); }
  public MapResource getMap() { return world.map; }

  public void dispose() {
    renderer.dispose();
    world.disposeWorld();
  }
}
