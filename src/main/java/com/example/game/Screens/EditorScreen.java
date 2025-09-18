package com.example.game.Screens;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

import com.example.game.App.GameApp;
import com.example.game.Render.SimpleShader;
import com.example.game.UI.SkinFactory;
import com.example.game.World.MapResource;
import com.example.game.World.NewMapCreator;
import com.example.game.World.TerrainSystem;

// editor framework
import com.example.game.World.Editor.EditorCamera;
import com.example.game.World.Editor.EditorContext;
import com.example.game.World.Editor.EditorInputRouter;
import com.example.game.World.Editor.LayerManager;
// the concrete terrain layer
import com.example.game.World.Editor.Layers.TerrainLayer;

public class EditorScreen implements Screen {

  private final GameApp app;
  private final String mapPath;

  // Rendering
  private PerspectiveCamera cam;
  private SimpleShader shader;
  private TerrainSystem terrain;
  private MapResource map;

  // UI / input
  private Stage ui;
  private Skin skin;
  private final LayerManager layers = new LayerManager();
  private final EditorCamera edCam = new EditorCamera();
  Label fpsLabel;

  public EditorScreen(GameApp app, String mapPath) {
    this.app = app;
    this.mapPath = mapPath;
  }

  @Override public void show() {
    // Camera
    cam = new PerspectiveCamera(70f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    cam.near = 0.05f; cam.far = 4000f;

    // Shader
    shader = new SimpleShader();
    shader.init();
    shader.setSunDirection(-0.4f, -1.0f, -0.3f);
    shader.setColor(new Color(0.52f, 0.75f, 0.45f, 1f));

    // Map/Terrain (create a blank map if missing so the editor always opens)
    ensureMapExists(mapPath);
    map = MapResource.loadFromFile(mapPath);
    terrain = new TerrainSystem(map);

    // UI
    ui = new Stage(new ScreenViewport());
    skin = SkinFactory.create();

    // Set global editor context (camera available to layers/overlays)
    EditorContext.setCamera(cam);

    // Register layers (terrain now; objects/atmos/rpg later)
    layers.add(new TerrainLayer(terrain, skin));
    // layers.add(new ObjectLayer(...));
    // layers.add(new AtmosLayer(...));
    // layers.add(new RpgLayer(...));

    // Build the UI (toolbar + active layer panel)
    buildToolbar();
    layers.buildUI(ui);
    fpsLabel = new Label("FPS: 0", skin);
    fpsLabel.setPosition(10, 10); // bottom-left
    ui.addActor(fpsLabel);

    // Input routing: UI first, then editor router (which delegates to active layer or camera)
    Gdx.input.setInputProcessor(new InputMultiplexer(ui,
      new EditorInputRouter(layers, new EditorInputRouter.CameraCallbacks() {
        @Override public void orbit(float dx, float dy) { edCam.orbit(dx, dy); }
        @Override public void pan(float dx, float dy)   { edCam.pan(cam, dx, dy); }
        @Override public void zoom(float amountY)       { edCam.zoom(amountY); }
      })
    ));
  }

  private void ensureMapExists(String path) {
    java.nio.file.Path p = java.nio.file.Paths.get(path);
    if (!java.nio.file.Files.exists(p)) {
      try {
        // simple blank 64×64, one chunk at (0,0)
        NewMapCreator.createBlankMap(path, 64, 1f, 0.5f, 0, 0, 1, 1);
      } catch (Exception e) {
        throw new RuntimeException("Failed to create blank map at " + path, e);
      }
    }
  }

  private void buildToolbar() {
    Table bar = new Table();
    bar.setFillParent(true);
    bar.top().left().pad(6);
    ui.addActor(bar);

    // Left side: tabs for layers
    HorizontalGroup tabs = new HorizontalGroup();
    tabs.space(6f);
    bar.add(tabs).left().expandX();

    for (var layer : layers.all()) {
      TextButton b = new TextButton(layer.displayName(), skin);
      tabs.addActor(b);
      b.addListener(SkinFactory.onClick(() -> {
        layers.setActive(layer.id());
        refreshLayerUI(); // rebuild layer panel when switching
      }));
    }

    // Right side: Save + Back
    TextButton save = new TextButton("Save (F5)", skin);
    TextButton back = new TextButton("Back", skin);
    bar.add(save).right().padRight(6);
    bar.add(back).right();

    save.addListener(SkinFactory.onClick(this::saveMap));
    back.addListener(SkinFactory.onClick(() -> app.goToMenu()));
  }

  private void refreshLayerUI() {
    // Clear all except the toolbar (which is first actor)
    if (ui.getActors().size > 1) {
      // keep index 0 (toolbar), remove others
      for (int i = ui.getActors().size - 1; i >= 1; --i) ui.getActors().removeIndex(i);
    }
    layers.buildUI(ui);
  }

  private void saveMap() {
    var manifest = terrain.buildManifest();
    java.util.Map<String, byte[]> chunkFiles = terrain.encodeChunksToHmx();
    MapResource.save(mapPath, manifest, chunkFiles);
    Gdx.app.log("Editor", "Saved map: " + mapPath);
  }

  @Override public void render(float dt) {
    // Camera pose
    edCam.update(cam);

    // Stream chunks near the camera focus
    var f = edCam.focus();
    terrain.updateAndStream(f.x, f.z);

    // Per-layer updates (tools, overlays, etc.)
    layers.update(dt);

    // --- 3D pass ---
    Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    Gdx.gl.glClearColor(0.05f, 0.07f, 0.10f, 1f);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    Gdx.gl.glEnable(GL20.GL_CULL_FACE);
    Gdx.gl.glCullFace(GL20.GL_BACK);

    shader.bind();
    shader.setProjectionView(cam.combined);
    terrain.render(shader, cam);
    shader.unbind();

    // Active layer overlays (brush ring, gizmos, etc.)
    layers.render3D(cam);

    // --- UI pass ---
    ui.act(dt);
    fpsLabel.setText("FPS: " + Gdx.graphics.getFramesPerSecond());
    Gdx.gl.glDisable(GL20.GL_CULL_FACE);
    Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    ui.draw();

    // Hotkeys (save/back) here so they work even if the UI doesn't have focus
    if (Gdx.input.isKeyJustPressed(Input.Keys.F5)) saveMap();
    if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) app.goToMenu();
  }

  @Override public void resize(int w, int h) {
    cam.viewportWidth = w; cam.viewportHeight = h; cam.update();
    ui.getViewport().update(w, h, true);
  }

  @Override public void pause() {}
  @Override public void resume() {}
  @Override public void hide() {}

  @Override public void dispose() {
    if (shader != null) shader.dispose();
    if (terrain != null) terrain.dispose();
    if (ui != null) ui.dispose();
    if (skin != null) skin.dispose();
  }
}
