package com.example.game.World.Editor.Tools.Splines;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.example.game.World.Editor.Tools.Tool;
import com.example.game.World.Editor.Layers.SplinesLayer;
import com.example.game.World.Splines.TerrainSpline;
import com.example.game.World.Splines.TerrainSplineNode;
import com.example.game.World.TerrainSystem;
import com.badlogic.gdx.Gdx;
import com.example.game.World.Editor.EditorContext;
import com.example.game.World.MapResource;

import java.util.HashMap;

import java.util.List;

/** Tool for placing and editing RIVER splines. */
public final class RiverTool implements Tool {
  private final SplinesLayer layer;
  private final TerrainSystem terrain;
  private final ImmediateModeRenderer20 im = new ImmediateModeRenderer20(65536, false, true, 1);
  private final ImmediateModeRenderer20 imSolid = new ImmediateModeRenderer20(65536, false, true, 0);
  private final ImmediateModeRenderer20 imLines = new ImmediateModeRenderer20(16384, false, true, 0);
  private final HashMap<Integer, Texture> texCache = new HashMap<>();

  private TerrainSpline active;
  private boolean dragging = false;

  // UI controls to allow syncing on selection changes within the tool
  private Slider widthSlider; private Label widthLabel;
  private Slider depthSlider; private Label depthLabel;
  private Slider steepSlider; private Label steepLabel;
  private Slider ribbonRatioSlider; private Label ribbonRatioLabel;
  private TerrainSpline lastBoundForUI;
  // Newly tracked so values update when selection changes
  private Slider alphaSliderRef; private Label alphaLabelRef;
  private Slider hOffSliderRef; private Label hOffLabelRef;
  private CheckBox texturedCBRef; private TextButton selectTexBtnRef; private Label texLabelRef;
  private com.badlogic.gdx.scenes.scene2d.ui.List<String> nodeList;

  public RiverTool(SplinesLayer layer, TerrainSystem terrain) {
    this.layer = layer; this.terrain = terrain;
    // Auto-resample ribbons whenever chunks load/unload so ribbons follow terrain without nudging
    this.terrain.onChunksChanged(() -> {
      java.util.List<TerrainSpline> list = this.layer.getSplines();
      for (TerrainSpline s : list) { s.markRibbonDirty(); s.rebuildRibbonAsync(this.terrain.getBaseHeightSampler(), 8); }
    });
  }

  // ----- helpers -----
  private static float projParamOnSegXZ(Vector3 a, Vector3 b, float px, float pz) {
    float vx = b.x - a.x, vz = b.z - a.z;
    float wx = px - a.x, wz = pz - a.z;
    float denom = vx*vx + vz*vz;
    if (denom <= 1e-6f) return 0f;
    return (vx*wx + vz*wz) / denom; // un-clamped t
  }

  private static float dist2ToSegXZ(Vector3 a, Vector3 b, float px, float pz) {
    float t = projParamOnSegXZ(a, b, px, pz);
    if (t < 0f) t = 0f; else if (t > 1f) t = 1f;
    float sx = a.x + (b.x - a.x) * t;
    float sz = a.z + (b.z - a.z) * t;
    float dx = px - sx, dz = pz - sz;
    return dx*dx + dz*dz;
  }

  @Override public String name() { return "River"; }

