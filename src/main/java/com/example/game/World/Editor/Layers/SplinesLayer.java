package com.example.game.World.Editor.Layers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.example.game.World.Editor.BaseToolLayer;
import com.example.game.World.Editor.Tools.Splines.RoadTool;
import com.example.game.World.Editor.Tools.Splines.RiverTool;
import com.example.game.World.Splines.TerrainSpline;
import com.example.game.World.Splines.TerrainSplineNode;
import com.example.game.World.TerrainSystem;
import com.example.game.World.Editor.Render.SplineRibbonRenderer;
import com.example.game.World.Editor.Render.RenderMode;
import com.example.game.World.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class SplinesLayer extends BaseToolLayer {
  private final TerrainSystem terrain;
  // Active splines (subset of catalog) used for rendering/picking
  private final List<TerrainSpline> splines = new ArrayList<>();
  // Full catalog loaded from map (persisted); may be larger than 'splines'
  private final List<TerrainSpline> catalog = new ArrayList<>();
  // Background worker for activation sync
  private static final java.util.concurrent.ExecutorService SPLINE_SYNC = java.util.concurrent.Executors.newSingleThreadExecutor(r -> { Thread t=new Thread(r, "SplineSync"); t.setDaemon(true); return t; });
  // Selection shared across tools
  private TerrainSpline selectedSpline = null;
  private int selectedNode = -1;

  // Dedicated texture set for splines (names only for now; could be actual textures later)
  private final List<String> splineTextureNames = new ArrayList<>();
  private final SplineRibbonRenderer ribbonRenderer;

  public SplinesLayer(TerrainSystem terrain, Skin skin) {
    super("splines", "Splines");
    this.terrain = terrain;
    this.ribbonRenderer = new SplineRibbonRenderer(this, terrain);
    // Create tools bound to this layer
    addTool(new RoadTool(this, terrain));
    addTool(new RiverTool(this, terrain));
    // Remove live height modifier; bake processors now handle rivers/mountains to avoid double application

    // Load splines catalog from map (if present)
    try { loadSplinesFromMap(); } catch (Throwable ignored) {}

    // River bake is installed centrally in EditorScreen

    // Seed active list from full catalog so splines are visible immediately at startup
    synchronized (splines) {
      splines.clear();
      splines.addAll(getCatalogSnapshot());
    }


    // When chunks change, rebuild ribbons and resync active set (additive listener)
    this.terrain.addChunksChangedListener(() -> {
      // After chunks load/unload and baking applies, reproject nodes to CURRENT heights (includes mountains/rivers)
      var hs = this.terrain; // TerrainSystem implements HeightSampler using current heights
      for (TerrainSpline s : this.splines) {
        java.util.List<com.example.game.World.Splines.TerrainSplineNode> nodes = s.getNodes();
        for (int i = 0; i < nodes.size(); i++) {
          com.example.game.World.Splines.TerrainSplineNode n = nodes.get(i);
          float ny = hs.heightAt(n.pos.x, n.pos.z, n.pos.y) + 0.5f + s.riverDepth;
          if (Math.abs(ny - n.pos.y) > 1e-4f) {
            n.pos.y = ny;
          }
        }
        s.markRibbonDirty();
        s.rebuildRibbonAsync(this.terrain, 2);
      }
      resyncActiveSplinesAsync();
      // Stage latest catalog so a subsequent save writes splines into the map
      saveSplinesToMap();
    });

  }

  // ---------------- Ribbon-only rendering (no overlays) ----------------
  public void renderRibbonsOnly(Camera cam) {
    ribbonRenderer.render(cam, RenderMode.SPLINES_LAYER);
  }

  /** Render-only the non-textured ROAD ribbons for the active Road tool. */
  public void renderRoadToolRibbons(Camera cam) {
    ribbonRenderer.render(cam, RenderMode.TOOL_ROAD);
  }

  @Override public void render3D(Camera cam) {
    // Always draw ribbons for context
    renderRibbonsOnly(cam);
    // Then let the active tool draw its overlays (e.g., RiverTool nodes/lines) when this layer is active
    super.render3D(cam);
  }


  public List<TerrainSpline> getSplines() {
    synchronized (splines) { return java.util.Collections.unmodifiableList(new java.util.ArrayList<>(splines)); }
  }
  /** Full catalog snapshot (all splines persisted in map), for baking/loading. */
  public List<TerrainSpline> getCatalogSnapshot() {
    synchronized (catalog) { return java.util.Collections.unmodifiableList(new java.util.ArrayList<>(catalog)); }
  }
  public TerrainSystem getTerrain() { return terrain; }

  public TerrainSpline createSpline(TerrainSpline.Type type) {
    TerrainSpline s = new TerrainSpline(type);
    // Assign a global unique id and register
    s.setId(com.example.game.World.PlaceableRegistry.nextId());
    com.example.game.World.PlaceableRegistry.upsert(s);
    splines.add(s);
    catalog.add(s);
    return s;
  }

  public void removeSpline(TerrainSpline s) {
    splines.remove(s);
    catalog.remove(s);
    com.example.game.World.PlaceableRegistry.remove(s);
  }

  @Override public void onActivate() { clearSelection(); }
  @Override public void onDeactivate() { /* no-op for now */ }

  // ---------------- Persistence: serialize/deserialize ----------------
  public void saveSplinesToMap() {
    // Serialize catalog (not just active) so data is complete
    String json = serializeSplines(this.catalog);
    byte[] bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    terrain.getMap().stageExtra("splines/splines.json", bytes);
  }

  private void loadSplinesFromMap() {
    byte[] data = terrain.getMap().readEntry("splines/splines.json");
    if (data == null || data.length == 0) {
      // Seed catalog from any existing in-memory splines if file not present
      this.catalog.clear();
      this.catalog.addAll(this.splines);
      return;
    }
    String json = new String(data, java.nio.charset.StandardCharsets.UTF_8);
    java.util.List<TerrainSpline> loaded = deserializeSplines(json);
    this.catalog.clear();
    this.catalog.addAll(loaded);
  }

  private static String serializeSplines(List<TerrainSpline> list) {
    StringBuilder sb = new StringBuilder(1024);
    sb.append('{').append("\"splines\":[");
    for (int i=0;i<list.size();i++) {
      TerrainSpline s = list.get(i);
      if (i>0) sb.append(',');
      sb.append('{')
        .append("\"type\":\"").append(s.getType().name()).append("\"")
        .append(",\"id\":").append(s.getId())
        .append(",\"name\":\"").append(s.getName().replace("\"","\\\"")).append("\"")
        .append(",\"width\":").append(s.width)
        .append(",\"riverDepth\":").append(s.riverDepth)
        .append(",\"bankFalloff\":").append(s.bankFalloff)
        .append(",\"textureId\":").append(s.textureId)
        .append(",\"transparency\":").append(s.transparency)
        .append(",\"heightOffset\":").append(s.heightOffset)
        .append(",\"textured\":").append(s.textured)
        .append(",\"ribbonWidthRatio\":").append(s.ribbonWidthRatio)
        .append(",\"reverseFlow\":").append(s.reverseFlow)
        .append(",\"nodes\":[");
      java.util.List<com.example.game.World.Splines.TerrainSplineNode> nodes = s.getNodes();
      for (int j=0;j<nodes.size();j++) {
        var node = nodes.get(j);
        var n = node.pos;
        if (j>0) sb.append(',');
        sb.append('{')
          .append("\"x\":").append(n.x)
          .append(",\"y\":").append(n.y)
          .append(",\"z\":").append(n.z);
        // Persist relative bounds if set (non-zero) to avoid bloating default cases
        float rminx = node.getBMinRel().x, rminy = node.getBMinRel().y, rminz = node.getBMinRel().z;
        float rmaxx = node.getBMaxRel().x, rmaxy = node.getBMaxRel().y, rmaxz = node.getBMaxRel().z;
        if (!(rminx == 0f && rminy == 0f && rminz == 0f && rmaxx == 0f && rmaxy == 0f && rmaxz == 0f)) {
          sb.append(",\"bminx\":").append(rminx)
            .append(",\"bminy\":").append(rminy)
            .append(",\"bminz\":").append(rminz)
            .append(",\"bmaxx\":").append(rmaxx)
            .append(",\"bmaxy\":").append(rmaxy)
            .append(",\"bmaxz\":").append(rmaxz);
        }
        sb.append('}');
      }
      sb.append(']').append('}');
    }
    sb.append(']').append('}');
    return sb.toString();
  }

  private static java.util.List<TerrainSpline> deserializeSplines(String json) {
    java.util.ArrayList<TerrainSpline> out = new java.util.ArrayList<>();
    com.badlogic.gdx.utils.JsonValue root = new com.badlogic.gdx.utils.JsonReader().parse(json);
    com.badlogic.gdx.utils.JsonValue arr = root.get("splines");
    if (arr == null) return out;
    long maxId = 0L;
    for (com.badlogic.gdx.utils.JsonValue s : arr) {
      String type = s.getString("type", "RIVER");
      TerrainSpline ts = new TerrainSpline("ROAD".equals(type)? TerrainSpline.Type.ROAD : TerrainSpline.Type.RIVER);
      // id/name (assign id if missing)
      long sid = s.getLong("id", 0L);
      if (sid == 0L) sid = com.example.game.World.PlaceableRegistry.nextId();
      ts.setId(sid);
      String nm = s.getString("name", ts.getName());
      ts.setName(nm);
      if (sid > maxId) maxId = sid;
      ts.width = s.getFloat("width", ts.width);
      ts.riverDepth = s.getFloat("riverDepth", ts.riverDepth);
      ts.bankFalloff = s.getFloat("bankFalloff", ts.bankFalloff);
      ts.textureId = s.getInt("textureId", ts.textureId);
      ts.transparency = s.getFloat("transparency", ts.transparency);
      ts.heightOffset = s.getFloat("heightOffset", ts.heightOffset);
      ts.textured = s.getBoolean("textured", true);
      ts.ribbonWidthRatio = s.getFloat("ribbonWidthRatio", ts.ribbonWidthRatio);
      ts.reverseFlow = s.getBoolean("reverseFlow", false);
      com.badlogic.gdx.utils.JsonValue nodes = s.get("nodes");
      if (nodes != null) {
        for (com.badlogic.gdx.utils.JsonValue n : nodes) {
          float x = n.getFloat("x"); float y = n.getFloat("y"); float z = n.getFloat("z");
          com.example.game.World.Splines.TerrainSplineNode tn = new com.example.game.World.Splines.TerrainSplineNode(x, y, z);
          boolean hasBounds = n.has("bminx") && n.has("bminy") && n.has("bminz") && n.has("bmaxx") && n.has("bmaxy") && n.has("bmaxz");
          if (hasBounds) {
            tn.setBMinRel(n.getFloat("bminx"), n.getFloat("bminy"), n.getFloat("bminz"));
            tn.setBMaxRel(n.getFloat("bmaxx"), n.getFloat("bmaxy"), n.getFloat("bmaxz"));
          } else {
            // Apply type defaults if node lacks explicit bounds so getBoundingBox works everywhere
            float hxz, hya, hyb;
            if (ts.getType() == TerrainSpline.Type.RIVER) { hxz = 3.0f; hya = 2.5f; hyb = 0.5f; }
            else { hxz = 1.0f; hya = 0.6f; hyb = 0.2f; }
            tn.setBMinRel(-hxz, -hyb, -hxz);
            tn.setBMaxRel( hxz,  hya,  hxz);
          }
          ts.addNode(tn);
        }
      }
      out.add(ts);
      com.example.game.World.PlaceableRegistry.upsert(ts);
    }
    // ensure id generator continues past the largest id we saw
    com.example.game.World.PlaceableRegistry.ensureAtLeast(maxId + 1);
    return out;
  }

  // ---------------- Activation: keep only splines intersecting loaded chunks ----------------
  public void resyncActiveSplinesAsync() {
    final java.util.Set<Integer> loaded = terrain.getLoadedChunkKeysCopy();
    if (loaded.isEmpty()) return;
    final int S = terrain.getMap().getManifest().samplesPerSide;
    final float tile = terrain.getMap().getManifest().tile;
    final float chunkSize = (S - 1) * tile;
    SPLINE_SYNC.submit(() -> {
      java.util.ArrayList<TerrainSpline> next = new java.util.ArrayList<>();
      for (TerrainSpline s : catalog) {
        boolean keep = false;
        java.util.List<com.example.game.World.Splines.TerrainSplineNode> nodes = s.getNodes();
        for (int i=0;i<nodes.size();i++) {
          var p = nodes.get(i).pos;
          int cx = (int)Math.floor(p.x / chunkSize);
          int cz = (int)Math.floor(p.z / chunkSize);
          if (terrain.isChunkLoaded(cx, cz)) { keep = true; break; }
        }
        if (keep) next.add(s);
      }
      // Publish on render thread context next frame; here we replace list (simple editor)
      synchronized (splines) {
        splines.clear();
        splines.addAll(next);
      }
    });
  }

  // ---------------- Selection API ----------------
  public TerrainSpline getSelectedSpline() { return selectedSpline; }
  public int getSelectedNode() { return selectedNode; }
  public void clearSelection() { selectedSpline = null; selectedNode = -1; }
  public void select(TerrainSpline s, int nodeIndex) { selectedSpline = s; selectedNode = nodeIndex; }

  /** Find nearest node in all splines to the given world XZ within the given radius (meters). */
  public PickResult pickNode(float worldX, float worldZ, float radiusMeters) {
    TerrainSpline bestS = null; int bestIdx = -1; float bestD2 = radiusMeters * radiusMeters;
    java.util.List<TerrainSpline> iter; synchronized (splines) { iter = new java.util.ArrayList<>(splines); }
    for (TerrainSpline s : iter) {
      List<TerrainSplineNode> nodes = s.getNodes();
      for (int i = 0; i < nodes.size(); i++) {
        float dx = nodes.get(i).pos.x - worldX;
        float dz = nodes.get(i).pos.z - worldZ;
        float d2 = dx*dx + dz*dz;
        if (d2 <= bestD2) { bestD2 = d2; bestS = s; bestIdx = i; }
      }
    }
    return (bestS != null) ? new PickResult(bestS, bestIdx) : null;
  }

  /**
   * Ray-vs-AABB pick that ignores terrain projection.
   * Builds an AABB around each node center (node.pos) with half-extents (halfXZ, halfYBelow/Above) and selects
   * the nearest intersection along the ray.
   */
  public PickResult pickNodeRayAABB(Camera cam, int screenX, int screenY, float halfXZ, float halfYBelow, float halfYAbove) {
    return pickNodeRayBox(cam, screenX, screenY, c -> new AABB().set(
      c.x - halfXZ, c.y - halfYBelow, c.z - halfXZ,
      c.x + halfXZ, c.y + halfYAbove, c.z + halfXZ
    ));
  }

  /**
   * Ray-vs-box pick using a box builder per node center. The builder receives the node center and must
   * return an AABB in world-space for that node.
   */
  public PickResult pickNodeRayBox(Camera cam, int screenX, int screenY, Function<Vector3, AABB> boxAtCenter) {
    if (cam == null || boxAtCenter == null) return null;
    Ray ray = cam.getPickRay(screenX, screenY);
    TerrainSpline bestS = null; int bestIdx = -1; float bestT = Float.POSITIVE_INFINITY;
    java.util.List<TerrainSpline> iter; synchronized (splines) { iter = new java.util.ArrayList<>(splines); }
    for (TerrainSpline s : iter) {
      List<TerrainSplineNode> nodes = s.getNodes();
      for (int i = 0; i < nodes.size(); i++) {
        Vector3 c = nodes.get(i).pos;
        AABB box = boxAtCenter.apply(c);
        if (box == null) continue;
        float t = box.intersectRayT(ray);
        if (t >= 0f && t < bestT) { bestT = t; bestS = s; bestIdx = i; }
      }
    }
    return (bestS != null) ? new PickResult(bestS, bestIdx) : null;
  }

  /** Ray-vs-AABB pick using each node's own half extents. Falls back to type defaults if not set. */
  public PickResult pickNodeRayNodes(Camera cam, int screenX, int screenY) {
    if (cam == null) return null;
    Ray ray = cam.getPickRay(screenX, screenY);
    TerrainSpline bestS = null; int bestIdx = -1; float bestT = Float.POSITIVE_INFINITY;
    java.util.List<TerrainSpline> iter; synchronized (splines) { iter = new java.util.ArrayList<>(splines); }
    for (TerrainSpline s : iter) {
      List<TerrainSplineNode> nodes = s.getNodes();
      for (int i = 0; i < nodes.size(); i++) {
        TerrainSplineNode n = nodes.get(i);
        AABB box = n.getBoundingBox();
        float t = (box != null) ? box.intersectRayT(ray) : -1f;
        if (t >= 0f && t < bestT) { bestT = t; bestS = s; bestIdx = i; }
      }
    }
    return (bestS != null) ? new PickResult(bestS, bestIdx) : null;
  }

 

  /** 3D box pick around node position. Half extents are in meters. */
  public PickResult pickNodeBox(float worldX, float worldY, float worldZ, float halfXZ, float halfY) {
    TerrainSpline bestS = null; int bestIdx = -1;
    float bestScore = Float.POSITIVE_INFINITY; // lower is better
    java.util.List<TerrainSpline> iter; synchronized (splines) { iter = new java.util.ArrayList<>(splines); }
    for (TerrainSpline s : iter) {
      List<TerrainSplineNode> nodes = s.getNodes();
      for (int i = 0; i < nodes.size(); i++) {
        var p = nodes.get(i).pos;
        float dx = Math.abs(p.x - worldX);
        float dy = Math.abs(p.y - worldY);
        float dz = Math.abs(p.z - worldZ);
        if (dx <= halfXZ && dy <= halfY && dz <= halfXZ) {
          // score by Manhattan distance to prefer closer inside the box
          float score = dx + dy + dz;
          if (score < bestScore) { bestScore = score; bestS = s; bestIdx = i; }
        }
      }
    }
    return (bestS != null) ? new PickResult(bestS, bestIdx) : null;
  }

  public static final class PickResult {
    public final TerrainSpline spline; public final int nodeIndex;
    public PickResult(TerrainSpline s, int idx) { this.spline = s; this.nodeIndex = idx; }
  }

  // ---------------- Texture list API ----------------
  public String[] listSplineTextureNames() { return splineTextureNames.toArray(new String[0]); }
  public int importSplineTextureDesktop() {
    if (Gdx.app.getType() != com.badlogic.gdx.Application.ApplicationType.Desktop) return -1;
    final String[] abs = { null };
    try {
      javax.swing.SwingUtilities.invokeAndWait(() -> {
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        fc.setDialogTitle("Import Spline Texture");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Images", "png", "jpg", "jpeg", "bmp"));
        if (fc.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
          abs[0] = fc.getSelectedFile().getAbsolutePath();
        }
      });
    } catch (Exception ignored) {}
    if (abs[0] == null) return -1;
    String label = new java.io.File(abs[0]).getName();
    splineTextureNames.add(label);
    return splineTextureNames.size() - 1;
  }
}
