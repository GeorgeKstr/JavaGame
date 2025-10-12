package com.example.game.Screens;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.example.game.Config.SettingsStore;
import com.example.game.App.GameApp;
import com.example.game.Render.Pipeline.RenderContext;
import com.example.game.Render.Pipeline.RenderPipeline;
import com.example.game.Render.Passes.*;
import com.example.game.UI.Editor.EditorHUD;
import com.example.game.World.MapResource;
import com.example.game.World.NewMapCreator;
import com.example.game.World.TerrainSystem;
import com.example.game.World.Editor.*;
import com.example.game.World.Editor.Layers.TerrainLayer;
import com.example.game.World.Editor.Layers.WalkLayer;
import com.example.game.World.Editor.Layers.SplinesLayer;
import com.example.game.Render.Pipeline.RenderBootstrap;
import com.example.game.Config.Settings;
import com.example.game.Config.cvar.CVars;
import com.example.game.Config.cvar.PrefsCVarStore;

public class EditorScreen implements Screen {
  private final GameApp app;
  private final String mapPath;
  private final Settings settings;

  // core
  private PerspectiveCamera cam;
  private final EditorCamera edCam;
  private final LayerManager layers = new LayerManager();
  private final Vector3 sunDirWorld = new Vector3(-0.4f, -1f, -0.3f).nor();
  private float hour = 12f, elapsed = 0f;
  private EditorInputRouter inputRouter;

  // world
  private TerrainSystem terrain;
  private MapResource map;

  // UI
  private EditorHUD hud;
  private com.example.game.World.Editor.Layers.SplinesLayer splinesLayerRef;

  // rendering
  private RenderContext ctx;
  private RenderPipeline pipeline;

  public EditorScreen(GameApp app, String mapPath, Settings settings) {
    this.app = app; this.mapPath = mapPath; this.settings = settings;
    this.edCam = new EditorCamera(settings);
  }

  @Override public void show() {
    // Camera
    cam = new PerspectiveCamera(70f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    cam.near = 0.05f; cam.far = 8000f; cam.update();
    EditorContext.setCamera(cam);

    // World
    ensureMapExists(mapPath);
    map = MapResource.loadFromFile(mapPath);
    terrain = new TerrainSystem(map);
    // Apply initial render toggles
    terrain.setWireframe(Boolean.TRUE.equals(CVars.RENDER_TERRAIN_WIREFRAME.get()));
    terrain.setChunkGrid(Boolean.TRUE.equals(CVars.RENDER_TERRAIN_CHUNK_GRID.get()));

    // Initial values from CVars (kept in sync with Settings)
    hour = CVars.RENDER_TIME_OF_DAY.get();
    updateSunFromHour();

    // Render pipeline
    ctx = new RenderContext(cam, sunDirWorld);
    pipeline = RenderBootstrap.build(ctx, terrain, RenderBootstrap.Config.defaultConfig());

    // Apply persisted visual params before UI builds (from CVars)
    pipeline.get(SkyPass.class).setSunSizeDeg(CVars.RENDER_SUN_SIZE_DEG.get());
    pipeline.get(SkyPass.class).setWarmth(CVars.RENDER_SKY_WARMTH.get());
    pipeline.get(GodRaysPass.class).setExposure(CVars.RENDER_GODRAYS_EXP.get());
    pipeline.get(TerrainPass.class).setViewRadius(CVars.VIEW_RADIUS_CHUNKS.get());

    // Follow CVars automatically (cross-screen sync)
    CVars.RENDER_SUN_SIZE_DEG.onChanged(v -> {
      float val = v == null ? 1.5f : v;
      pipeline.get(SkyPass.class).setSunSizeDeg(val);
    });
    CVars.RENDER_SKY_WARMTH.onChanged(v -> {
      float val = v == null ? 0.5f : v;
      pipeline.get(SkyPass.class).setWarmth(val);
    });
    CVars.RENDER_GODRAYS_EXP.onChanged(v -> {
      float val = v == null ? 3f : v;
      pipeline.get(GodRaysPass.class).setExposure(val);
    });
    CVars.VIEW_RADIUS_CHUNKS.onChanged(v -> {
      int val = v == null ? 3 : v;
      pipeline.get(TerrainPass.class).setViewRadius(val);
    });
    CVars.RENDER_TIME_OF_DAY.onChanged(v -> {
      hour = v == null ? 12f : v;
      updateSunFromHour();
    });
    CVars.RENDER_TERRAIN_WIREFRAME.onChanged(v -> terrain.setWireframe(Boolean.TRUE.equals(v)));
    CVars.RENDER_TERRAIN_CHUNK_GRID.onChanged(v -> terrain.setChunkGrid(Boolean.TRUE.equals(v)));

    // HUD
    hud = new EditorHUD();
    layers.add(new TerrainLayer(terrain, hud.skin()));
    layers.add(new WalkLayer(terrain, hud.skin()));
    splinesLayerRef = new SplinesLayer(terrain, hud.skin());
    layers.add(splinesLayerRef);

    hud.build(layers, new EditorHUD.Callbacks() {
      @Override public void onBack() { app.goToMenu(); }

      @Override public void onSave() { saveMap(); }

      @Override public void onHourChanged(float h) { CVars.RENDER_TIME_OF_DAY.set(h); }

      @Override public void onSunSizeChanged(float deg) { CVars.RENDER_SUN_SIZE_DEG.set(deg); }

      @Override public void onWarmthChanged(float w) { CVars.RENDER_SKY_WARMTH.set(w); }

      @Override public void onGodRaysChanged(float e) { CVars.RENDER_GODRAYS_EXP.set(e); }

      @Override public void onViewDistanceChanged(int r) { CVars.VIEW_RADIUS_CHUNKS.set(r); }

      @Override public void onRefresh() {
        // Reload chunks to reapply live baking and current edits
        terrain.reloadAllChunks();
        // Ensure ribbons reflect current terrain
        if (splinesLayerRef != null) {
          for (var s : splinesLayerRef.getSplines()) {
            s.markRibbonDirty();
            s.rebuildRibbonAsync(terrain.getBaseHeightSampler(), 2);
          }
        }
      }
    },
    // Initial values for HUD sliders/knobs from CVars
    /* initialHour */ CVars.RENDER_TIME_OF_DAY.get(),
    /* initialWarmth */ CVars.RENDER_SKY_WARMTH.get(),
    /* initialSunSize */ CVars.RENDER_SUN_SIZE_DEG.get(),
    /* initialGodRays */ CVars.RENDER_GODRAYS_EXP.get(),
    /* initialViewRadius */ CVars.VIEW_RADIUS_CHUNKS.get()
    );

    // Input
    inputRouter = EditorBootstrap.inputRouter(layers, edCam, cam, terrain);
    // Route input based on focus/hover: Stage first when over/focused; otherwise camera/tools first
    com.example.game.UI.Editor.StageAwareInputRouter router = new com.example.game.UI.Editor.StageAwareInputRouter(hud.stage(), inputRouter);
    Gdx.input.setInputProcessor(router);
  }


  @Override public void render(float dt) {
    elapsed += dt;
    ctx.elapsedSeconds = elapsed;

    // Update sim
    edCam.update(cam);
    var f = edCam.focus();
    terrain.updateAndStream(f.x, f.z);
    layers.update(dt, hud.skin());
    layers.render3D(cam);

    // Clear
    Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    Gdx.gl.glClearColor(0.05f, 0.07f, 0.10f, 1f);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    Gdx.gl.glEnable(GL20.GL_CULL_FACE);
    Gdx.gl.glCullFace(GL20.GL_BACK);

    // Render
    pipeline.update(dt, ctx);
    pipeline.render(ctx);

    // Gizmos/overlays
    layers.render3D(cam);

    // UI
    Gdx.gl.glDisable(GL20.GL_CULL_FACE);
    Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    hud.setFps(Gdx.graphics.getFramesPerSecond());
    hud.actAndDraw(dt);

    inputRouter.update(dt); // for WASD movement
    if (Gdx.input.isKeyJustPressed(Input.Keys.F5)) saveMap();
    if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
      Dialog dlg = new Dialog("Are you sure?", hud.skin()) {
        @Override protected void result(Object obj) { if (Boolean.TRUE.equals(obj)) app.goToMenu(); }
      };
      dlg.text("Exit to main menu?"); dlg.button("Exit", true); dlg.button("Cancel", false); dlg.show(hud.stage());
    }
  }

