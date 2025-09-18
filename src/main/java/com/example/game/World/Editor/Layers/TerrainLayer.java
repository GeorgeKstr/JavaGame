package com.example.game.World.Editor.Layers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.example.game.UI.SkinFactory;
import com.example.game.World.TerrainSystem;
import com.example.game.World.Editor.*;
import com.example.game.World.Editor.IO.MapReader;
import com.example.game.World.Editor.IO.MapWriter;

public class TerrainLayer implements EditorLayer {
  private final TerrainSystem terrain;
  private final EditorState state;
  private final BrushOverlay overlay;
  private final Skin skin;

  public TerrainLayer(TerrainSystem terrain, Skin skin) {
    this.terrain = terrain;
    this.skin = skin;
    this.state = new EditorState();
    this.overlay = new BrushOverlay(64);
  }

  @Override public String id() { return "terrain"; }
  @Override public String displayName() { return "Terrain"; }

  @Override public void onActivate() {}
  @Override public void onDeactivate() {}

  @Override public void update(float dt) {
    overlay.update(EditorContext.cam(), terrain, state.radius(), terrain.getTileSize());
  }

  @Override public void render3D(Camera cam) {
    overlay.draw(cam);
  }

  @Override public void buildUI(Stage ui) {
    // Left panel with brush controls and texture palette
    Table root = new Table(); root.setFillParent(true); root.pad(10); ui.addActor(root);
    Table left = new Table(); left.defaults().pad(4).left();
    root.add(left).top().left().width(360).expandY();

    left.add(new Label("Terrain Tools", skin, "title")).row();

    // Tool buttons
    Table tools = new Table(); tools.defaults().padRight(6);
    TextButton bRaise = new TextButton("1: Raise", skin);
    TextButton bLower = new TextButton("2: Lower", skin);
    TextButton bPaint = new TextButton("3: Paint", skin);
    tools.add(bRaise); tools.add(bLower); tools.add(bPaint);
    left.add(tools).row();
    bRaise.addListener(SkinFactory.onClick(() -> state.setTool(EditorState.Tool.RAISE)));
    bLower.addListener(SkinFactory.onClick(() -> state.setTool(EditorState.Tool.LOWER)));
    bPaint.addListener(SkinFactory.onClick(() -> state.setTool(EditorState.Tool.PAINT)));

    // Radius / Delta
    left.add(new Label("Brush Radius", skin)).row();
    left.add(stepper(() -> state.radius(), v -> state.setRadius(v))).row();

    left.add(new Label("Height Δ", skin)).row();
    left.add(stepper(() -> (int)state.delta(), v -> state.setDelta((short)v))).row();

    // Texture palette
    left.add(new Label("Texture", skin)).row();
    TexturePalette palette = new TexturePalette(skin, new int[]{0,1,2,3,4,5,6,7});
    palette.onChange(() -> state.setTexId(palette.selectedId()));
    left.add(palette.widget()).width(320).height(160).row();
  }

  private Actor stepper(IntGetter get, IntSetter set) {
    Table t = new Table(); t.defaults().padRight(6);
    Label val = new Label(String.valueOf(get.get()), skin);
    TextButton minus = new TextButton("-", skin), plus = new TextButton("+", skin);
    minus.addListener(SkinFactory.onClick(() -> { set.set(Math.max(1, get.get()-1)); val.setText(String.valueOf(get.get())); }));
    plus.addListener(SkinFactory.onClick(() -> { set.set(get.get()+1); val.setText(String.valueOf(get.get())); }));
    t.add(minus); t.add(val).width(52).center(); t.add(plus);
    return t;
  }
  private interface IntGetter { int get(); }
  private interface IntSetter { void set(int v); }

  @Override
  public boolean handleInput(EditorInputRouter.InputEvent e) {
    switch (e.type) {
      case MouseDown -> {
        if (e.buttonLeft) { applyAtMouse(); return true; }
        return false;
      }
      case MouseDrag -> {
        if (Gdx.input.isButtonPressed(com.badlogic.gdx.Input.Buttons.LEFT)) {
          applyAtMouse(); return true;
        }
        return false;
      }
      case KeyDown -> {
        switch (e.keycode) {
          case com.badlogic.gdx.Input.Keys.NUM_1 -> state.setTool(EditorState.Tool.RAISE);
          case com.badlogic.gdx.Input.Keys.NUM_2 -> state.setTool(EditorState.Tool.LOWER);
          case com.badlogic.gdx.Input.Keys.NUM_3 -> state.setTool(EditorState.Tool.PAINT);
        }
      }
    }
    return false;
  }

  private void applyAtMouse() {
    // Precise hit (raw Y; no flip)
    float mx = Gdx.input.getX(), my = Gdx.input.getY();
    var ray = EditorContext.cam().getPickRay(mx, my);
    var hit = BrushOverlay.rayIntersectTerrain(ray, terrain, 0.75f, 8);
    if (hit == null) return;

    int gx = Math.round(hit.x / terrain.getTileSize());
    int gz = Math.round(hit.z / terrain.getTileSize());
    boolean changed = false;
    switch (state.tool()) {
      case RAISE -> changed = terrain.editHeightsCircle(gx, gz, state.radius(), state.delta());
      case LOWER -> changed = terrain.editHeightsCircle(gx, gz, state.radius(), (short)-state.delta());
      case PAINT -> changed = terrain.paintTexCircle(gx, gz, state.radius(), (byte)state.texId());
    }
    if (changed) terrain.rebuildDirtyChunks();
  }

  // Save/load hooks for terrain:
  @Override public void saveToMap(MapWriter w) {
    // TerrainSystem already knows how to encode chunks; ask EditorScreen to orchestrate final map save.
    // Optionally, write terrain-specific settings here (e.g., default texture id).
  }
  @Override public void loadFromMap(MapReader r) {
    // Terrain loaded by TerrainSystem already; nothing extra here for now.
  }
}
