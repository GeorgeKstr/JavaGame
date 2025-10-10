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
  // Ribbon renderer (triangles with 1 texcoord set)
  private final com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20 im = new com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20(65536, false, true, 1);
  // Solid-color renderer with no texcoords (prevents sampling any bound textures)
  private final com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20 imSolid = new com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20(65536, false, true, 0);
  private final java.util.HashMap<Integer, com.badlogic.gdx.graphics.Texture> texCache = new java.util.HashMap<>();
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

  // ---------------- Ribbon-only rendering (no overlays) ----------------
  public void renderRibbonsOnly(Camera cam) {
    com.badlogic.gdx.Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
    com.badlogic.gdx.Gdx.gl.glBlendFunc(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA);
    com.badlogic.gdx.Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_CULL_FACE);

    for (TerrainSpline s : splines) {
      if (s.getType() != TerrainSpline.Type.RIVER) continue;
      if (!s.hasRibbon()) continue;
      float aa = Math.max(0f, Math.min(1f, s.transparency));
      com.badlogic.gdx.graphics.Texture tex = (s.textured ? getSplineTexture(s.textureId) : null);
      float rr, gg, bb;
      if (tex != null) {
        float[] sunTint = computeSunTint(); rr = sunTint[0]; gg = sunTint[1]; bb = sunTint[2];
      } else {
        // Helper ribbon color similar to overlays (light blue)
        rr = 0.08f; gg = 0.50f; bb = 1.00f;
      }

      float[] lx = s.getLX(), ly = s.getLY(), lz = s.getLZ();
      float[] rx = s.getRX(), ry = s.getRY(), rz = s.getRZ();
      int n = s.getTriPairs() + 1;
      float yLift = s.heightOffset + 0.02f;
      float ratio = Math.max(0.01f, s.ribbonWidthRatio);

      int i = 0; final int maxSegmentsPerBatch = 2500;
      float cumV = 0f;
      while (i < n - 1) {
        int end = Math.min(n - 1, i + maxSegmentsPerBatch);
        float vLen = 0f;
        if (tex != null) {
          im.begin(cam.combined, com.badlogic.gdx.graphics.GL20.GL_TRIANGLES);
          for (int k = i; k < end; k++) {
            float clx0 = 0.5f * (lx[k] + rx[k]);
            float clz0 = 0.5f * (lz[k] + rz[k]);
            float clx1 = 0.5f * (lx[k+1] + rx[k+1]);
            float clz1 = 0.5f * (lz[k+1] + rz[k+1]);
            float ax = clx0, az = clz0;
            float bx = clx1, bz = clz1;
            float du = (float)Math.sqrt((bx-ax)*(bx-ax) + (bz-az)*(bz-az));
            float v2 = vLen + du / Math.max(0.1f, s.width);
            tex.bind();
            float nlx0 = ax + (lx[k]   - ax) * ratio; float nlz0 = az + (lz[k]   - az) * ratio;
            float nrx0 = ax + (rx[k]   - ax) * ratio; float nrz0 = az + (rz[k]   - az) * ratio;
            float nlx1 = bx + (lx[k+1] - bx) * ratio; float nlz1 = bz + (lz[k+1] - bz) * ratio;
            float nrx1 = bx + (rx[k+1] - bx) * ratio; float nrz1 = bz + (rz[k+1] - bz) * ratio;
            im.texCoord(0f, cumV + vLen); im.color(rr,gg,bb,aa); im.vertex(nlx0, ly[k]   +yLift, nlz0);
            im.texCoord(1f, cumV + vLen); im.color(rr,gg,bb,aa); im.vertex(nrx0, ry[k]   +yLift, nrz0);
            im.texCoord(0f, cumV + v2);   im.color(rr,gg,bb,aa); im.vertex(nlx1, ly[k+1] +yLift, nlz1);
            im.texCoord(1f, cumV + vLen); im.color(rr,gg,bb,aa); im.vertex(nrx0, ry[k]   +yLift, nrz0);
            im.texCoord(1f, cumV + v2);   im.color(rr,gg,bb,aa); im.vertex(nrx1, ry[k+1] +yLift, nrz1);
            im.texCoord(0f, cumV + v2);   im.color(rr,gg,bb,aa); im.vertex(nlx1, ly[k+1] +yLift, nlz1);
            vLen = v2;
          }
          im.end();
        } else {
          com.badlogic.gdx.Gdx.gl.glActiveTexture(com.badlogic.gdx.graphics.GL20.GL_TEXTURE0);
          com.badlogic.gdx.Gdx.gl.glBindTexture(com.badlogic.gdx.graphics.GL20.GL_TEXTURE_2D, 0);
          imSolid.begin(cam.combined, com.badlogic.gdx.graphics.GL20.GL_TRIANGLES);
          for (int k = i; k < end; k++) {
            float clx0 = 0.5f * (lx[k] + rx[k]);
            float clz0 = 0.5f * (lz[k] + rz[k]);
            float clx1 = 0.5f * (lx[k+1] + rx[k+1]);
            float clz1 = 0.5f * (lz[k+1] + rz[k+1]);
            float ax = clx0, az = clz0;
            float bx = clx1, bz = clz1;
            float du = (float)Math.sqrt((bx-ax)*(bx-ax) + (bz-az)*(bz-az));
            float v2 = vLen + du / Math.max(0.1f, s.width);
            float nlx0 = ax + (lx[k]   - ax) * ratio; float nlz0 = az + (lz[k]   - az) * ratio;
            float nrx0 = ax + (rx[k]   - ax) * ratio; float nrz0 = az + (rz[k]   - az) * ratio;
            float nlx1 = bx + (lx[k+1] - bx) * ratio; float nlz1 = bz + (lz[k+1] - bz) * ratio;
            float nrx1 = bx + (rx[k+1] - bx) * ratio; float nrz1 = bz + (rz[k+1] - bz) * ratio;
            imSolid.color(rr,gg,bb,aa); imSolid.vertex(nlx0, ly[k]   +yLift, nlz0);
            imSolid.color(rr,gg,bb,aa); imSolid.vertex(nrx0, ry[k]   +yLift, nrz0);
            imSolid.color(rr,gg,bb,aa); imSolid.vertex(nlx1, ly[k+1] +yLift, nlz1);
            imSolid.color(rr,gg,bb,aa); imSolid.vertex(nrx0, ry[k]   +yLift, nrz0);
            imSolid.color(rr,gg,bb,aa); imSolid.vertex(nrx1, ry[k+1] +yLift, nrz1);
            imSolid.color(rr,gg,bb,aa); imSolid.vertex(nlx1, ly[k+1] +yLift, nlz1);
            vLen = v2;
          }
          imSolid.end();
        }
        i = end;
        cumV += vLen;
      }
    }
    com.badlogic.gdx.Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
  }

  @Override public void render3D(Camera cam) {
    // Always draw ribbons for context
    renderRibbonsOnly(cam);
    // Then let the active tool draw its overlays (e.g., RiverTool nodes/lines) when this layer is active
    super.render3D(cam);
  }

  private com.badlogic.gdx.graphics.Texture getSplineTexture(int id) {
    if (id < 0) return null;
    com.badlogic.gdx.graphics.Texture cached = texCache.get(id);
    if (cached != null) return cached;
    com.example.game.World.MapResource map = terrain.getMap();
    var list = map.getManifest().groundTextures;
    if (id >= list.size()) return null;
    String path = list.get(id).file;
    byte[] bytes = map.getTextureBytes(path);
    if (bytes == null || bytes.length == 0) return null;
    com.badlogic.gdx.graphics.Texture t = new com.badlogic.gdx.graphics.Texture(new com.badlogic.gdx.graphics.Pixmap(bytes, 0, bytes.length));
    t.setFilter(com.badlogic.gdx.graphics.Texture.TextureFilter.Linear, com.badlogic.gdx.graphics.Texture.TextureFilter.Linear);
    t.setWrap(com.badlogic.gdx.graphics.Texture.TextureWrap.Repeat, com.badlogic.gdx.graphics.Texture.TextureWrap.Repeat);
    texCache.put(id, t);
    return t;
  }

  private float[] computeSunTint() {
    float hour = com.example.game.Config.cvar.CVars.RENDER_TIME_OF_DAY.get();
    float t = (hour % 24f) / 24f;
    float az = (float)(t * Math.PI * 2.0);
    float maxElevDeg = 75f;
    float maxElevRad = (float)(Math.toRadians(maxElevDeg));
    float elev = (float)(maxElevRad * Math.sin(az - Math.PI * 0.5));
    float sinE = (float)Math.sin(elev);
    float sunY = sinE;
    float intensity = Math.max(0f, sunY);
    intensity = 0.2f + 0.8f * intensity;
    float warmth = com.example.game.Config.cvar.CVars.RENDER_SKY_WARMTH.get();
    float coolR=0.60f, coolG=0.70f, coolB=0.80f;
    float warmR=0.95f, warmG=0.88f, warmB=0.72f;
    float r = coolR + (warmR - coolR) * warmth;
    float g = coolG + (warmG - coolG) * warmth;
    float b = coolB + (warmB - coolB) * warmth;
    r *= intensity; g *= intensity; b *= intensity;
    return new float[]{ r, g, b };
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
