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
import com.example.game.World.Editor.EditorLayer;
// concrete layer
import com.example.game.World.Editor.Layers.TerrainLayer;

public class EditorScreen implements Screen {
  private final GameApp app;
  private final String mapPath;

  // render
  private PerspectiveCamera cam;
  private SimpleShader shader;
  private TerrainSystem terrain;
  private MapResource map;

  // ui
  private Stage ui;
  private Skin skin;
  private Table root;      // root layout
  private Table tabsBar;   // row 1 (top)
  private Table leftPane;  // row 2, col 1 (sidebar)

  // editor infra
  private final LayerManager layers = new LayerManager();
  private final EditorCamera edCam;

  // add a field only if you actually use it inside EditorScreen
private com.example.game.Config.Settings settings;


public EditorScreen(GameApp app, String mapPath, com.example.game.Config.Settings settings) {
    this.app = app;
    this.mapPath = mapPath;
    this.settings = settings;   // store if you need it later
    this.edCam = new EditorCamera(settings);
}


  @Override public void show() {
    // camera
    cam = new PerspectiveCamera(70f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    cam.near = 0.05f; cam.far = 4000f;
    EditorContext.setCamera(cam);

    // shader
    shader = new SimpleShader();
    shader.init();
    shader.setSunDirection(-0.4f, -1.0f, -0.3f);
    shader.setColor(new Color(0.52f, 0.75f, 0.45f, 1f));

    // map/terrain (create blank if missing)
    ensureMapExists(mapPath);
    map = MapResource.loadFromFile(mapPath);
    terrain = new TerrainSystem(map);

    // ui scaffolding
    ui = new Stage(new ScreenViewport());
    skin = SkinFactory.create();

    buildRootLayout();      // root, tabsBar, leftPane
    buildTabsBar();         // top tabs + Save/Back
    registerLayersAndShow();// add TerrainLayer and build its UI into leftPane

    // input: UI first, then router for camera/pan/orbit and layer input
    Gdx.input.setInputProcessor(new InputMultiplexer(ui,
      new EditorInputRouter(layers, new EditorInputRouter.CameraCallbacks() {
        @Override public void orbit(float dx, float dy) { edCam.orbit(dx, dy); }
        @Override public void pan(float dx, float dy)   { edCam.pan(cam, dx, dy); }
        @Override public void zoom(float amountY)       { edCam.zoom(amountY); }
      })
    ));
  }

  private void buildRootLayout() {
    root = new Table();
    root.setFillParent(true);
    ui.addActor(root);

    // Row 1: tabs bar
    tabsBar = new Table();
    tabsBar.defaults().pad(6);
    root.add(tabsBar).growX().left(); // full width
    root.row();

    // Row 2: content row (left tools + spacer)
    leftPane = new Table();
    leftPane.top().left().pad(8);

    ScrollPane leftScroll = new ScrollPane(leftPane, skin);
    leftScroll.setFadeScrollBars(false);
    leftScroll.setScrollingDisabled(true, false);

    root.add(leftScroll).width(360).growY().top().left();
    root.add().grow(); // spacer/right side
  }

  private void buildTabsBar() {
    tabsBar.clear();

    // tabs
    HorizontalGroup tabs = new HorizontalGroup();
    tabs.space(6f);
    tabsBar.add(tabs).left().expandX();

    for (EditorLayer l : layers.all()) {
      TextButton b = new TextButton(l.displayName(), skin);
      tabs.addActor(b);
      b.addListener(SkinFactory.onClick(() -> {
        layers.setActive(l.id());
        rebuildLeftPane();
      }));
    }

    // right: Save / Back
    TextButton save = new TextButton("Save (F5)", skin);
    TextButton back = new TextButton("Back", skin);
    tabsBar.add(save).right().padRight(6);
    tabsBar.add(back).right();

    save.addListener(SkinFactory.onClick(this::saveMap));
    back.addListener(SkinFactory.onClick(() -> app.goToMenu()));
  }

  private void registerLayersAndShow() {
    layers.add(new TerrainLayer(terrain, skin)); // register (more layers later)
    // NOTE: buildTabsBar needs layers already added; rebuild it now so tabs show
    buildTabsBar();
    rebuildLeftPane();
  }

  private void rebuildLeftPane() {
    leftPane.clear();
    layers.buildUI(leftPane, skin);
  }

  private void saveMap() {
    var manifest = terrain.buildManifest();
    var chunkFiles = terrain.encodeChunksToHmx();
    MapResource.save(mapPath, manifest, chunkFiles);
    Gdx.app.log("Editor", "Saved map: " + mapPath);
    map.reload(mapPath);
  }

  private void ensureMapExists(String path) {
    java.nio.file.Path p = java.nio.file.Paths.get(path);
    if (!java.nio.file.Files.exists(p)) {
      try {
        NewMapCreator.createBlankMap(path, 64, 1f, 0.5f, 0, 0, 1, 1);
      } catch (Exception e) {
        throw new RuntimeException("Failed to create blank map at " + path, e);
      }
    }
  }

  @Override public void render(float dt) {
    edCam.update(cam);
    var f = edCam.focus();
    terrain.updateAndStream(f.x, f.z);

    layers.update(dt);

    // 3D pass
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

    layers.render3D(cam);

    // UI pass
    ui.act(dt);
    Gdx.gl.glDisable(GL20.GL_CULL_FACE);
    Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    ui.draw();

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
