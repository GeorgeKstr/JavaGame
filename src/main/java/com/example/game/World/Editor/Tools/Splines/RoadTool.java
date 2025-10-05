package com.example.game.World.Editor.Tools.Splines;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.example.game.Config.cvar.CVars;
import com.example.game.World.Editor.Tools.Tool;
import com.example.game.World.Editor.Layers.SplinesLayer;
import com.example.game.World.Splines.TerrainSpline;
import com.example.game.World.Splines.TerrainSplineNode;
import com.example.game.World.TerrainSystem;
import com.badlogic.gdx.Gdx;

import java.util.List;

/** Tool for placing and editing ROAD splines. */
public final class RoadTool implements Tool {
  private final SplinesLayer layer;
  private final TerrainSystem terrain;
  private final ImmediateModeRenderer20 im = new ImmediateModeRenderer20(65536, false, true, 0);

  private TerrainSpline active;
  private boolean dragging = false;

  public RoadTool(SplinesLayer layer, TerrainSystem terrain) {
    this.layer = layer; this.terrain = terrain;
  }

  @Override public String name() { return "Road"; }

  @Override public void buildUI(Table container, Skin skin) {
    container.add(new Label("Road spline editing", skin, "title")).left().row();
    container.add(new Label("Left-click: select • Drag: move • Ctrl+Click: add node • Click empty: unselect", skin)).left().row();

    // Selected spline info and width control (only show if selected spline is ROAD)
    TerrainSpline sel = layer.getSelectedSpline();
    if (sel != null && sel.getType() != TerrainSpline.Type.ROAD) sel = null;
    String name = (sel == null ? "None" : (sel.getType()+" (#"+sel.hashCode()+")"));
    container.add(new Label("Selected: "+name, skin)).left().padTop(6).row();

    Table wtbl = new Table();
    final Label wLbl = new Label("Width: "+ (sel==null?"-":String.format("%.1f m", sel.width)), skin);
    final Slider wS = new Slider(1f, 30f, 0.5f, false, skin);
    if (sel != null) wS.setValue(sel.width);
    wS.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
      TerrainSpline s = layer.getSelectedSpline(); if (s==null || s.getType()!=TerrainSpline.Type.ROAD) return; s.width = wS.getValue(); s.markRibbonDirty(); wLbl.setText(String.format("Width: %.1f m", s.width)); }});
    wtbl.add(wLbl).left().row();
    wtbl.add(wS).width(240).row();
    container.add(wtbl).left().padBottom(8).row();

    // Texture select + import
    Table t = new Table();
    t.add(new Label("Texture:", skin)).left().padRight(6);
    final SelectBox<String> sb = new SelectBox<>(skin);
    sb.setItems(layer.listSplineTextureNames());
    if (sel != null) {
      String[] names = layer.listSplineTextureNames();
      if (sel.textureId >=0 && sel.textureId < names.length) sb.setSelected(names[sel.textureId]);
    }
    sb.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
      TerrainSpline s = layer.getSelectedSpline(); if (s==null) return; String[] names = layer.listSplineTextureNames();
      String val = sb.getSelected(); for (int i=0;i<names.length;i++){ if (names[i].equals(val)) { s.textureId = i; break; } }
    }});
    TextButton imp = new TextButton("Import...", skin);
    imp.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
      int id = layer.importSplineTextureDesktop(); if (id>=0) { sb.setItems(layer.listSplineTextureNames()); sb.setSelectedIndex(id); TerrainSpline s = layer.getSelectedSpline(); if (s!=null) s.textureId=id; }
    }});
    t.add(sb).width(200).padRight(6);
    t.add(imp).width(100);
    container.add(t).left().row();

    // Nodes list for the selected spline
    if (sel != null) {
      container.add(new Label("Nodes:", skin, "title")).left().padTop(8).row();
      Table lt = new Table();
      final com.badlogic.gdx.scenes.scene2d.ui.List<String> nodeList = new com.badlogic.gdx.scenes.scene2d.ui.List<>(skin);
      nodeList.setItems(buildNodeLabels(sel));
      nodeList.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
        TerrainSpline cur = layer.getSelectedSpline();
        if (cur == null || cur.getType()!=TerrainSpline.Type.ROAD) return;
        int idx = nodeList.getSelectedIndex(); if (idx >= 0 && idx < cur.size()) layer.select(cur, idx);
      }});
      ScrollPane sp = new ScrollPane(nodeList, skin);
      sp.setFadeScrollBars(false);
      lt.add(sp).width(260).height(120).row();
      TextButton del = new TextButton("Delete Selected Node", skin);
      del.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
        TerrainSpline cur = layer.getSelectedSpline();
        if (cur == null || cur.getType()!=TerrainSpline.Type.ROAD) return;
        int idx = nodeList.getSelectedIndex(); if (idx>=0 && idx < cur.size()) { cur.removeNode(idx); layer.select(cur, Math.min(idx, cur.size()-1)); nodeList.setItems(buildNodeLabels(cur)); }
      }});
      lt.add(del).left().padTop(6);
      container.add(lt).left().row();
    }
  }

  private com.badlogic.gdx.utils.Array<String> buildNodeLabels(TerrainSpline s) {
    com.badlogic.gdx.utils.Array<String> arr = new com.badlogic.gdx.utils.Array<>();
    for (int i=0;i<s.size();i++) {
      var n = s.getNodes().get(i);
      arr.add(String.format("%02d: (%.1f, %.1f, %.1f)", i, n.pos.x, n.pos.y, n.pos.z));
    }
    return arr;
  }

  @Override public void onSelected() {
    active = null; dragging = false;
    layer.clearSelection();
  }

  @Override public void onPointerDown(Vector3 world, int button, boolean shift, boolean ctrl) {
    if (button == com.badlogic.gdx.Input.Buttons.LEFT) {
      // Ctrl+Click: add node to selected or start new
      if (ctrl) {
        TerrainSpline sel = layer.getSelectedSpline();
        if (sel == null) sel = layer.createSpline(TerrainSpline.Type.ROAD);
        float y = terrain.heightAt(world.x, world.z, world.y);
        sel.addNode(new TerrainSplineNode(world.x, y, world.z));
        layer.select(sel, sel.size()-1);
        return;
      }
      // Pick nearest node
      var pick = layer.pickNode(world.x, world.z, 3.5f);
      if (pick != null && pick.spline.getType() == TerrainSpline.Type.ROAD) {
        layer.select(pick.spline, pick.nodeIndex);
        dragging = true;
      } else {
        // click empty: unselect
        layer.clearSelection(); dragging = false;
      }
    } else if (button == com.badlogic.gdx.Input.Buttons.RIGHT) {
      active = null; dragging = false;
    }
  }

  @Override public void onPointerDrag(Vector3 world, int button, boolean shift, boolean ctrl) {
    if (!dragging) return;
    TerrainSpline sel = layer.getSelectedSpline();
    int idx = layer.getSelectedNode();
    if (sel == null || idx < 0) return;
    float y = terrain.heightAt(world.x, world.z, world.y);
    sel.setNodePos(idx, new Vector3(world.x, y, world.z));
  }

  @Override public void onPointerUp(Vector3 world, int button, boolean shift, boolean ctrl) { dragging = false; }

  @Override public void render3D(Camera cam) {
    // Draw ROAD ribbons and polylines; highlight selected; draw node hitboxes
    Gdx.gl.glEnable(GL20.GL_BLEND);
    Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    for (TerrainSpline s : layer.getSplines()) {
      if (s.getType() != TerrainSpline.Type.ROAD) continue;
      if (s.isRibbonDirty() && !s.isRibbonRebuilding()) s.rebuildRibbonAsync(terrain.getBaseHeightSampler(), 8);
      if (!s.hasRibbon()) continue;
      boolean sel = (s == layer.getSelectedSpline());
      float r = 0.6f, g = 0.45f, b = 0.2f; // asphalt-ish tint
      float a = 0.5f;

      boolean atop = Boolean.TRUE.equals(CVars.BRUSH_ALWAYS_ON_TOP.get());
      if (atop) { Gdx.gl.glDisable(GL20.GL_DEPTH_TEST); Gdx.gl.glDepthMask(false); }
      Gdx.gl.glDisable(GL20.GL_CULL_FACE);

      // Use cached arrays
      float[] lx = s.getLX(), ly = s.getLY(), lz = s.getLZ();
      float[] rx = s.getRX(), ry = s.getRY(), rz = s.getRZ();
      int n = s.getTriPairs() + 1;
      float yLift = 0.01f;

      // Emit triangles in batches to avoid IMR overflow
      int seg = 0;
      final int maxSegmentsPerBatch = 2500;
      while (seg < n - 1) {
        int end = Math.min(n - 1, seg + maxSegmentsPerBatch);
        im.begin(cam.combined, GL20.GL_TRIANGLES);
        for (int k = seg; k < end; k++) {
          im.color(r,g,b,a); im.vertex(lx[k],   ly[k]  +yLift, lz[k]);
          im.color(r,g,b,a); im.vertex(rx[k],   ry[k]  +yLift, rz[k]);
          im.color(r,g,b,a); im.vertex(lx[k+1], ly[k+1]+yLift, lz[k+1]);
          im.color(r,g,b,a); im.vertex(rx[k],   ry[k]  +yLift, rz[k]);
          im.color(r,g,b,a); im.vertex(rx[k+1], ry[k+1]+yLift, rz[k+1]);
          im.color(r,g,b,a); im.vertex(lx[k+1], ly[k+1]+yLift, lz[k+1]);
        }
        im.end();
        seg = end;
      }

      if (atop) { Gdx.gl.glDepthMask(true); Gdx.gl.glEnable(GL20.GL_DEPTH_TEST); }
      Gdx.gl.glEnable(GL20.GL_CULL_FACE);

      // Polyline center for guidance (use midpoints of cached edges)
      float lr = sel ? 1f : 0.9f;
      float lg = sel ? 0.8f : 0.6f;
      float lb = 0.2f;
      float la = sel ? 1f : 0.8f;
      im.begin(cam.combined, GL20.GL_LINES);
      for (int i = 0; i < n - 1; i++) {
        float ax = 0.5f * (lx[i] + rx[i]);
        float ay = 0.5f * (ly[i] + ry[i]) + 0.05f;
        float az = 0.5f * (lz[i] + rz[i]);
        float bx = 0.5f * (lx[i+1] + rx[i+1]);
        float by = 0.5f * (ly[i+1] + ry[i+1]) + 0.05f;
        float bz = 0.5f * (lz[i+1] + rz[i+1]);
        im.color(lr, lg, lb, la); im.vertex(ax, ay, az);
        im.color(lr, lg, lb, la); im.vertex(bx, by, bz);
      }
      im.end();

      // node hitboxes (squares)
      List<com.example.game.World.Splines.TerrainSplineNode> nodes = s.getNodes();
      for (int i=0;i<nodes.size();i++){
        Vector3 p = nodes.get(i).pos;
        float sz = 0.6f;
        float nx = sz, nz = sz;
        float cr= sel && i==layer.getSelectedNode() ? 1f:0.9f;
        float cg= sel && i==layer.getSelectedNode() ? 1f:0.9f;
        float cb= sel && i==layer.getSelectedNode() ? 0.2f:0.2f;
        float ca= 1f;
        im.begin(cam.combined, GL20.GL_LINES);
        im.color(cr,cg,cb,ca); im.vertex(p.x-nx, p.y+0.07f, p.z-nz);
        im.color(cr,cg,cb,ca); im.vertex(p.x+nx, p.y+0.07f, p.z-nz);
        im.color(cr,cg,cb,ca); im.vertex(p.x+nx, p.y+0.07f, p.z-nz);
        im.color(cr,cg,cb,ca); im.vertex(p.x+nx, p.y+0.07f, p.z+nz);
        im.color(cr,cg,cb,ca); im.vertex(p.x+nx, p.y+0.07f, p.z+nz);
        im.color(cr,cg,cb,ca); im.vertex(p.x-nx, p.y+0.07f, p.z+nz);
        im.color(cr,cg,cb,ca); im.vertex(p.x-nx, p.y+0.07f, p.z+nz);
        im.color(cr,cg,cb,ca); im.vertex(p.x-nx, p.y+0.07f, p.z-nz);
        im.end();
      }
    }
    Gdx.gl.glDisable(GL20.GL_BLEND);
  }
}
