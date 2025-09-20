package com.example.game.Screens;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

import com.example.game.App.GameApp;
import com.example.game.Render.SimpleShader;
import com.example.game.Render.SkyRenderer;
import com.example.game.Render.GodRaysRenderer;
import com.example.game.Render.SimpleSolidShader;
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
  private final SkyRenderer sky = new SkyRenderer();
  private float elapsedSeconds = 0f;

  // god rays (you already added these classes)
  private final GodRaysRenderer godrays = new GodRaysRenderer();
  private final SimpleSolidShader solid = new SimpleSolidShader();
  private final Vector2 sunUV = new Vector2();

  // sun / time-of-day
  private float hour = 12f; // 0..24
  private final Vector3 sunDirWorld = new Vector3(-0.4f, -1f, -0.3f).nor();

  // ui
  private Stage ui;
  private Skin skin;
  private Table root;      // root layout
  private Table tabsBar;   // row 1 (top)
  private Table leftPane;  // row 2, col 1 (sidebar)
  private Table rightPane; // row 2, right controls

  private Label hourLabel;
  private Slider hourSlider;

  // NEW: god rays intensity slider
  private Label raysLabel;
  private Slider raysSlider;

  private Label distanceLabel;
  private Slider distanceSlider;

  // NEW: FPS counter
  private Label fpsLabel;

  // editor infra
  private final LayerManager layers = new LayerManager();
  private final EditorCamera edCam;

  private com.example.game.Config.Settings settings;

  public EditorScreen(GameApp app, String mapPath, com.example.game.Config.Settings settings) {
    this.app = app;
    this.mapPath = mapPath;
    this.settings = settings;
    this.edCam = new EditorCamera(settings);
  }

  @Override public void show() {
    // camera
    cam = new PerspectiveCamera(70f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    cam.near = 0.05f; cam.far = 4000f;
    EditorContext.setCamera(cam);

    // sky & terrain shader
    sky.init();
    shader = new SimpleShader();
    shader.init();
    shader.setColor(new Color(0.52f, 0.75f, 0.45f, 1f));

    // init sun
    updateSunFromHour();
    shader.setSunDirection(sunDirWorld.x, sunDirWorld.y, sunDirWorld.z);

    // map/terrain
    ensureMapExists(mapPath);
    map = MapResource.loadFromFile(mapPath);
    terrain = new TerrainSystem(map);

    // god rays resources (and solid color shader for silhouettes)
    godrays.init(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    solid.init();

    // ui scaffolding
    ui = new Stage(new ScreenViewport());
    skin = SkinFactory.create();

    buildRootLayout();      // root, tabsBar, leftPane, rightPane
    buildTabsBar();         // top tabs + Save/Back + FPS
    registerLayersAndShow();// add TerrainLayer and build its UI into leftPane

    // input: UI first, then editor input
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
    root.add(tabsBar).growX().left();
    root.row();

    // Row 2: content row (left tools + center spacer + right controls)
    leftPane = new Table();
    leftPane.top().left().pad(8);

    ScrollPane leftScroll = new ScrollPane(leftPane, skin);
    leftScroll.setFadeScrollBars(false);
    leftScroll.setScrollingDisabled(true, false);

    rightPane = new Table();
    rightPane.top().right().pad(8);
    buildRightControls(rightPane);

    root.add(leftScroll).width(360).growY().top().left();
    root.add().grow(); // spacer
    root.add(rightPane).width(260).growY().top().right();
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

    // FPS label (live)
    fpsLabel = new Label("FPS: --", skin);
    tabsBar.add(fpsLabel).padRight(12).right();

    // Save / Back
    TextButton save = new TextButton("Save (F5)", skin);
    TextButton back = new TextButton("Back", skin);
    tabsBar.add(save).right().padRight(6);
    tabsBar.add(back).right();

    save.addListener(SkinFactory.onClick(this::saveMap));
    back.addListener(SkinFactory.onClick(() -> app.goToMenu()));
  }

  private void registerLayersAndShow() {
    layers.add(new TerrainLayer(terrain, skin));
    layers.add(new com.example.game.World.Editor.Layers.WalkLayer(terrain, skin));
    buildTabsBar();
    rebuildLeftPane();
  }

  private void rebuildLeftPane() {
    leftPane.clear();
    layers.buildUI(leftPane, skin);
  }

  /** Right-side panel: Hour slider + Sun Size + Warmth + God Rays Intensity (all horizontal). */
  private void buildRightControls(Table parent) {
    parent.clear();

    Label title = new Label("Sky / Time", skin, "title");
    parent.add(title).left().row();

    // --- Hour slider ---
    hourLabel = new Label(formatHour(hour), skin);
    parent.add(hourLabel).left().padBottom(6).row();

    hourSlider = new Slider(0f, 24f, 0.05f, false, skin);
    hourSlider.setAnimateDuration(0f);
    hourSlider.setValue(hour);
    hourSlider.addListener(new ChangeListener() {
      @Override public void changed(ChangeEvent event, Actor actor) {
        hour = hourSlider.getValue();
        updateSunFromHour();
        shader.setSunDirection(sunDirWorld.x, sunDirWorld.y, sunDirWorld.z);
        hourLabel.setText(formatHour(hour));
      }
    });
    parent.add(hourSlider).width(240).height(32).padBottom(8).row();

    // --- Sun Size slider ---
    final Label sizeLbl = new Label("Sun Size: 0.90°", skin);
    parent.add(sizeLbl).left().padTop(8).row();

    final Slider sizeSlider = new Slider(0.3f, 3.0f, 0.01f, false, skin);
    sizeSlider.setAnimateDuration(0f);
    sizeSlider.setValue(0.90f);
    sizeSlider.addListener(new ChangeListener() {
      @Override public void changed(ChangeEvent event, Actor actor) {
        float v = sizeSlider.getValue();
        sizeLbl.setText(String.format("Sun Size: %.2f°", v));
        sky.setSunSizeDeg(v);
      }
    });
    parent.add(sizeSlider).width(240).height(32).row();

    // --- Warmth slider ---
    final Label warmLbl = new Label("Warmth: 0.35", skin);
    parent.add(warmLbl).left().padTop(8).row();

    final Slider warmSlider = new Slider(0f, 1f, 0.01f, false, skin);
    warmSlider.setAnimateDuration(0f);
    warmSlider.setValue(0.35f);
    warmSlider.addListener(new ChangeListener() {
      @Override public void changed(ChangeEvent event, Actor actor) {
        float v = warmSlider.getValue();
        warmLbl.setText(String.format("Warmth: %.2f", v));
        sky.setWarmth(v);
      }
    });
    parent.add(warmSlider).width(240).height(32).row();

    // --- God Rays Intensity (exposure) ---
    raysLabel = new Label(String.format("God Rays: %.2f", godrays.exposure), skin);
    parent.add(raysLabel).left().padTop(8).row();

    raysSlider = new Slider(0f, 1.5f, 0.01f, false, skin);
    raysSlider.setAnimateDuration(0f);
    raysSlider.setValue(godrays.exposure); // assumes your GodRaysRenderer has a public 'exposure' field
    raysSlider.addListener(new ChangeListener() {
      @Override public void changed(ChangeEvent event, Actor actor) {
        float v = raysSlider.getValue();
        godrays.exposure = v;                 // drive post effect intensity
        raysLabel.setText(String.format("God Rays: %.2f", v));
      }
    });
    parent.add(raysSlider).width(240).height(32).row();

    // --- View Distance slider ---
    distanceLabel = new Label("Render Distance: 3.00", skin);
    parent.add(distanceLabel).left().padTop(8).row();

    distanceSlider = new Slider(0f, 100f, 1f, false, skin);
    distanceSlider.setAnimateDuration(0f);
    distanceSlider.setValue(3f);
    distanceSlider.addListener(new ChangeListener() {
      @Override public void changed(ChangeEvent event, Actor actor) {
        float v = distanceSlider.getValue();
        terrain.setViewRadius((int)v);
        distanceLabel.setText(String.format("Distance: %.2f", v));
      }
    });
    parent.add(distanceSlider).width(240).height(32).row();
  }

  private static String formatHour(float h) {
    int hh = ((int)h) % 24;
    int mm = (int)((h - (int)h) * 60f + 0.5f);
    if (mm >= 60) { mm -= 60; hh = (hh + 1) % 24; }
    return String.format("Hour: %02d:%02d", hh, mm);
  }

  /** Map hour (0..24) to a full-day sun orbit: azimuth spins 0..2π and elevation follows a sine arc. */
  private void updateSunFromHour() {
    float t = (hour % 24f) / 24f;
    float az = t * MathUtils.PI2;
    float maxElevDeg = 75f;
    float elev = MathUtils.degreesToRadians * maxElevDeg * MathUtils.sin(az);
    float sinE = MathUtils.sin(elev);
    float cosE = MathUtils.cos(elev);
    float sx = MathUtils.sin(az) * cosE;
    float sy = sinE;
    float sz = -MathUtils.cos(az) * cosE;
    sunDirWorld.set(sx, sy, sz).nor();
  }

  private void saveMap() {
    terrain.buildManifest();
    terrain.encodeChunksToHmx();
    terrain.saveAll(mapPath);
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

  private static float smoothstep(float edge0, float edge1, float x) {
    float t = MathUtils.clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
    return t * t * (3f - 2f * t);
  }


  // --------------------------------
  // RENDER LOOP (with god rays)
  // --------------------------------
  @Override public void render(float dt) {
    elapsedSeconds += dt;
    edCam.update(cam);

    var f = edCam.focus();
    terrain.updateAndStream(f.x, f.z);

    layers.update(dt);

    // Clear & 3D state
    Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    Gdx.gl.glClearColor(0.05f, 0.07f, 0.10f, 1f);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    Gdx.gl.glEnable(GL20.GL_CULL_FACE);
    Gdx.gl.glCullFace(GL20.GL_BACK);

    // 1) SKY
    sky.render(cam, sunDirWorld, elapsedSeconds);

    // 2) WORLD
    shader.bind();
    shader.setProjectionView(cam.combined);
    shader.setSunDirection(-sunDirWorld.x, -sunDirWorld.y, -sunDirWorld.z); // flip for terrain if needed
    shader.applyUniforms();
    terrain.render(shader, cam);
    // 3) GOD RAYS — fade out as the sun turns away
    float facing = cam.direction.dot(sunDirWorld);           // [-1..1]
    float fade   = smoothstep(0.0f, 0.18f, Math.max(0f, facing)); // 0→1 with a soft knee
    if (fade > 0.001f) {
      // keep your existing light UV calc
      godrays.updateLightScreenPos(cam, sunDirWorld);

      godrays.beginOcclusion();
        // sky bright
        sky.render(cam, sunDirWorld, elapsedSeconds);
        // occluders black
        renderTerrainSolidBlack();
      godrays.endOcclusion(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

      // scale intensity this frame (restore after)
      float prev = godrays.exposure;                // slider-driven base
      godrays.exposure = prev * fade;               // fade with facing
      godrays.compositeAdd(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
      godrays.exposure = prev;                      // restore
    }

    // 5) editor overlays
    layers.render3D(cam);

    // UI
    ui.act(dt);
    Gdx.gl.glDisable(GL20.GL_CULL_FACE);
    Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    ui.draw();

    // Update FPS label
    if (fpsLabel != null) fpsLabel.setText("FPS: " + Gdx.graphics.getFramesPerSecond());

    if (Gdx.input.isKeyJustPressed(Input.Keys.F5)) saveMap();
    if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
      Dialog dialog = new Dialog("Are you sure?", skin) {
        @Override protected void result(Object object) {
          if (Boolean.TRUE.equals(object)) app.goToMenu();
        }
      };
      dialog.text("Exit to main menu?");
      dialog.button("Exit", true);
      dialog.button("Cancel", false);
      dialog.show(ui);
    }
  }

  /** Renders terrain using the SimpleSolidShader (black) into the currently bound target. */
  private void renderTerrainSolidBlack() {
    SimpleShader proxy = new SimpleShader() {
      @Override public void bind() { solid.bind(cam.combined); }
      @Override public ShaderProgram getProgram() { return solid.getProgram(); }
      @Override public void applyUniforms() { /* none */ }
    };
    terrain.render(proxy, cam);
  }

  @Override public void resize(int w, int h) {
    cam.viewportWidth = w; cam.viewportHeight = h; cam.update();
    ui.getViewport().update(w, h, true);
    godrays.resize(w, h); // rebuild its half-res FBO
  }

  @Override public void pause() {}
  @Override public void resume() {}
  @Override public void hide() {}
  @Override public void dispose() {
    if (shader != null) shader.dispose();
    if (terrain != null) terrain.dispose();
    if (ui != null) ui.dispose();
    if (skin != null) skin.dispose();
    if (sky != null) sky.dispose();
    if (godrays != null) godrays.dispose();
    if (solid != null) solid.dispose();
  }
}
