package com.example.game.World.Editor.Tools.Splines;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.example.game.World.Editor.Layers.SplinesLayer;
import com.example.game.World.Splines.TerrainSpline;
import com.example.game.World.TerrainSystem;
import com.badlogic.gdx.Gdx;
import com.example.game.World.Editor.Render.NodeBoxRenderer;
import com.example.game.World.Editor.Render.RenderMode;

/** Tool for placing and editing RIVER splines. */
public final class RiverTool extends BaseSplineTool {
  private final ImmediateModeRenderer20 imLines = new ImmediateModeRenderer20(16384, false, true, 0);
  private final NodeBoxRenderer nodeRenderer;


  // UI controls to allow syncing on selection changes within the tool
  private Slider widthSlider; private Label widthLabel;
  private Slider depthSlider; private Label depthLabel;
  private Slider steepSlider; private Label steepLabel;
  private Slider ribbonRatioSlider; private Label ribbonRatioLabel;
  // Newly tracked so values update when selection changes
  private Slider alphaSliderRef; private Label alphaLabelRef;
  private Slider hOffSliderRef; private Label hOffLabelRef;
  private CheckBox reverseFlowCBRef;
  private com.badlogic.gdx.scenes.scene2d.ui.List<String> nodeList;

  public RiverTool(SplinesLayer layer, TerrainSystem terrain) {
    super(layer, terrain);
    this.nodeRenderer = new NodeBoxRenderer(layer, terrain);
    // Auto-resample ribbons whenever chunks load/unload so ribbons follow terrain without nudging
    this.terrain.onChunksChanged(() -> {
      java.util.List<TerrainSpline> list = this.layer.getSplines();
      for (TerrainSpline s : list) { s.markRibbonDirty(); s.rebuildRibbonAsync(this.terrain.getBaseHeightSampler(), 8); }
    });
  }

