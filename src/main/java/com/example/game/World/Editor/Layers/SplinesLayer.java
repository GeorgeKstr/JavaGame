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

import java.util.ArrayList;
import java.util.List;

public final class SplinesLayer extends BaseToolLayer {
  private final TerrainSystem terrain;
  private final List<TerrainSpline> splines = new ArrayList<>();
  // Selection shared across tools
  private TerrainSpline selectedSpline = null;
  private int selectedNode = -1;

  // Dedicated texture set for splines (names only for now; could be actual textures later)
  private final List<String> splineTextureNames = new ArrayList<>();

  public SplinesLayer(TerrainSystem terrain, Skin skin) {
    super("splines", "Splines");
    this.terrain = terrain;
    // Create tools bound to this layer
    addTool(new RoadTool(this, terrain));
    addTool(new RiverTool(this, terrain));
    // Install non-destructive height modifier that reads current river splines
    this.terrain.setHeightModifier(new com.example.game.World.Splines.SplineHeightModifier(this::getSplines));
  }

  public List<TerrainSpline> getSplines() { return splines; }
  public TerrainSystem getTerrain() { return terrain; }

  public TerrainSpline createSpline(TerrainSpline.Type type) {
    TerrainSpline s = new TerrainSpline(type);
    splines.add(s);
    return s;
  }

  public void removeSpline(TerrainSpline s) { splines.remove(s); }

  @Override public void onActivate() { clearSelection(); }
  @Override public void onDeactivate() { /* no-op for now */ }

  // ---------------- Selection API ----------------
  public TerrainSpline getSelectedSpline() { return selectedSpline; }
  public int getSelectedNode() { return selectedNode; }
  public void clearSelection() { selectedSpline = null; selectedNode = -1; }
  public void select(TerrainSpline s, int nodeIndex) { selectedSpline = s; selectedNode = nodeIndex; }

  /** Find nearest node in all splines to the given world XZ within the given radius (meters). */
  public PickResult pickNode(float worldX, float worldZ, float radiusMeters) {
    TerrainSpline bestS = null; int bestIdx = -1; float bestD2 = radiusMeters * radiusMeters;
    for (TerrainSpline s : splines) {
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
    if (cam == null) return null;
    Ray ray = cam.getPickRay(screenX, screenY);
    TerrainSpline bestS = null; int bestIdx = -1; float bestT = Float.POSITIVE_INFINITY;
    Vector3 min = new Vector3(); Vector3 max = new Vector3();
    for (TerrainSpline s : splines) {
      List<TerrainSplineNode> nodes = s.getNodes();
      for (int i = 0; i < nodes.size(); i++) {
        Vector3 c = nodes.get(i).pos;
        min.set(c.x - halfXZ, c.y - halfYBelow, c.z - halfXZ);
        max.set(c.x + halfXZ, c.y + halfYAbove, c.z + halfXZ);
        float t = rayAabb(ray, min, max);
        if (t >= 0f && t < bestT) { bestT = t; bestS = s; bestIdx = i; }
      }
    }
    return (bestS != null) ? new PickResult(bestS, bestIdx) : null;
  }

  // Slab method for ray-AABB. Returns t of entry if hit, else -1.
  private static float rayAabb(Ray r, Vector3 bmin, Vector3 bmax) {
    float tmin = (bmin.x - r.origin.x) / r.direction.x;
    float tmax = (bmax.x - r.origin.x) / r.direction.x;
    if (tmin > tmax) { float tmp = tmin; tmin = tmax; tmax = tmp; }

    float tymin = (bmin.y - r.origin.y) / r.direction.y;
    float tymax = (bmax.y - r.origin.y) / r.direction.y;
    if (tymin > tymax) { float tmp = tymin; tymin = tymax; tymax = tmp; }

    if (tmin > tymax || tymin > tmax) return -1f;
    if (tymin > tmin) tmin = tymin;
    if (tymax < tmax) tmax = tymax;

    float tzmin = (bmin.z - r.origin.z) / r.direction.z;
    float tzmax = (bmax.z - r.origin.z) / r.direction.z;
    if (tzmin > tzmax) { float tmp = tzmin; tzmin = tzmax; tzmax = tmp; }

    if (tmin > tzmax || tzmin > tmax) return -1f;
    if (tzmin > tmin) tmin = tzmin;
    if (tzmax < tmax) tmax = tzmax;

    return tmax < 0f ? -1f : (tmin >= 0f ? tmin : tmax);
  }

  /** 3D box pick around node position. Half extents are in meters. */
  public PickResult pickNodeBox(float worldX, float worldY, float worldZ, float halfXZ, float halfY) {
    TerrainSpline bestS = null; int bestIdx = -1;
    float bestScore = Float.POSITIVE_INFINITY; // lower is better
    for (TerrainSpline s : splines) {
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
