package com.example.game.Screens;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;

import com.example.game.App.GameApp;
import com.example.game.Render.Pipeline.*;
import com.example.game.Render.Passes.*;
import com.example.game.UI.Editor.EditorHUD;
import com.example.game.World.MapResource;
import com.example.game.World.NewMapCreator;
import com.example.game.World.TerrainSystem;

// editor framework
import com.example.game.World.Editor.EditorCamera;
import com.example.game.World.Editor.EditorContext;
import com.example.game.World.Editor.EditorInputRouter;
import com.example.game.World.Editor.LayerManager;
import com.example.game.World.Editor.Layers.TerrainLayer;

public class EditorScreen implements Screen {
  private final GameApp app;
  private final String mapPath;
  private final com.example.game.Config.Settings settings;

  // camera + time
  private PerspectiveCamera cam;
  private final EditorCamera edCam;
  private final Vector3 sunDirWorld = new Vector3(-0.4f, -1f, -0.3f).nor();
  private float hour = 12f, elapsedSeconds = 0f;

  // world
  private TerrainSystem terrain;
  private MapResource map;

  // editor infra
  private final LayerManager layers = new LayerManager();

  // UI
  private EditorHUD hud;

  // pipeline
  private RenderContext ctx;
  private RenderPipeline pipeline;
  private SkyPass skyPass;
  private TerrainPass terrainPass;
  private GodRaysPass raysPass;
  private BirdsPass birdsPass;

  public EditorScreen(GameApp app, String mapPath, com.example.game.Config.Settings settings) {
    this.app = app; this.mapPath = mapPath; this.settings = settings;
    this.edCam = new EditorCamera(settings);
  }

  @Override public void show() {
    cam = new PerspectiveCamera(70f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    cam.near = 0.05f; cam.far = 4000f;
    EditorContext.setCamera(cam);

    updateSunFromHour();

    ensureMapExists(mapPath);
    map = MapResource.loadFromFile(mapPath);
    terrain = new TerrainSystem(map);

    // passes
    skyPass = new SkyPass();         skyPass.init();
    terrainPass = new TerrainPass(terrain); terrainPass.init();
    raysPass = new GodRaysPass(skyPass, terrainPass); raysPass.init(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    
    Texture birdTex = new Texture(Gdx.files.internal("textures/bird_silhouette.png"), true); // mipmaps
    birdTex.setFilter(TextureFilter.MipMapLinearLinear, TextureFilter.Linear);
    birdTex.setWrap(TextureWrap.ClampToEdge, TextureWrap.ClampToEdge);
    birdsPass = new BirdsPass(30);
    birdsPass.init();
    birdsPass.setRadius(600f);
    birdsPass.setPixelSizeRange(2.0f, 9.0f);
    birdsPass.setGlobalSpeed(0.8f);
    birdsPass.setTexture(birdTex, true);
    birdsPass.setTint(0.9f, 0.9f, 0.9f); // optional: brighten if yo
    birdsPass.setCenterRepel(0.8f);   // pushes projected points outward (0..1)
    birdsPass.setHorizonBias(1.0f);
    ctx = new RenderContext(cam, sunDirWorld);
    pipeline = new RenderPipeline()
        .add(skyPass)
        .add(birdsPass)
        .add(terrainPass)
        .add(raysPass);

    // HUD
    hud = new EditorHUD();
    buildLayersAndHUD();

    // input
    Gdx.input.setInputProcessor(new InputMultiplexer(
      hud.stage(),
      new EditorInputRouter(layers, new EditorInputRouter.CameraCallbacks() {
        @Override public void orbit(float dx, float dy) { edCam.orbit(dx, dy); }
        @Override public void pan(float dx, float dy)   { edCam.pan(cam, dx, dy); }
        @Override public void zoom(float amountY)       { edCam.zoom(amountY); }
      })
    ));
  }

  private void buildLayersAndHUD() {
    layers.add(new TerrainLayer(terrain, hud.skin()));
    layers.add(new com.example.game.World.Editor.Layers.WalkLayer(terrain, hud.skin()));
    hud.build(layers, new EditorHUD.Callbacks() {
      @Override public void onBack() { app.goToMenu(); }
      @Override public void onSave() { saveMap(); }
      @Override public void onHourChanged(float h) { hour = h; updateSunFromHour(); }
      @Override public void onSunSizeChanged(float deg) { skyPass.setSunSizeDeg(deg); }
      @Override public void onWarmthChanged(float w) { skyPass.setWarmth(w); }
      @Override public void onGodRaysChanged(float e) { raysPass.setExposure(e); }
      @Override public void onViewDistanceChanged(int r) { terrainPass.setViewRadius(r); }
    }, hour, raysPass.exposure, 3);
  }

  @Override public void render(float dt) {
    elapsedSeconds += dt;
    ctx.elapsedSeconds = elapsedSeconds;

    edCam.update(cam);
    var f = edCam.focus();
    terrain.updateAndStream(f.x, f.z);

    layers.update(dt);

    // clear + state
    Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    Gdx.gl.glClearColor(0.05f, 0.07f, 0.10f, 1f);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    Gdx.gl.glEnable(GL20.GL_CULL_FACE);
    Gdx.gl.glCullFace(GL20.GL_BACK);

    // render passes
    pipeline.update(dt, ctx);
    pipeline.render(ctx);

    // overlays
    layers.render3D(cam);

    // UI
    Gdx.gl.glDisable(GL20.GL_CULL_FACE);
    Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    hud.setFps(Gdx.graphics.getFramesPerSecond());
    hud.actAndDraw(dt);

    // quick keys
    if (Gdx.input.isKeyJustPressed(Input.Keys.F5)) saveMap();
    if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
      Dialog dialog = new Dialog("Are you sure?", hud.skin()) {
        @Override protected void result(Object object) {
          if (Boolean.TRUE.equals(object)) app.goToMenu();
        }
      };
      dialog.text("Exit to main menu?");
      dialog.button("Exit", true);
      dialog.button("Cancel", false);
      dialog.show(hud.stage());
    }
  }

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

  private void ensureMapExists(String path) {
    java.nio.file.Path p = java.nio.file.Paths.get(path);
    if (!java.nio.file.Files.exists(p)) {
      try { NewMapCreator.createBlankMap(path, 64, 1f, 0.5f, 0, 0, 1, 1); }
      catch (Exception e) { throw new RuntimeException("Failed to create blank map at " + path, e); }
    }
  }

  private void saveMap() {
    terrain.buildManifest();
    terrain.encodeChunksToHmx();
    terrain.saveAll(mapPath);
    Gdx.app.log("Editor", "Saved map: " + mapPath);
    map.reload(mapPath);
  }

  @Override public void resize(int w, int h) {
    cam.viewportWidth = w; cam.viewportHeight = h; cam.update();
    hud.stage().getViewport().update(w, h, true);
    pipeline.resize(w, h);
  }

  @Override public void pause() {}
  @Override public void resume() {}
  @Override public void hide() {}

  @Override public void dispose() {
    if (pipeline != null) pipeline.close();
    if (hud != null) hud.dispose();
    if (terrain != null) terrain.dispose();
  }
}
