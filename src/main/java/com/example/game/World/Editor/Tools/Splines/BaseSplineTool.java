package com.example.game.World.Editor.Tools.Splines;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Vector3;
import com.example.game.World.Editor.EditorContext;
import com.example.game.World.Editor.Layers.SplinesLayer;
import com.example.game.World.Editor.Tools.Tool;
import com.example.game.World.Splines.TerrainSpline;
import com.example.game.World.Splines.TerrainSplineNode;
import com.example.game.World.TerrainSystem;

import java.util.List;

/** Shared pointer/node interaction for spline tools (add, pick, drag, release). */
public abstract class BaseSplineTool implements Tool {
  protected final SplinesLayer layer;
  protected final TerrainSystem terrain;
  protected boolean dragging = false;

  protected BaseSplineTool(SplinesLayer layer, TerrainSystem terrain) {
    this.layer = layer; this.terrain = terrain;
  }

  @Override public void onSelected() {
    dragging = false;
    layer.clearSelection();
    EditorContext.setSelectedPlaceable(null);
  }

  @Override public void onPointerDown(Vector3 world, int button, boolean shift, boolean ctrl) {
    if (button == com.badlogic.gdx.Input.Buttons.LEFT) {
      if (ctrl) {
        handleCtrlInsert(world);
        return;
      }
      // Ray-AABB pick using screen coords
      int sx = Gdx.input.getX();
      int sy = Gdx.input.getY();
      var pick = layer.pickNodeRayAABB(EditorContext.getCamera(), sx, sy, pickHalfXZ(), pickHalfYBelow(), pickHalfYAbove());
      // Only allow selecting splines of the type handled by this tool
      if (pick != null && pick.spline.getType() == handledType()) {
        layer.select(pick.spline, pick.nodeIndex);
        dragging = true;
        EditorContext.setSelectedPlaceable(pick.spline);
        onSelectionChangedUI(pick.spline, pick.nodeIndex);
      } else {
        layer.clearSelection();
        EditorContext.setSelectedPlaceable(null);
        dragging = false;
        onSelectionClearedUI();
      }
    } else if (button == com.badlogic.gdx.Input.Buttons.RIGHT) {
      dragging = false;
    }
  }

  @Override public void onPointerDrag(Vector3 world, int button, boolean shift, boolean ctrl) {
    // Dragging handled in update for performance parity
    return;
  }

  @Override public void update(float dt, Camera cam) {
    if (!dragging) return;
    TerrainSpline sel = layer.getSelectedSpline();
    int idx = layer.getSelectedNode();
    if (sel == null || idx < 0 || cam == null) return;
    int sx = Gdx.input.getX();
    int sy = Gdx.input.getY();
    com.badlogic.gdx.math.collision.Ray ray = cam.getPickRay(sx, sy);
    var picker = terrain.getPicker();
    Vector3 hit = picker.rayIntersectTerrain(ray, terrain.getBaseHeightSampler(), dragRayStep(), dragRaySteps());
    if (hit == null) return;
    sel.setNodePos(idx, new Vector3(hit.x, hit.y + 0.05f, hit.z));
    sel.markRibbonDirty();
    sel.rebuildRibbonAsync(terrain.getBaseHeightSampler(), 2);
    onSelectionChangedUI(sel, idx);
  }

  @Override public void onPointerUp(Vector3 world, int button, boolean shift, boolean ctrl) {
    if (dragging) {
      TerrainSpline sel = layer.getSelectedSpline();
      if (sel != null && sel.getType() == handledType()) {
        sel.markRibbonDirty();
        sel.rebuildRibbonAsync(terrain.getBaseHeightSampler(), 2);
      }
      terrain.reloadAllChunks();
    }
    dragging = false;
  }

  // -------- helpers shared by spline tools --------
  protected void handleCtrlInsert(Vector3 world) {
    TerrainSpline spline = layer.getSelectedSpline();
    float baseY = terrain.getBaseHeightSampler().heightAt(world.x, world.z, world.y);
    float y = baseY + 0.05f;
    if (spline == null || spline.getType() != handledType() || layer.getSelectedNode() < 0) {
      if (spline == null) spline = layer.createSpline(handledType());
      spline.addNode(new TerrainSplineNode(world.x, y, world.z));
      layer.select(spline, spline.size()-1);
      EditorContext.setSelectedPlaceable(spline);
      spline.markRibbonDirty();
      spline.rebuildRibbonAsync(terrain.getBaseHeightSampler(), 2);
      onSelectionChangedUI(spline, spline.size()-1);
      return;
    }

    int n = spline.size();
    int iSel = layer.getSelectedNode();
    List<TerrainSplineNode> nodes = spline.getNodes();
    int insertAt;

    if (n == 1) {
      insertAt = 1;
    } else if (iSel == 0) {
      Vector3 a = nodes.get(0).pos, b = nodes.get(1).pos;
      float tRaw = projParamOnSegXZ(a, b, world.x, world.z);
      if (tRaw < 0f) insertAt = 0; else insertAt = 1;
    } else if (iSel == n - 1) {
      Vector3 a = nodes.get(n-2).pos, b = nodes.get(n-1).pos;
      float tRaw = projParamOnSegXZ(a, b, world.x, world.z);
      if (tRaw > 1f) insertAt = n; else insertAt = n - 1;
    } else {
      Vector3 a0 = nodes.get(iSel-1).pos, b0 = nodes.get(iSel).pos;
      Vector3 a1 = nodes.get(iSel).pos, b1 = nodes.get(iSel+1).pos;
      float d0 = dist2ToSegXZ(a0, b0, world.x, world.z);
      float d1 = dist2ToSegXZ(a1, b1, world.x, world.z);
      insertAt = (d0 <= d1) ? iSel : iSel + 1;
    }

    insertAt = Math.max(0, Math.min(insertAt, spline.size()));
    spline.insertNode(insertAt, new TerrainSplineNode(world.x, y, world.z));
    layer.select(spline, insertAt);
    EditorContext.setSelectedPlaceable(spline);
    spline.markRibbonDirty();
    spline.rebuildRibbonAsync(terrain.getBaseHeightSampler(), 2);
    terrain.reloadAllChunks();
    onSelectionChangedUI(spline, insertAt);
  }

  protected static float projParamOnSegXZ(Vector3 a, Vector3 b, float px, float pz) {
    float vx = b.x - a.x, vz = b.z - a.z;
    float wx = px - a.x, wz = pz - a.z;
    float denom = vx*vx + vz*vz;
    if (denom <= 1e-6f) return 0f;
    return (vx*wx + vz*wz) / denom;
  }

  protected static float dist2ToSegXZ(Vector3 a, Vector3 b, float px, float pz) {
    float t = projParamOnSegXZ(a, b, px, pz);
    if (t < 0f) t = 0f; else if (t > 1f) t = 1f;
    float sx = a.x + (b.x - a.x) * t;
    float sz = a.z + (b.z - a.z) * t;
    float dx = px - sx, dz = pz - sz;
    return dx*dx + dz*dz;
  }

  // -------- hooks for subclasses --------
  protected abstract TerrainSpline.Type handledType();
  protected abstract void onSelectionChangedUI(TerrainSpline s, int nodeIndex);
  protected abstract void onSelectionClearedUI();
  protected abstract float pickHalfXZ();
  protected abstract float pickHalfYBelow();
  protected abstract float pickHalfYAbove();
  protected abstract float dragRayStep();
  protected abstract int dragRaySteps();
}
