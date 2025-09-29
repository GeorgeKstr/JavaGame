package com.example.game.World.Editor.Tools.Terrain;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Scaling;
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
  private Table grid;          // grid of thumbnails inside ScrollPane
  private ScrollPane scroller;

  // state
  private int layerIndex = 0;
  private final Array<Texture> thumbTextures = new Array<>(false, 16); // for cleanup
  private static final int THUMB = 64;   // px square thumbnails
  private static final int COLS = 3;     // thumbnails per row

  public PaintTextureTool(TerrainSystem terrain, BrushSettings brush){
    super(brush, terrain);
    this.terrain = terrain;
    this.map = terrain.getMap();
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

    // Thumbnail grid inside a scroll pane
    grid = new Table();
    grid.defaults().pad(4);
    scroller = new ScrollPane(grid, skin);
    scroller.setFadeScrollBars(false);
    scroller.setScrollingDisabled(true, false);
    parent.add(scroller).width(260).height(180).row();

    rebuildGrid(skin);

    // Import / Refresh buttons
    Table row = new Table();
    TextButton importBtn = new TextButton("Import...", skin);
    TextButton refreshBtn = new TextButton("Refresh", skin);
    row.add(importBtn).padRight(6); row.add(refreshBtn);
    parent.add(row).left().row();

    importBtn.addListener(new ChangeListener() {
      @Override public void changed(ChangeEvent event, Actor actor) {
        int newId = terrain.importGroundTextureDesktop(1.0f); // default uvScale
        if (newId >= 0) {
          layerIndex = newId;
          terrain.reloadGroundTextures();
          rebuildGrid(skin);
          activeLbl.setText(currentSelectionText());
        }
      }
    });
    refreshBtn.addListener(new ChangeListener() {
      @Override public void changed(ChangeEvent event, Actor actor) {
        terrain.reloadGroundTextures();
        rebuildGrid(skin);
        if (layerIndex >= map.getManifest().groundTextures.size()) {
          layerIndex = Math.max(0, map.getManifest().groundTextures.size() - 1);
        }
        activeLbl.setText(currentSelectionText());
      }
    });
  }

  private void rebuildGrid(Skin skin) {
    // clear old UI + textures
    grid.clearChildren();
    for (Texture t : thumbTextures) t.dispose();
    thumbTextures.clear();

    var texRefs = map.getManifest().groundTextures;
    if (texRefs == null || texRefs.isEmpty()) return;

    int col = 0;
    Table row = new Table();
    for (int i = 0; i < texRefs.size(); i++) {
      MapResource.TextureRef tr = texRefs.get(i);

      // build thumbnail texture (scaled to THUMB x THUMB)
      Texture thumb = buildThumbnail(tr.file, THUMB, THUMB);
      thumbTextures.add(thumb);

      // Image + label stacked in a button-like container
      Image img = new Image(thumb);
      img.setScaling(Scaling.fit);
      img.setAlign(Align.center);

      Label name = new Label(shortName(tr), skin);
      name.setAlignment(Align.center);

      Table cell = new Table();
      cell.defaults().growX();
      cell.add(img).size(THUMB, THUMB).row();
      cell.add(name).padTop(2).width(THUMB);

      // Select behavior
      Button btn = new Button(skin, "toggle"); // uses your Skin's toggle style
      btn.add(cell).pad(4);
      btn.setChecked(i == layerIndex);

      final int idx = i;
      btn.addListener(new ChangeListener() {
        @Override public void changed(ChangeEvent event, Actor actor) {
          layerIndex = idx;
          activeLbl.setText(currentSelectionText());
          // ensure only one is checked – manually uncheck others
          for (Actor a : grid.getChildren()) {
            if (a instanceof Button && a != btn) ((Button)a).setChecked(false);
          }
        }
      });

      row.add(btn);
      col++;
      if (col >= COLS) {
        grid.add(row).left().row();
        row = new Table();
        col = 0;
      }
    }
    if (col > 0) grid.add(row).left().row();
    scroller.layout();
  }

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
  @Override public void onPointerDown(Vector3 world, int button, boolean shift, boolean ctrl) { paint(world); }
  @Override public void onPointerDrag(Vector3 world, int button, boolean shift, boolean ctrl) { paint(world); }

  private void paint(Vector3 world) {
    float tile = terrain.getTileSize();
    int gx = (int)Math.floor(world.x / tile);
    int gz = (int)Math.floor(world.z / tile);
    int rCells = Math.max(1, Math.round(brush.getRadius() / tile));
    byte texId = (byte)Math.max(0, Math.min(255, layerIndex));
    if (terrain.paintTexCircle(gx, gz, rCells, texId, brush.getStrength(), brush.getFalloff())) {
      terrain.rebuildDirtyChunks();
    }
  }

  /** Optional: call this from your layer's onDeactivate() to free thumbs immediately. */
  public void disposeThumbs() {
    for (Texture t : thumbTextures) t.dispose();
    thumbTextures.clear();
  }
}