  @Override public void resize(int w, int h) {
    cam.viewportWidth = w; cam.viewportHeight = h; cam.update();
    hud.stage().getViewport().update(w, h, true);
    if (pipeline != null) pipeline.resize(w, h);
  }

  @Override public void pause() {}
  @Override public void resume() {}
  @Override public void hide() {}
  @Override public void dispose() {
    // Persist latest CVars and legacy settings facade
    PrefsCVarStore.saveAll(CVars.reg);
    SettingsStore.save(settings);
    if (pipeline != null) pipeline.close();
    if (hud != null) hud.dispose();
    if (terrain != null) terrain.dispose();
  }

  private void updateSunFromHour() {
    // hour: 0..24  ->  azimuth: 0..2π
    float t  = (hour % 24f) / 24f;
    float az = t * MathUtils.PI2; // 0: north (midnight), π/2: east (06:00), π: south (12:00), 3π/2: west (18:00)

    // Max elevation at local noon; 0 at sunrise/sunset; negative at night.
    // Using a simple equinox-like arc (no latitude/season yet):
    float maxElevDeg = 75f; // tweak to taste, e.g., 60–85
    float maxElevRad = MathUtils.degreesToRadians * maxElevDeg;

    // Correct day arc: peak at az=π (noon), zero at π/2 & 3π/2 (rise/set), min at 0 (midnight).
    float elev = maxElevRad * MathUtils.sin(az - MathUtils.PI / 2f);

    float sinE = MathUtils.sin(elev), cosE = MathUtils.cos(elev);

    // World orientation (unchanged): az=0 -> north (-Z), π/2 -> east (+X), π -> south (+Z), 3π/2 -> west (-X)
    sunDirWorld.set(MathUtils.sin(az) * cosE,  // X
                    sinE,                      // Y
                  -MathUtils.cos(az) * cosE   // Z
                  ).nor();
  }

  private void ensureMapExists(String path) {
    java.nio.file.Path p = java.nio.file.Paths.get(path);
    if (!java.nio.file.Files.exists(p)) {
      try { NewMapCreator.createBlankMap(path, 64, 2f, 0.5f, 0, 0, 1, 1); }
      catch (Exception e) { throw new RuntimeException("Failed to create blank map at " + path, e); }
    }
  }
  private void saveMap() {
    // Stage latest splines into the map before saving chunks/textures
    if (splinesLayerRef != null) splinesLayerRef.saveSplinesToMap();
    terrain.buildManifestMerged();
    terrain.encodeChunksToHmx();
    terrain.saveAll(mapPath);
    Gdx.app.log("Editor", "Saved map: " + mapPath);
    map.reload(mapPath);
  }
}