  // helper methods moved to BaseSplineTool

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
      TerrainSpline s = layer.getSelectedSpline(); if (s==null) return; s.width = widthSlider.getValue(); s.markRibbonDirty(); s.rebuildRibbonAsync(terrain.getBaseHeightSampler(), 2); widthLabel.setText(String.format("Width: %.1f m", s.width)); layer.saveSplinesToMap(); }});
    wtbl.add(widthLabel).left().row();
    wtbl.add(widthSlider).width(240).row();
    container.add(wtbl).left().padBottom(8).row();

    Table dt = new Table();
    depthLabel = new Label("Depth: "+ (sel==null?"-":String.format("%.1f m", sel.riverDepth)), skin);
    depthSlider = new Slider(0f, 20f, 0.1f, false, skin);
    if (sel != null) depthSlider.setValue(sel.riverDepth);
    depthSlider.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
      TerrainSpline s = layer.getSelectedSpline(); if (s==null || s.getType()!=TerrainSpline.Type.RIVER) return; s.riverDepth = depthSlider.getValue(); s.markRibbonDirty(); s.rebuildRibbonAsync(terrain.getBaseHeightSampler(), 2); depthLabel.setText(String.format("Depth: %.1f m", s.riverDepth)); layer.saveSplinesToMap(); }});
    dt.add(depthLabel).left().row();
    dt.add(depthSlider).width(240).row();
    container.add(dt).left().padBottom(8).row();

    // Steepness slider (controls edge profile exponent via bankFalloff 0..1)
    Table st = new Table();
    steepLabel = new Label("Steepness: "+ (sel==null?"-":String.format("%.2f", sel.bankFalloff)), skin);
    steepSlider = new Slider(0f, 1f, 0.01f, false, skin);
    if (sel != null) steepSlider.setValue(sel.bankFalloff);
    steepSlider.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
      TerrainSpline s = layer.getSelectedSpline(); if (s==null || s.getType()!=TerrainSpline.Type.RIVER) return; s.bankFalloff = steepSlider.getValue(); s.markRibbonDirty(); s.rebuildRibbonAsync(terrain.getBaseHeightSampler(), 2); steepLabel.setText(String.format("Steepness: %.2f", s.bankFalloff)); layer.saveSplinesToMap(); }});
    st.add(steepLabel).left().row();
    st.add(steepSlider).width(240).row();
    container.add(st).left().padBottom(8).row();

    // Ribbon Width Ratio slider (scales visual ribbon width vs river width)
    Table rrt = new Table();
    ribbonRatioLabel = new Label("Ribbon overflow: "+ (sel==null?"-":String.format("%.2f", sel.ribbonWidthRatio)), skin);
    ribbonRatioSlider = new Slider(0.25f, 3.0f, 0.01f, false, skin);
    if (sel != null) ribbonRatioSlider.setValue(sel.ribbonWidthRatio);
    ribbonRatioSlider.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
      TerrainSpline s = layer.getSelectedSpline(); if (s==null || s.getType()!=TerrainSpline.Type.RIVER) return; s.ribbonWidthRatio = ribbonRatioSlider.getValue(); ribbonRatioLabel.setText(String.format("Ribbon overflow: %.2f", s.ribbonWidthRatio)); layer.saveSplinesToMap(); }});
    rrt.add(ribbonRatioLabel).left().row();
    rrt.add(ribbonRatioSlider).width(240).row();
    container.add(rrt).left().padBottom(8).row();

    // Transparency slider (alpha multiplier 0..1)
    Table at = new Table();
    alphaLabelRef = new Label("Transparency: "+ (sel==null?"-":String.format("%.2f", sel.transparency)), skin);
    alphaSliderRef = new Slider(0f, 1f, 0.01f, false, skin);
    if (sel != null) alphaSliderRef.setValue(sel.transparency);
    alphaSliderRef.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
      TerrainSpline s = layer.getSelectedSpline(); if (s==null || s.getType()!=TerrainSpline.Type.RIVER) return; s.transparency = alphaSliderRef.getValue(); alphaLabelRef.setText(String.format("Transparency: %.2f", s.transparency)); layer.saveSplinesToMap(); }});
    at.add(alphaLabelRef).left().row();
    at.add(alphaSliderRef).width(240).row();
    container.add(at).left().padBottom(8).row();

    // Height Offset slider (-5..+5 m) moves ribbon vertically at render time
    Table ht = new Table();
    hOffLabelRef = new Label("Height Offset: "+ (sel==null?"-":String.format("%.2f m", sel.heightOffset)), skin);
    hOffSliderRef = new Slider(-5f, 5f, 0.01f, false, skin);
    if (sel != null) hOffSliderRef.setValue(sel.heightOffset);
    hOffSliderRef.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
      TerrainSpline s = layer.getSelectedSpline(); if (s==null || s.getType()!=TerrainSpline.Type.RIVER) return; s.heightOffset = hOffSliderRef.getValue(); hOffLabelRef.setText(String.format("Height Offset: %.2f m", s.heightOffset)); layer.saveSplinesToMap(); }});
    ht.add(hOffLabelRef).left().row();
    ht.add(hOffSliderRef).width(240).row();
    container.add(ht).left().padBottom(8).row();

    // Reverse flow toggle (persisted per river)
    Table t = new Table();
    t.defaults().left().padRight(6);
    reverseFlowCBRef = new CheckBox(" Reverse flow", skin);
    reverseFlowCBRef.setChecked(sel != null && sel.reverseFlow);
    reverseFlowCBRef.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
      TerrainSpline s = layer.getSelectedSpline(); if (s == null || s.getType()!=TerrainSpline.Type.RIVER) return;
      s.reverseFlow = reverseFlowCBRef.isChecked();
      layer.saveSplinesToMap();
    }});
    t.add(reverseFlowCBRef);
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
    lt.add(sp).width(180).height(120).row();
    TextButton del = new TextButton("Delete Node", skin);
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
      cur.markRibbonDirty(); cur.rebuildRibbonAsync(terrain.getBaseHeightSampler(), 2);
      terrain.reloadAllChunks();
    }});
    lt.add(del).left().padTop(6);
    TextButton delSpline = new TextButton("Delete Spline", skin);
    delSpline.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
      TerrainSpline cur = layer.getSelectedSpline();
      if (cur == null || cur.getType()!=TerrainSpline.Type.RIVER) return;
      layer.removeSpline(cur);
      layer.clearSelection();
      if (nodeList != null) nodeList.clearItems();
      layer.saveSplinesToMap();
      terrain.reloadAllChunks();
    }});
    lt.add(delSpline).left().padTop(6).padLeft(6);
    container.add(lt).left().row();

    // (Bake button removed; baking is live. Use global Refresh in HUD if needed.)
  }

  @Override public void onSelected() { super.onSelected(); }

  @Override public void onPointerDown(int button, boolean shift, boolean ctrl) {
    // Delegate pointer behavior to BaseSplineTool
    super.onPointerDown(button, shift, ctrl);
  }

  @Override public void onPointerDrag(int button, boolean shift, boolean ctrl) {
    // Dragging handled by BaseSplineTool.update
    super.onPointerDrag(button, shift, ctrl);
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
      // Sync reverse flow checkbox
      if (reverseFlowCBRef != null && reverseFlowCBRef.isChecked() != cur.reverseFlow) {
        reverseFlowCBRef.setChecked(cur.reverseFlow);
      }
    }
    // Delegate dragging behavior to BaseSplineTool
    super.update(dt, cam);
  }

  @Override public void onPointerUp(int button, boolean shift, boolean ctrl) {
    super.onPointerUp(button, shift, ctrl);
  }

  // ----- Base hooks -----
  @Override protected TerrainSpline.Type handledType() { return TerrainSpline.Type.RIVER; }
  @Override protected float pickHalfXZ() { return 3.0f; }
  @Override protected float pickHalfYBelow() { return 2.5f; }
  @Override protected float pickHalfYAbove() { return 0.5f; }
  @Override protected float dragRayStep() { return 0.25f; }
  @Override protected int dragRaySteps() { return 6; }

  @Override
  protected void onSelectionChangedUI(TerrainSpline ssel, int nodeIndex) {
    if (ssel != null && ssel.getType() == TerrainSpline.Type.RIVER) {
      if (nodeList != null) { nodeList.setItems(buildNodeLabels(ssel)); nodeList.setSelectedIndex(nodeIndex); }
      if (widthSlider != null) { widthSlider.setValue(ssel.width); widthLabel.setText(String.format("Width: %.1f m", ssel.width)); }
      if (depthSlider != null) { depthSlider.setValue(ssel.riverDepth); depthLabel.setText(String.format("Depth: %.1f m", ssel.riverDepth)); }
      if (steepSlider != null) { steepSlider.setValue(ssel.bankFalloff); steepLabel.setText(String.format("Steepness: %.2f", ssel.bankFalloff)); }
      if (alphaSliderRef != null) { alphaSliderRef.setValue(ssel.transparency); }
      if (alphaLabelRef != null) { alphaLabelRef.setText(String.format("Transparency: %.2f", ssel.transparency)); }
      if (hOffSliderRef != null) { hOffSliderRef.setValue(ssel.heightOffset); }
      if (hOffLabelRef != null) { hOffLabelRef.setText(String.format("Height Offset: %.2f m", ssel.heightOffset)); }
      if (ribbonRatioSlider != null) { ribbonRatioSlider.setValue(ssel.ribbonWidthRatio); }
      if (ribbonRatioLabel != null) { ribbonRatioLabel.setText(String.format("Ribbon overflow: %.2f", ssel.ribbonWidthRatio)); }
      if (reverseFlowCBRef != null) reverseFlowCBRef.setChecked(ssel.reverseFlow);
    }
  }

  @Override
  protected void onSelectionClearedUI() {
    if (nodeList != null) nodeList.clearItems();
  }

  @Override public void render3D(Camera cam) {
    // Draw all river splines using procedural water shader (no textures)
    Gdx.gl.glEnable(GL20.GL_BLEND);
    Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    for (TerrainSpline s : layer.getSplines()) {
      if (s.getType() != TerrainSpline.Type.RIVER) continue;
      if (!s.hasRibbon()) continue;
      boolean sel = (s == layer.getSelectedSpline());
      // ribbons are drawn by SplinesLayer; only overlays here
      boolean atop = false;
      if (atop) { Gdx.gl.glDisable(GL20.GL_DEPTH_TEST); Gdx.gl.glDepthMask(false); }
      Gdx.gl.glDisable(GL20.GL_CULL_FACE);

      float[] lx = s.getLX(), ly = s.getLY(), lz = s.getLZ();
      float[] rx = s.getRX(), ry = s.getRY(), rz = s.getRZ();
      int n = s.getTriPairs() + 1;
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

      // Restore state so subsequent splines render with depth/cull
      Gdx.gl.glLineWidth(1f);
      Gdx.gl.glEnable(GL20.GL_CULL_FACE);
      Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    }
    // Render node AABBs once per frame for rivers
    nodeRenderer.render(cam, RenderMode.TOOL_RIVER);
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
}
