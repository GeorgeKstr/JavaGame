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

/** Tool for placing and editing ROAD splines. */
public final class RoadTool extends BaseSplineTool {
  private final SplinesLayer layer;
  private final TerrainSystem terrain;
  private final ImmediateModeRenderer20 imLines = new ImmediateModeRenderer20(16384, false, true, 0);
  private final NodeBoxRenderer nodeRenderer;

  private TerrainSpline active;

  // UI controls
  private Slider widthSlider; private Label widthLabel;
  private Slider depthSlider; private Label depthLabel;
  private Slider steepSlider; private Label steepLabel;
  private Slider ribbonRatioSlider; private Label ribbonRatioLabel;
  private Slider alphaSliderRef; private Label alphaLabelRef;
  private Slider hOffSliderRef; private Label hOffLabelRef;
  private CheckBox texturedCBRef; private TextButton selectTexBtnRef; private Label texLabelRef;
  private com.badlogic.gdx.scenes.scene2d.ui.List<String> nodeList;

  public RoadTool(SplinesLayer layer, TerrainSystem terrain) {
    super(layer, terrain);
    this.layer = layer; this.terrain = terrain;
    this.nodeRenderer = new NodeBoxRenderer(layer, terrain);
    // Rebuild ribbons when chunks change so roads follow terrain
    this.terrain.onChunksChanged(() -> {
      java.util.List<TerrainSpline> list = this.layer.getSplines();
      for (TerrainSpline s : list) { s.markRibbonDirty(); s.rebuildRibbonAsync(this.terrain.getBaseHeightSampler(), 8); }
    });
  }

  // helpers moved to BaseSplineTool

  @Override public String name() { return "Road"; }