  @Override public void buildUI(Table container, Skin skin) {
    container.add(new Label("River spline editing", skin, "title")).left().row();
    container.add(new Label("Left-click: select • Drag: move • Ctrl+Click: add node • Click empty: unselect", skin)).left().row();

    // Selected spline info and width/depth controls (only show if selected spline is RIVER)
    TerrainSpline sel = layer.getSelectedSpline();
    if (sel != null && sel.getType() != TerrainSpline.Type.RIVER) sel = null;
    String name = (sel == null ? "None" : (sel.getType()+" (#"+sel.hashCode()+")"));
    container.add(new Label("Selected: "+name, skin)).left().padTop(6).row();

    Table wtbl = new Table();
    widthLabel = new Label("Width: "+ (sel==null?"-":String.format("%.1f m", sel.width)), skin);
    widthSlider = new Slider(1f, 50f, 0.5f, false, skin);
    if (sel != null) widthSlider.setValue(sel.width);
    widthSlider.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
      TerrainSpline s = layer.getSelectedSpline(); if (s==null) return; s.width = widthSlider.getValue(); s.markRibbonDirty(); s.rebuildRibbonAsync(terrain.getBaseHeightSampler(), 8); widthLabel.setText(String.format("Width: %.1f m", s.width)); }});
    wtbl.add(widthLabel).left().row();
    wtbl.add(widthSlider).width(240).row();
    container.add(wtbl).left().padBottom(8).row();

    Table dt = new Table();
    depthLabel = new Label("Depth: "+ (sel==null?"-":String.format("%.1f m", sel.riverDepth)), skin);
    depthSlider = new Slider(0f, 20f, 0.1f, false, skin);
    if (sel != null) depthSlider.setValue(sel.riverDepth);
    depthSlider.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
      TerrainSpline s = layer.getSelectedSpline(); if (s==null || s.getType()!=TerrainSpline.Type.RIVER) return; s.riverDepth = depthSlider.getValue(); s.markRibbonDirty(); s.rebuildRibbonAsync(terrain.getBaseHeightSampler(), 8); depthLabel.setText(String.format("Depth: %.1f m", s.riverDepth)); }});
    dt.add(depthLabel).left().row();
    dt.add(depthSlider).width(240).row();
    container.add(dt).left().padBottom(8).row();

    // Steepness slider (controls edge profile exponent via bankFalloff 0..1)
    Table st = new Table();
    steepLabel = new Label("Steepness: "+ (sel==null?"-":String.format("%.2f", sel.bankFalloff)), skin);
    steepSlider = new Slider(0f, 1f, 0.01f, false, skin);
    if (sel != null) steepSlider.setValue(sel.bankFalloff);
    steepSlider.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
      TerrainSpline s = layer.getSelectedSpline(); if (s==null || s.getType()!=TerrainSpline.Type.RIVER) return; s.bankFalloff = steepSlider.getValue(); s.markRibbonDirty(); s.rebuildRibbonAsync(terrain.getBaseHeightSampler(), 8); steepLabel.setText(String.format("Steepness: %.2f", s.bankFalloff)); }});
    st.add(steepLabel).left().row();
    st.add(steepSlider).width(240).row();
    container.add(st).left().padBottom(8).row();

    // Ribbon Width Ratio slider (scales visual ribbon width vs river width)
    Table rrt = new Table();
    ribbonRatioLabel = new Label("Ribbon overflow: "+ (sel==null?"-":String.format("%.2f", sel.ribbonWidthRatio)), skin);
    ribbonRatioSlider = new Slider(0.25f, 3.0f, 0.01f, false, skin);
    if (sel != null) ribbonRatioSlider.setValue(sel.ribbonWidthRatio);
    ribbonRatioSlider.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
      TerrainSpline s = layer.getSelectedSpline(); if (s==null || s.getType()!=TerrainSpline.Type.RIVER) return; s.ribbonWidthRatio = ribbonRatioSlider.getValue(); ribbonRatioLabel.setText(String.format("Ribbon overflow: %.2f", s.ribbonWidthRatio)); }});
    rrt.add(ribbonRatioLabel).left().row();
    rrt.add(ribbonRatioSlider).width(240).row();
    container.add(rrt).left().padBottom(8).row();

    // Transparency slider (alpha multiplier 0..1)
    Table at = new Table();
    alphaLabelRef = new Label("Transparency: "+ (sel==null?"-":String.format("%.2f", sel.transparency)), skin);
    alphaSliderRef = new Slider(0f, 1f, 0.01f, false, skin);
    if (sel != null) alphaSliderRef.setValue(sel.transparency);
    alphaSliderRef.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
      TerrainSpline s = layer.getSelectedSpline(); if (s==null || s.getType()!=TerrainSpline.Type.RIVER) return; s.transparency = alphaSliderRef.getValue(); alphaLabelRef.setText(String.format("Transparency: %.2f", s.transparency)); }});
    at.add(alphaLabelRef).left().row();
    at.add(alphaSliderRef).width(240).row();
    container.add(at).left().padBottom(8).row();

    // Height Offset slider (-5..+5 m) moves ribbon vertically at render time
    Table ht = new Table();
    hOffLabelRef = new Label("Height Offset: "+ (sel==null?"-":String.format("%.2f m", sel.heightOffset)), skin);
    hOffSliderRef = new Slider(-5f, 5f, 0.01f, false, skin);
    if (sel != null) hOffSliderRef.setValue(sel.heightOffset);
    hOffSliderRef.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
      TerrainSpline s = layer.getSelectedSpline(); if (s==null || s.getType()!=TerrainSpline.Type.RIVER) return; s.heightOffset = hOffSliderRef.getValue(); hOffLabelRef.setText(String.format("Height Offset: %.2f m", s.heightOffset)); }});
    ht.add(hOffLabelRef).left().row();
    ht.add(hOffSliderRef).width(240).row();
    container.add(ht).left().padBottom(8).row();

    // Textured toggle + Texture select using the shared TextureBrowser (open in textures/splines)
    Table t = new Table();
    t.defaults().left().padRight(6);
    texturedCBRef = new CheckBox(" Textured", skin);
    texturedCBRef.setChecked(sel == null ? true : sel.textured);
    texturedCBRef.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
      TerrainSpline s = layer.getSelectedSpline(); if (s == null) return;
      s.textured = texturedCBRef.isChecked();
      // Reset texture id to a known default on any toggle (both enable and disable)
      s.textureId = 0;
      if (texLabelRef != null) texLabelRef.setText("Texture: " + currentTextureLabel(s));
      if (selectTexBtnRef != null) selectTexBtnRef.setDisabled(!s.textured);
    }});
    texLabelRef = new Label("Texture: " + currentTextureLabel(sel), skin);
    selectTexBtnRef = new TextButton("Select Texture...", skin);
    if (sel != null) selectTexBtnRef.setDisabled(!sel.textured);
    selectTexBtnRef.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
      com.example.game.World.Editor.TextureBrowser tb = new com.example.game.World.Editor.TextureBrowser(skin, terrain, "splines", tr -> {
        TerrainSpline s = layer.getSelectedSpline();
        if (s == null) return;
        s.textureId = tr.id; s.textured = true;
        if (texLabelRef != null) texLabelRef.setText("Texture: " + currentTextureLabel(s));
        if (texturedCBRef != null) texturedCBRef.setChecked(true);
      });
      if (container.getStage() != null) tb.show(container.getStage());
    }});
    t.add(texturedCBRef);
    t.add(texLabelRef);
    t.add(selectTexBtnRef);
    container.add(t).left().row();

    // Nodes list (always visible; contents depend on selected spline)
    container.add(new Label("Nodes:", skin, "title")).left().padTop(8).row();
    Table lt = new Table();
    nodeList = new com.badlogic.gdx.scenes.scene2d.ui.List<>(skin);
    nodeList.setItems(buildNodeLabels(sel));
    nodeList.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
      TerrainSpline cur = layer.getSelectedSpline();
      if (cur == null || cur.getType()!=TerrainSpline.Type.RIVER) return;
      int idx = nodeList.getSelectedIndex(); if (idx >= 0 && idx < cur.size()) layer.select(cur, idx);
    }});
    ScrollPane sp = new ScrollPane(nodeList, skin);
    sp.setFadeScrollBars(false);
    lt.add(sp).width(260).height(120).row();
    TextButton del = new TextButton("Delete Selected Node", skin);
    del.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
      TerrainSpline cur = layer.getSelectedSpline();
      if (cur == null || cur.getType()!=TerrainSpline.Type.RIVER) return;
      // Prefer list selection; fallback to layer's selected node
      int idx = (nodeList != null ? nodeList.getSelectedIndex() : -1);
      if (idx < 0 || idx >= cur.size()) idx = layer.getSelectedNode();
      if (idx < 0 || idx >= cur.size()) return;
      cur.removeNode(idx);
      int newSel = Math.min(idx, cur.size()-1);
      layer.select(cur, newSel);
      if (nodeList != null) { nodeList.setItems(buildNodeLabels(cur)); if (newSel >= 0) nodeList.setSelectedIndex(newSel); }
      cur.markRibbonDirty(); cur.rebuildRibbonAsync(terrain.getBaseHeightSampler(), 8);
    }});
    lt.add(del).left().padTop(6);
    container.add(lt).left().row();

    // Bake button
    TextButton bake = new TextButton("Bake River Depth to Terrain", skin);
    bake.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
      // Install background river baking so loads/unloads always apply depths
      int spp = 12; // sampling density along spline segments
      terrain.installRiverBake(layer.getSplines(), spp);
      // Force reload so current area reflects baked heights
      terrain.reloadAllChunks();
      // Rebuild ribbons after geometry changes
      for (TerrainSpline s : layer.getSplines()) { s.markRibbonDirty(); s.rebuildRibbonAsync(terrain.getBaseHeightSampler(), 8); }
    }});
    container.add(bake).left().padTop(8).row();
  }

  @Override public void onSelected() { active = null; dragging = false; layer.clearSelection(); }

  @Override public void onPointerDown(Vector3 world, int button, boolean shift, boolean ctrl) {
    if (button == com.badlogic.gdx.Input.Buttons.LEFT) {
      if (ctrl) {
        TerrainSpline spline = layer.getSelectedSpline();
        float baseY = terrain.getBaseHeightSampler().heightAt(world.x, world.z, world.y);
        float y = baseY + 0.05f;
        if (spline == null || spline.getType() != TerrainSpline.Type.RIVER || layer.getSelectedNode() < 0) {
          // No valid selection: create or append to a new spline
          if (spline == null) spline = layer.createSpline(TerrainSpline.Type.RIVER);
          spline.addNode(new TerrainSplineNode(world.x, y, world.z));
          layer.select(spline, spline.size()-1);
          spline.markRibbonDirty();
          spline.rebuildRibbonAsync(terrain.getBaseHeightSampler(), 8);
          if (nodeList != null) nodeList.setItems(buildNodeLabels(spline)); nodeList.setSelectedIndex(spline.size()-1);
          if (widthSlider != null) { widthSlider.setValue(spline.width); widthLabel.setText(String.format("Width: %.1f m", spline.width)); }
          if (depthSlider != null) { depthSlider.setValue(spline.riverDepth); depthLabel.setText(String.format("Depth: %.1f m", spline.riverDepth)); }
          if (steepSlider != null) { steepSlider.setValue(spline.bankFalloff); steepLabel.setText(String.format("Steepness: %.2f", spline.bankFalloff)); }
          return;
        }

        int n = spline.size();
        int iSel = layer.getSelectedNode();
        List<TerrainSplineNode> nodes = spline.getNodes();
        int insertAt;

        if (n == 1) {
          // Single node: make this the new second node
          insertAt = 1;
        } else if (iSel == 0) {
          // First node: decide between split [0,1] or prepend
          Vector3 a = nodes.get(0).pos, b = nodes.get(1).pos;
          float tRaw = projParamOnSegXZ(a, b, world.x, world.z);
          if (tRaw < 0f) insertAt = 0; // before first
          else insertAt = 1;            // split between 0 and 1
        } else if (iSel == n - 1) {
          // Last node: decide between append or split [n-2,n-1]
          Vector3 a = nodes.get(n-2).pos, b = nodes.get(n-1).pos;
          float tRaw = projParamOnSegXZ(a, b, world.x, world.z);
          if (tRaw > 1f) insertAt = n; // after last
          else insertAt = n - 1;       // split between n-2 and n-1 (insert before last)
        } else {
          // Middle node: choose nearer adjacent segment
          Vector3 a0 = nodes.get(iSel-1).pos, b0 = nodes.get(iSel).pos;
          Vector3 a1 = nodes.get(iSel).pos, b1 = nodes.get(iSel+1).pos;
          float d0 = dist2ToSegXZ(a0, b0, world.x, world.z);
          float d1 = dist2ToSegXZ(a1, b1, world.x, world.z);
          insertAt = (d0 <= d1) ? iSel : iSel + 1;
        }

        // Clamp and insert
        insertAt = Math.max(0, Math.min(insertAt, spline.size()));
        spline.insertNode(insertAt, new TerrainSplineNode(world.x, y, world.z));
        layer.select(spline, insertAt);
        spline.markRibbonDirty();
        spline.rebuildRibbonAsync(terrain.getBaseHeightSampler(), 8);
        if (nodeList != null) nodeList.setItems(buildNodeLabels(spline)); nodeList.setSelectedIndex(insertAt);
        if (widthSlider != null) { widthSlider.setValue(spline.width); widthLabel.setText(String.format("Width: %.1f m", spline.width)); }
        if (depthSlider != null) { depthSlider.setValue(spline.riverDepth); depthLabel.setText(String.format("Depth: %.1f m", spline.riverDepth)); }
        if (steepSlider != null) { steepSlider.setValue(spline.bankFalloff); steepLabel.setText(String.format("Steepness: %.2f", spline.bankFalloff)); }
        if (alphaSliderRef != null) { alphaSliderRef.setValue(spline.transparency); }
        if (alphaLabelRef != null) { alphaLabelRef.setText(String.format("Transparency: %.2f", spline.transparency)); }
        if (hOffSliderRef != null) { hOffSliderRef.setValue(spline.heightOffset); }
        if (hOffLabelRef != null) { hOffLabelRef.setText(String.format("Height Offset: %.2f m", spline.heightOffset)); }
        if (ribbonRatioSlider != null) { ribbonRatioSlider.setValue(spline.ribbonWidthRatio); }
        if (ribbonRatioLabel != null) { ribbonRatioLabel.setText(String.format("Ribbon overflow: %.2f", spline.ribbonWidthRatio)); }
        if (texturedCBRef != null) texturedCBRef.setChecked(spline.textured);
        if (selectTexBtnRef != null) selectTexBtnRef.setDisabled(!spline.textured);
        if (texLabelRef != null) texLabelRef.setText("Texture: " + currentTextureLabel(spline));
        return;
      }
      // Ray-vs-AABB picking (ignores terrain); uses current mouse screen coords
      int sx = Gdx.input.getX();
      int sy = Gdx.input.getY();
      var pick = layer.pickNodeRayAABB(EditorContext.getCamera(), sx, sy, 1.0f, 0.4f, 0.8f);
      if (pick != null) {
        layer.select(pick.spline, pick.nodeIndex); dragging = true;
        // Sync sliders to picked spline
        TerrainSpline ssel = layer.getSelectedSpline();
        if (ssel != null && ssel.getType() == TerrainSpline.Type.RIVER) {
          if (nodeList != null) { nodeList.setItems(buildNodeLabels(ssel)); nodeList.setSelectedIndex(pick.nodeIndex); }
          if (widthSlider != null) { widthSlider.setValue(ssel.width); widthLabel.setText(String.format("Width: %.1f m", ssel.width)); }
          if (depthSlider != null) { depthSlider.setValue(ssel.riverDepth); depthLabel.setText(String.format("Depth: %.1f m", ssel.riverDepth)); }
          if (steepSlider != null) { steepSlider.setValue(ssel.bankFalloff); steepLabel.setText(String.format("Steepness: %.2f", ssel.bankFalloff)); }
          if (alphaSliderRef != null) { alphaSliderRef.setValue(ssel.transparency); }
          if (alphaLabelRef != null) { alphaLabelRef.setText(String.format("Transparency: %.2f", ssel.transparency)); }
          if (hOffSliderRef != null) { hOffSliderRef.setValue(ssel.heightOffset); }
          if (hOffLabelRef != null) { hOffLabelRef.setText(String.format("Height Offset: %.2f m", ssel.heightOffset)); }
          if (ribbonRatioSlider != null) { ribbonRatioSlider.setValue(ssel.ribbonWidthRatio); }
          if (ribbonRatioLabel != null) { ribbonRatioLabel.setText(String.format("Ribbon overflow: %.2f", ssel.ribbonWidthRatio)); }
          if (texturedCBRef != null) texturedCBRef.setChecked(ssel.textured);
          if (selectTexBtnRef != null) selectTexBtnRef.setDisabled(!ssel.textured);
          if (texLabelRef != null) texLabelRef.setText("Texture: " + currentTextureLabel(ssel));
        }
      }
      else { 
        layer.clearSelection(); 
        dragging = false; 
        if (nodeList != null) nodeList.clearItems();
      }
    } else if (button == com.badlogic.gdx.Input.Buttons.RIGHT) {
      active = null; dragging = false;
    }
  }

  @Override public void onPointerDrag(Vector3 world, int button, boolean shift, boolean ctrl) {
    // Do nothing here; dragging is handled in update() for performance
    return;
  }

  @Override public void update(float dt, Camera cam) {
    // Keep UI sliders in sync with current selection every frame
    TerrainSpline cur = layer.getSelectedSpline();
    if (cur != null && cur.getType() == TerrainSpline.Type.RIVER) {
      if (alphaSliderRef != null && Math.abs(alphaSliderRef.getValue() - cur.transparency) > 1e-5f) {
        alphaSliderRef.setValue(cur.transparency);
        if (alphaLabelRef != null) alphaLabelRef.setText(String.format("Transparency: %.2f", cur.transparency));
      }
      if (hOffSliderRef != null && Math.abs(hOffSliderRef.getValue() - cur.heightOffset) > 1e-5f) {
        hOffSliderRef.setValue(cur.heightOffset);
        if (hOffLabelRef != null) hOffLabelRef.setText(String.format("Height Offset: %.2f m", cur.heightOffset));
      }
      if (ribbonRatioSlider != null && Math.abs(ribbonRatioSlider.getValue() - cur.ribbonWidthRatio) > 1e-4f) {
        ribbonRatioSlider.setValue(cur.ribbonWidthRatio);
        if (ribbonRatioLabel != null) ribbonRatioLabel.setText(String.format("Ribbon overflow: %.2f", cur.ribbonWidthRatio));
      }
      // Sync textured checkbox and enforce textureId=0 when untextured
      if (texturedCBRef != null && texturedCBRef.isChecked() != cur.textured) {
        texturedCBRef.setChecked(cur.textured);
      }
      if (selectTexBtnRef != null) selectTexBtnRef.setDisabled(cur == null || !cur.textured);
      if (cur != null && !cur.textured && cur.textureId != 0) {
        cur.textureId = 0;
        if (texLabelRef != null) texLabelRef.setText("Texture: " + currentTextureLabel(cur));
      }
    }

    if (!dragging) return;
    TerrainSpline sel = layer.getSelectedSpline();
    int idx = layer.getSelectedNode();
    if (sel == null || idx < 0 || cam == null) return;
    int sx = Gdx.input.getX();
    int sy = Gdx.input.getY();
    com.badlogic.gdx.math.collision.Ray ray = cam.getPickRay(sx, sy);
    // Use shared TerrainPicking API (same as terrain tools)
    var picker = terrain.getPicker();
    Vector3 hit = picker.rayIntersectTerrain(ray, terrain.getBaseHeightSampler(), 2.5f, 6);
    if (hit == null) hit = picker.rayIntersectTerrainFastPlane(ray, terrain.getBaseHeightSampler());
    sel.setNodePos(idx, new Vector3(hit.x, hit.y + 0.05f, hit.z));
    // Throttled by TerrainSpline.rebuildRibbonAsync internal timer
    sel.markRibbonDirty();
    sel.rebuildRibbonAsync(terrain.getBaseHeightSampler(), 8);
    if (nodeList != null) { nodeList.setItems(buildNodeLabels(sel)); nodeList.setSelectedIndex(idx); }
  }

  @Override public void onPointerUp(Vector3 world, int button, boolean shift, boolean ctrl) {
    if (dragging) {
      TerrainSpline sel = layer.getSelectedSpline();
      if (sel != null && sel.getType() == TerrainSpline.Type.RIVER) {
        sel.markRibbonDirty();
        sel.rebuildRibbonAsync(terrain.getBaseHeightSampler(), 8);
      }
    }
    dragging = false;
  }

  @Override public void render3D(Camera cam) {
    // Draw all river splines as textured ribbons (fallback to colored if texture unavailable)
    Gdx.gl.glEnable(GL20.GL_BLEND);
    Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    for (TerrainSpline s : layer.getSplines()) {
      if (s.getType() != TerrainSpline.Type.RIVER) continue;
      if (!s.hasRibbon()) continue;
      boolean sel = (s == layer.getSelectedSpline());
      float aa = Math.max(0f, Math.min(1f, s.transparency));
      Texture tex = (s.textured ? getSplineTexture(s.textureId) : null);
      float rr, gg, bb;
      if (tex != null) {
        float[] sunTint = computeSunTint();
        rr = sunTint[0]; gg = sunTint[1]; bb = sunTint[2];
      } else {
        // Helper ribbon color similar to centerline/node boxes (light blue)
        rr = 0.08f; gg = 0.50f; bb = 1.00f;
      }
      boolean atop = false;
      if (atop) { Gdx.gl.glDisable(GL20.GL_DEPTH_TEST); Gdx.gl.glDepthMask(false); }
      Gdx.gl.glDisable(GL20.GL_CULL_FACE);

      float[] lx = s.getLX(), ly = s.getLY(), lz = s.getLZ();
      float[] rx = s.getRX(), ry = s.getRY(), rz = s.getRZ();
      int n = s.getTriPairs() + 1;
      float yLift = s.heightOffset + 0.02f;
      float ratio = Math.max(0.01f, s.ribbonWidthRatio);

      // Draw in batches of segments to stay well within internal buffers
      int i = 0;
      final int maxSegmentsPerBatch = 2500; // 2500 * 6 = 15000 verts
      float cumV = 0f; // cumulative V length across batches
      while (i < n - 1) {
        int end = Math.min(n - 1, i + maxSegmentsPerBatch);
        float vLen = 0f; // accumulate along centerline (V axis) for this batch
        if (tex != null) {
          im.begin(cam.combined, GL20.GL_TRIANGLES);
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
            // tri 1
            float nlx0 = ax + (lx[k]   - ax) * ratio; float nlz0 = az + (lz[k]   - az) * ratio;
            float nrx0 = ax + (rx[k]   - ax) * ratio; float nrz0 = az + (rz[k]   - az) * ratio;
            float nlx1 = bx + (lx[k+1] - bx) * ratio; float nlz1 = bz + (lz[k+1] - bz) * ratio;
            float nrx1 = bx + (rx[k+1] - bx) * ratio; float nrz1 = bz + (rz[k+1] - bz) * ratio;
            im.texCoord(0f, cumV + vLen); im.color(rr,gg,bb,aa); im.vertex(nlx0, ly[k]   +yLift, nlz0);
            im.texCoord(1f, cumV + vLen); im.color(rr,gg,bb,aa); im.vertex(nrx0, ry[k]   +yLift, nrz0);
            im.texCoord(0f, cumV + v2);   im.color(rr,gg,bb,aa); im.vertex(nlx1, ly[k+1] +yLift, nlz1);
            // tri 2
            im.texCoord(1f, cumV + vLen); im.color(rr,gg,bb,aa); im.vertex(nrx0, ry[k]   +yLift, nrz0);
            im.texCoord(1f, cumV + v2);   im.color(rr,gg,bb,aa); im.vertex(nrx1, ry[k+1] +yLift, nrz1);
            im.texCoord(0f, cumV + v2);   im.color(rr,gg,bb,aa); im.vertex(nlx1, ly[k+1] +yLift, nlz1);
            vLen = v2;
          }
          im.end();
        } else {
          // Ensure no texture is bound to avoid sampling birds or other layers
          Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, 0);
          imSolid.begin(cam.combined, GL20.GL_TRIANGLES);
          for (int k = i; k < end; k++) {
            float clx0 = 0.5f * (lx[k] + rx[k]);
            float clz0 = 0.5f * (lz[k] + rz[k]);
            float clx1 = 0.5f * (lx[k+1] + rx[k+1]);
            float clz1 = 0.5f * (lz[k+1] + rz[k+1]);
            float ax = clx0, az = clz0;
            float bx = clx1, bz = clz1;
            float du = (float)Math.sqrt((bx-ax)*(bx-ax) + (bz-az)*(bz-az));
            float v2 = vLen + du / Math.max(0.1f, s.width);
            // tri 1
            float nlx0 = ax + (lx[k]   - ax) * ratio; float nlz0 = az + (lz[k]   - az) * ratio;
            float nrx0 = ax + (rx[k]   - ax) * ratio; float nrz0 = az + (rz[k]   - az) * ratio;
            float nlx1 = bx + (lx[k+1] - bx) * ratio; float nlz1 = bz + (lz[k+1] - bz) * ratio;
            float nrx1 = bx + (rx[k+1] - bx) * ratio; float nrz1 = bz + (rz[k+1] - bz) * ratio;
            imSolid.color(rr,gg,bb,aa); imSolid.vertex(nlx0, ly[k]   +yLift, nlz0);
            imSolid.color(rr,gg,bb,aa); imSolid.vertex(nrx0, ry[k]   +yLift, nrz0);
            imSolid.color(rr,gg,bb,aa); imSolid.vertex(nlx1, ly[k+1] +yLift, nlz1);
            // tri 2
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

      // No extra polygons: ratio only scales left/right vertices; caps removed per request

      // Ensure overlays (centerline and node boxes) draw with solid color (no texturing)
      Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, 0);

      // Draw overlays on top: no depth/cull so they are always visible
      Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
      Gdx.gl.glDisable(GL20.GL_CULL_FACE);
      Gdx.gl.glLineWidth(2f);

      // Centerline poly for guidance (midpoints)
      float r = 0.08f, g = sel ? 0.5f : 0.5f, b = 1.0f;
      float a = sel ? 1f : 0.5f;
      imLines.begin(cam.combined, GL20.GL_LINES);
      for (int j = 0; j < n - 1; j++) {
        float ax = 0.5f * (lx[j] + rx[j]);
        float ay = 0.5f * (ly[j] + ry[j]) + 0.05f;
        float az = 0.5f * (lz[j] + rz[j]);
        float bx = 0.5f * (lx[j+1] + rx[j+1]);
        float by = 0.5f * (ly[j+1] + ry[j+1]) + 0.05f;
        float bz = 0.5f * (lz[j+1] + rz[j+1]);
        imLines.color(r, g, b, a); imLines.vertex(ax, ay, az);
        imLines.color(r, g, b, a); imLines.vertex(bx, by, bz);
      }
      imLines.end();

      // node hitboxes (3D wire boxes centered at node's stored height); batch drawing
      List<com.example.game.World.Splines.TerrainSplineNode> nodes = s.getNodes();
      imLines.begin(cam.combined, GL20.GL_LINES);
      for (int k=0;k<nodes.size();k++){
        Vector3 p = nodes.get(k).pos;
        float baseY = p.y - 0.05f; // nodes are kept at base + 0.05f during edits
        float halfX = 1.0f, halfZ = 1.0f;
        float halfYAbove = 0.6f, halfYBelow = 0.2f; // extend more above baseline
        float y0 = baseY - halfYBelow;
        float y1 = baseY + halfYAbove;
        float x0 = p.x - halfX, x1 = p.x + halfX;
        float z0 = p.z - halfZ, z1 = p.z + halfZ;

        // Colors: light blue when selected spline, darker blue when not; selected node = red
        int selIdx = layer.getSelectedNode();
        float cr, cg, cb, ca;
        if (sel && k == selIdx) {
          cr = 1.0f; cg = 0.25f; cb = 0.25f; ca = 1.0f; // selected node: red
        } else if (sel) {
          cr = 0.20f; cg = 0.60f; cb = 1.00f; ca = 1.0f; // selected spline: lighter blue
        } else {
          cr = 0.08f; cg = 0.35f; cb = 0.60f; ca = 0.9f; // non-selected spline: darker blue
        }
        // bottom rectangle
        imLines.color(cr,cg,cb,ca); imLines.vertex(x0, y0, z0); imLines.color(cr,cg,cb,ca); imLines.vertex(x1, y0, z0);
        imLines.color(cr,cg,cb,ca); imLines.vertex(x1, y0, z0); imLines.color(cr,cg,cb,ca); imLines.vertex(x1, y0, z1);
        imLines.color(cr,cg,cb,ca); imLines.vertex(x1, y0, z1); imLines.color(cr,cg,cb,ca); imLines.vertex(x0, y0, z1);
        imLines.color(cr,cg,cb,ca); imLines.vertex(x0, y0, z1); imLines.color(cr,cg,cb,ca); imLines.vertex(x0, y0, z0);
        // top rectangle
        imLines.color(cr,cg,cb,ca); imLines.vertex(x0, y1, z0); imLines.color(cr,cg,cb,ca); imLines.vertex(x1, y1, z0);
        imLines.color(cr,cg,cb,ca); imLines.vertex(x1, y1, z0); imLines.color(cr,cg,cb,ca); imLines.vertex(x1, y1, z1);
        imLines.color(cr,cg,cb,ca); imLines.vertex(x1, y1, z1); imLines.color(cr,cg,cb,ca); imLines.vertex(x0, y1, z1);
        imLines.color(cr,cg,cb,ca); imLines.vertex(x0, y1, z1); imLines.color(cr,cg,cb,ca); imLines.vertex(x0, y1, z0);
        // vertical edges
        imLines.color(cr,cg,cb,ca); imLines.vertex(x0, y0, z0); imLines.color(cr,cg,cb,ca); imLines.vertex(x0, y1, z0);
        imLines.color(cr,cg,cb,ca); imLines.vertex(x1, y0, z0); imLines.color(cr,cg,cb,ca); imLines.vertex(x1, y1, z0);
        imLines.color(cr,cg,cb,ca); imLines.vertex(x1, y0, z1); imLines.color(cr,cg,cb,ca); imLines.vertex(x1, y1, z1);
        imLines.color(cr,cg,cb,ca); imLines.vertex(x0, y0, z1); imLines.color(cr,cg,cb,ca); imLines.vertex(x0, y1, z1);
      }
      imLines.end();

      // Restore state so subsequent splines render with depth/cull
      Gdx.gl.glLineWidth(1f);
      Gdx.gl.glEnable(GL20.GL_CULL_FACE);
      Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    }
    Gdx.gl.glDisable(GL20.GL_BLEND);
  }

  private com.badlogic.gdx.utils.Array<String> buildNodeLabels(TerrainSpline s) {
    com.badlogic.gdx.utils.Array<String> arr = new com.badlogic.gdx.utils.Array<>();
    if (s == null) return arr;
    for (int i=0;i<s.size();i++) {
      var n = s.getNodes().get(i);
      arr.add(String.format("%02d: (%.1f, %.1f, %.1f)", i, n.pos.x, n.pos.y, n.pos.z));
    }
    return arr;
  }

  private String currentTextureLabel(TerrainSpline s) {
    if (s == null) return "-";
    String[] names = layer.listSplineTextureNames();
    int id = s.textureId;
    if (id >= 0 && id < names.length) return names[id];
    return "-";
  }

  private Texture getSplineTexture(int id) {
    if (id < 0) return null;
    Texture cached = texCache.get(id);
    if (cached != null) return cached;
    // Resolve texture path by MapResource manifest id (shared list)
    MapResource map = terrain.getMap();
    var list = map.getManifest().groundTextures;
    if (id >= list.size()) return null;
    String path = list.get(id).file;
    byte[] bytes = map.getTextureBytes(path);
    if (bytes == null || bytes.length == 0) return null;
    Texture t = new Texture(new com.badlogic.gdx.graphics.Pixmap(bytes, 0, bytes.length));
    t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
    t.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
    texCache.put(id, t);
    return t;
  }

  // Compute a simple day/night tint similar in spirit to terrain lighting.
  // Uses time-of-day CVAR to compute a sun direction and derives an intensity from elevation.
  private float[] computeSunTint() {
    // Time of day 0..24
    float hour = com.example.game.Config.cvar.CVars.RENDER_TIME_OF_DAY.get();
    float t = (hour % 24f) / 24f;
    float az = (float)(t * Math.PI * 2.0); // 0..2π
    // Equinox-like elevation curve (peak at noon)
    float maxElevDeg = 75f;
    float maxElevRad = (float)(Math.toRadians(maxElevDeg));
    float elev = (float)(maxElevRad * Math.sin(az - Math.PI * 0.5));
    float sinE = (float)Math.sin(elev);
    float cosE = (float)Math.cos(elev);
    // World orientation consistent with EditorScreen
    float sunY = sinE; // elevation drives brightness

    // Intensity from elevation; keep some ambient so nights aren't black
    float intensity = Math.max(0f, sunY);
    intensity = 0.2f + 0.8f * intensity; // 0.2 at night .. 1.0 at noon

    // Warm/cool balance from CVAR
    float warmth = com.example.game.Config.cvar.CVars.RENDER_SKY_WARMTH.get(); // 0..1
    float coolR=0.60f, coolG=0.70f, coolB=0.80f;
    float warmR=0.95f, warmG=0.88f, warmB=0.72f;
    float r = coolR + (warmR - coolR) * warmth;
    float g = coolG + (warmG - coolG) * warmth;
    float b = coolB + (warmB - coolB) * warmth;

    // Apply intensity
    r *= intensity; g *= intensity; b *= intensity;
    return new float[]{ r, g, b };
  }
}
