package com.example.game.World.Editor.Tools.Terrain;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.example.game.World.MapResource;
import com.example.game.World.TerrainSystem;
import com.example.game.World.Editor.Tools.AbstractBrushTool;
import com.example.game.World.Editor.Tools.BrushSettings;
import com.badlogic.gdx.math.Vector3;

/** Texture painting tool with a scrollable thumbnail grid. */
public final class PaintTextureTool extends AbstractBrushTool {
  private final TerrainSystem terrain;
  private final MapResource map;

  // UI
  private Label activeLbl;
  private Image previewImg;
  private Texture previewTex;

  // state
  private int layerIndex = 0;
  private static final int THUMB = 64;   // px square preview

  public PaintTextureTool(TerrainSystem terrain, BrushSettings brush){
    super(brush, terrain);
    this.terrain = terrain;
    this.map = terrain.getMap();
  }

  private void rebuildPreview() {
    // Dispose previous
    if (previewTex != null) { previewTex.dispose(); previewTex = null; }
    var texRefs = map.getManifest().groundTextures;
    if (texRefs == null || texRefs.isEmpty()) { previewImg.setDrawable(null); return; }
    int clamped = Math.max(0, Math.min(layerIndex, texRefs.size()-1));
    MapResource.TextureRef tr = texRefs.get(clamped);
    // Build a small preview texture (use existing helper)
    previewTex = buildThumbnail(tr.file, THUMB, THUMB);
    previewImg.setDrawable(new TextureRegionDrawable(new TextureRegion(previewTex)));
  }

  @Override public String name() { return "Paint"; }

  @Override public void buildUI(Table parent, Skin skin) {
    parent.defaults().left().padBottom(6);

    // Brush sliders
    addBrushUI(parent, skin);

    // Header + selected label
    parent.add(new Label("Texture Layer", skin, "title")).left().row();
    activeLbl = new Label(currentSelectionText(), skin);
    parent.add(activeLbl).left().row();

    // Selected preview + open browser
    Table selRow = new Table();
    selRow.defaults().left().padRight(8);
    previewImg = new Image();
    rebuildPreview();
    TextButton selectBtn = new TextButton("Select Texture...", skin);
    selectBtn.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
      com.example.game.World.Editor.TextureBrowser tb = new com.example.game.World.Editor.TextureBrowser(skin, terrain, "ground", tr -> {
        if (tr == null) return;
        layerIndex = tr.id;
        activeLbl.setText(currentSelectionText());
        rebuildPreview();
      });
      if (parent.getStage() != null) tb.show(parent.getStage());
    }});
    selRow.add(previewImg).size(64,64);
    selRow.add(selectBtn);
    parent.add(selRow).left().row();

    // (Removed legacy scrolling grid and import/refresh; use TextureBrowser instead)
  }

  // (Removed rebuildGrid and scrolling thumbnails)

  private String shortName(MapResource.TextureRef tr) {
    String label = (tr.name != null && !tr.name.isEmpty()) ? tr.name : tr.file;
    int slash = Math.max(label.lastIndexOf('/'), label.lastIndexOf('\\'));
    return (slash >= 0 ? label.substring(slash + 1) : label);
  }

  private Texture buildThumbnail(String pathInZip, int w, int h) {
    byte[] bytes = map.getTextureBytes(pathInZip);
    if (bytes == null || bytes.length == 0) {
      // fallback solid
      Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);
      pm.setColor(0.2f,0.2f,0.2f,1f); pm.fill();
      Texture t = new Texture(pm); pm.dispose();
      return t;
    }
    Pixmap src = new Pixmap(bytes, 0, bytes.length);
    Pixmap scaled = new Pixmap(w, h, Pixmap.Format.RGBA8888);
    scaled.drawPixmap(src, 0, 0, src.getWidth(), src.getHeight(), 0, 0, w, h);
    src.dispose();
    Texture t = new Texture(scaled);
    t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
    scaled.dispose();
    return t;
    // (We keep Texture alive; disposed in rebuildGrid() or when tool/layer deactivates)
  }

  private String currentSelectionText() {
    var list = map.getManifest().groundTextures;
    if (list == null || list.isEmpty()) return "Selected: (none)";
    int clamped = Math.max(0, Math.min(layerIndex, list.size() - 1));
    MapResource.TextureRef tr = list.get(clamped);
    return "Selected: [" + clamped + "] " + shortName(tr);
  }

  // painting
  @Override public void onPointerDown(int button, boolean shift, boolean ctrl) { paint(world); }
  @Override public void onPointerDrag(int button, boolean shift, boolean ctrl) { paint(world); }

  private void paint(Vector3 world) {
    float tile = terrain.getTileSize();
    int gx = (int)Math.floor(world.x / tile);
    int gz = (int)Math.floor(world.z / tile);
    int rCells = Math.max(1, Math.round(brush.getRadius() / tile));
    byte texId = (byte)Math.max(0, Math.min(255, layerIndex));
    if (terrain.getEditor().paintTexCircle(gx, gz, rCells, texId, brush.getStrength(), brush.getFalloff())) {
      terrain.rebuildDirtyChunks();
    }
  }

  /** Optional: call this from your layer's onDeactivate() to free thumbs immediately. */
  public void disposeThumbs() {
    if (previewTex != null) { previewTex.dispose(); previewTex = null; }
  }
}