  @Override public void buildUI(Table container, Skin skin) {
    container.add(new Label("Road spline editing", skin, "title")).left().row();
    container.add(new Label("Left-click: select • Drag: move • Ctrl+Click: add node • Click empty: unselect", skin)).left().row();

    // Selected spline info and width/depth controls (only show if selected spline is ROAD)
    TerrainSpline sel = layer.getSelectedSpline();
    if (sel != null && sel.getType() != TerrainSpline.Type.ROAD) sel = null;
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
      TerrainSpline s = layer.getSelectedSpline(); if (s==null || s.getType()!=TerrainSpline.Type.ROAD) return; s.riverDepth = depthSlider.getValue(); s.markRibbonDirty(); s.rebuildRibbonAsync(terrain.getBaseHeightSampler(), 2); depthLabel.setText(String.format("Depth: %.1f m", s.riverDepth)); layer.saveSplinesToMap(); }});
    dt.add(depthLabel).left().row();
    dt.add(depthSlider).width(240).row();
    container.add(dt).left().padBottom(8).row();

    Table st = new Table();
    steepLabel = new Label("Steepness: "+ (sel==null?"-":String.format("%.2f", sel.bankFalloff)), skin);
    steepSlider = new Slider(0f, 1f, 0.01f, false, skin);
    if (sel != null) steepSlider.setValue(sel.bankFalloff);
    steepSlider.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
      TerrainSpline s = layer.getSelectedSpline(); if (s==null || s.getType()!=TerrainSpline.Type.ROAD) return; s.bankFalloff = steepSlider.getValue(); s.markRibbonDirty(); s.rebuildRibbonAsync(terrain.getBaseHeightSampler(), 2); steepLabel.setText(String.format("Steepness: %.2f", s.bankFalloff)); layer.saveSplinesToMap(); }});
    st.add(steepLabel).left().row();
    st.add(steepSlider).width(240).row();
    container.add(st).left().padBottom(8).row();

    Table rrt = new Table();
    ribbonRatioLabel = new Label("Ribbon overflow: "+ (sel==null?"-":String.format("%.2f", sel.ribbonWidthRatio)), skin);
    ribbonRatioSlider = new Slider(0.25f, 3.0f, 0.01f, false, skin);
    if (sel != null) ribbonRatioSlider.setValue(sel.ribbonWidthRatio);
    ribbonRatioSlider.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
      TerrainSpline s = layer.getSelectedSpline(); if (s==null || s.getType()!=TerrainSpline.Type.ROAD) return; s.ribbonWidthRatio = ribbonRatioSlider.getValue(); ribbonRatioLabel.setText(String.format("Ribbon overflow: %.2f", s.ribbonWidthRatio)); layer.saveSplinesToMap(); }});
    rrt.add(ribbonRatioLabel).left().row();
    rrt.add(ribbonRatioSlider).width(240).row();
    container.add(rrt).left().padBottom(8).row();

    Table at = new Table();
    alphaLabelRef = new Label("Transparency: "+ (sel==null?"-":String.format("%.2f", sel.transparency)), skin);
    alphaSliderRef = new Slider(0f, 1f, 0.01f, false, skin);
    if (sel != null) alphaSliderRef.setValue(sel.transparency);
    alphaSliderRef.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
      TerrainSpline s = layer.getSelectedSpline(); if (s==null || s.getType()!=TerrainSpline.Type.ROAD) return; s.transparency = alphaSliderRef.getValue(); alphaLabelRef.setText(String.format("Transparency: %.2f", s.transparency)); layer.saveSplinesToMap(); }});
    at.add(alphaLabelRef).left().row();
    at.add(alphaSliderRef).width(240).row();
    container.add(at).left().padBottom(8).row();

    Table ht = new Table();
    hOffLabelRef = new Label("Height Offset: "+ (sel==null?"-":String.format("%.2f m", sel.heightOffset)), skin);
    hOffSliderRef = new Slider(-5f, 5f, 0.01f, false, skin);
    if (sel != null) hOffSliderRef.setValue(sel.heightOffset);
    hOffSliderRef.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
      TerrainSpline s = layer.getSelectedSpline(); if (s==null || s.getType()!=TerrainSpline.Type.ROAD) return; s.heightOffset = hOffSliderRef.getValue(); hOffLabelRef.setText(String.format("Height Offset: %.2f m", s.heightOffset)); layer.saveSplinesToMap(); }});
    ht.add(hOffLabelRef).left().row();
    ht.add(hOffSliderRef).width(240).row();
    container.add(ht).left().padBottom(8).row();

    // Textured toggle + Texture Browser integration
    Table t = new Table();
    t.defaults().left().padRight(6);
    texturedCBRef = new CheckBox(" Textured", skin);
    texturedCBRef.setChecked(sel == null ? true : sel.textured);
    texturedCBRef.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
      TerrainSpline s = layer.getSelectedSpline(); if (s == null) return;
      s.textured = texturedCBRef.isChecked();
      s.textureId = 0;
      if (texLabelRef != null) texLabelRef.setText("Texture: " + currentTextureLabel(s));
      if (selectTexBtnRef != null) selectTexBtnRef.setDisabled(!s.textured);
      layer.saveSplinesToMap();
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
        layer.saveSplinesToMap();
      });
      if (container.getStage() != null) tb.show(container.getStage());
    }});
    t.add(texturedCBRef);
    t.add(texLabelRef);
    t.add(selectTexBtnRef);
    container.add(t).left().row();

    // Nodes list
    container.add(new Label("Nodes:", skin, "title")).left().padTop(8).row();
    Table lt = new Table();
    nodeList = new com.badlogic.gdx.scenes.scene2d.ui.List<>(skin);
    nodeList.setItems(buildNodeLabels(sel));
    nodeList.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
      TerrainSpline cur = layer.getSelectedSpline();
      if (cur == null || cur.getType()!=TerrainSpline.Type.ROAD) return;
      int idx = nodeList.getSelectedIndex(); if (idx >= 0 && idx < cur.size()) layer.select(cur, idx);
    }});
    ScrollPane sp = new ScrollPane(nodeList, skin);
    sp.setFadeScrollBars(false);
    lt.add(sp).width(180).height(120).row();
    TextButton del = new TextButton("Delete Node", skin);
    del.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
      TerrainSpline cur = layer.getSelectedSpline();
      if (cur == null || cur.getType()!=TerrainSpline.Type.ROAD) return;
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
      if (cur == null || cur.getType()!=TerrainSpline.Type.ROAD) return;
      layer.removeSpline(cur);
      layer.clearSelection();
      if (nodeList != null) nodeList.clearItems();
      layer.saveSplinesToMap();
      terrain.reloadAllChunks();
    }});
    lt.add(delSpline).left().padTop(6).padLeft(6);
    container.add(lt).left().row();
  }

  @Override public void onSelected() { active = null; super.onSelected(); }

  @Override public void onPointerDown(Vector3 world, int button, boolean shift, boolean ctrl) {
    super.onPointerDown(world, button, shift, ctrl);
  }

  @Override public void onPointerDrag(Vector3 world, int button, boolean shift, boolean ctrl) {
    super.onPointerDrag(world, button, shift, ctrl);
  }

  @Override public void update(float dt, Camera cam) {
    // Keep UI sliders in sync with current selection every frame
    TerrainSpline cur = layer.getSelectedSpline();
    if (cur != null && cur.getType() == TerrainSpline.Type.ROAD) {
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
      if (texturedCBRef != null && texturedCBRef.isChecked() != cur.textured) {
        texturedCBRef.setChecked(cur.textured);
      }
      if (selectTexBtnRef != null) selectTexBtnRef.setDisabled(cur == null || !cur.textured);
      if (cur != null && !cur.textured && cur.textureId != 0) {
        cur.textureId = 0;
        if (texLabelRef != null) texLabelRef.setText("Texture: " + currentTextureLabel(cur));
      }
    }
    super.update(dt, cam);
  }

  @Override public void onPointerUp(Vector3 world, int button, boolean shift, boolean ctrl) {
    super.onPointerUp(world, button, shift, ctrl);
  }

  // ----- Base hooks -----
  @Override protected TerrainSpline.Type handledType() { return TerrainSpline.Type.ROAD; }
  @Override protected float pickHalfXZ() { return 1.0f; }
  @Override protected float pickHalfYBelow() { return 0.4f; }
  @Override protected float pickHalfYAbove() { return 0.8f; }
  @Override protected float dragRayStep() { return 2.5f; }
  @Override protected int dragRaySteps() { return 6; }

  @Override
  protected void onSelectionChangedUI(TerrainSpline ssel, int nodeIndex) {
    if (ssel != null && ssel.getType() == TerrainSpline.Type.ROAD) {
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
      if (texturedCBRef != null) texturedCBRef.setChecked(ssel.textured);
      if (selectTexBtnRef != null) selectTexBtnRef.setDisabled(!ssel.textured);
      if (texLabelRef != null) texLabelRef.setText("Texture: " + currentTextureLabel(ssel));
    }
  }

  @Override
  protected void onSelectionClearedUI() {
    if (nodeList != null) nodeList.clearItems();
  }

  @Override public void render3D(Camera cam) {
    // Road-tool-only ribbons: draw non-textured roads here (textured roads are drawn in background layer)
    layer.renderRoadToolRibbons(cam);
    // Draw overlays only; ribbons are handled by SplinesLayer via SplineRibbonRenderer
    Gdx.gl.glEnable(GL20.GL_BLEND);
    Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    for (TerrainSpline s : layer.getSplines()) {
      if (s.getType() != TerrainSpline.Type.ROAD) continue;
      if (!s.hasRibbon()) continue;
      boolean sel = (s == layer.getSelectedSpline());
      boolean atop = false;
      if (atop) { Gdx.gl.glDisable(GL20.GL_DEPTH_TEST); Gdx.gl.glDepthMask(false); }
      Gdx.gl.glDisable(GL20.GL_CULL_FACE);

      float[] lx = s.getLX(), ly = s.getLY(), lz = s.getLZ();
      float[] rx = s.getRX(), ry = s.getRY(), rz = s.getRZ();
      // Compute a safe sample count in case arrays/triPairs change due to async rebuilds
      if (lx == null || ly == null || lz == null || rx == null || ry == null || rz == null) continue;
      int nFromTri = s.getTriPairs() + 1;
      int len = Math.min(Math.min(lx.length, rx.length), Math.min(ly.length, ry.length));
      len = Math.min(len, Math.min(lz.length, rz.length));
      int n = Math.min(nFromTri, len);
      if (n < 2) continue;

      // Overlays
      Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, 0);
      Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
      Gdx.gl.glDisable(GL20.GL_CULL_FACE);
      Gdx.gl.glLineWidth(2f);

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
      // Node AABBs
      nodeRenderer.render(cam, RenderMode.TOOL_ROAD);

      // Restore state
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
}
