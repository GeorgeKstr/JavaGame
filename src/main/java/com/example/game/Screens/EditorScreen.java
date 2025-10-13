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

  // --- Height minimap / non-loaded chunk preview ---
  // For low-LOD terrain preview: per-chunk corner heights (meters): [y00(x0,z0), y10(x1,z0), y01(x0,z1), y11(x1,z1)]
  private final java.util.concurrent.ConcurrentHashMap<Integer, float[]> chunkCornerHeights = new java.util.concurrent.ConcurrentHashMap<>();
  // Per-chunk average albedo color derived from dominant texture (r,g,b in 0..1)
  private final java.util.concurrent.ConcurrentHashMap<Integer, float[]> chunkAlbedo = new java.util.concurrent.ConcurrentHashMap<>();
  // Per-texture average color cache (id -> rgb)
  private final java.util.concurrent.ConcurrentHashMap<Integer, float[]> textureAvgCache = new java.util.concurrent.ConcurrentHashMap<>();
  private final java.util.concurrent.ExecutorService avgExec = java.util.concurrent.Executors.newSingleThreadExecutor(r -> { Thread t=new Thread(r, "ChunkAvgWorker"); t.setDaemon(true); return t; });
  private final com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20 imAvg = new com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20(262144, false, true, 0);

  // UI toggles
  private boolean showOuterLod = true;
  private boolean showMinimap = false; // keep functionality, but do not use by default

  // Shader-based LOD preview
  private com.badlogic.gdx.graphics.Mesh lodMesh;
  private com.badlogic.gdx.graphics.glutils.ShaderProgram lodShader;
  // No caching of camera cell; rebuild per frame to avoid lag/gaps with streamed chunks

  public EditorScreen(GameApp app, String mapPath, Settings settings) {
    this.app = app; this.mapPath = mapPath; this.settings = settings;
    this.edCam = new EditorCamera(settings);
  }

  /** Compute an approximate average RGB color [0..1] for a ground texture id, sampled from the map resource. */
  private float[] computeTextureAverageColor(Integer texId) {
    try {
      if (texId == null || map == null) return new float[]{0.65f,0.67f,0.70f};
      MapResource.Manifest man = map.getManifest();
      MapResource.TextureRef tr = null;
      for (MapResource.TextureRef t : man.groundTextures) { if (t.id == texId) { tr = t; break; } }
      if (tr == null || tr.file == null) return new float[]{0.65f,0.67f,0.70f};
      byte[] data = map.getTextureBytes(tr.file);
      if (data == null || data.length == 0) return new float[]{0.65f,0.67f,0.70f};
      Pixmap pm = new Pixmap(data, 0, data.length);
      try {
        int w = pm.getWidth(), h = pm.getHeight();
        if (w <= 0 || h <= 0) return new float[]{0.65f,0.67f,0.70f};
        int stepX = Math.max(1, w / 64);
        int stepY = Math.max(1, h / 64);
        float sr=0, sg=0, sb=0; int cnt=0;
        com.badlogic.gdx.graphics.Color c = new com.badlogic.gdx.graphics.Color();
        for (int y = 0; y < h; y += stepY) {
          for (int x = 0; x < w; x += stepX) {
            int rgba = pm.getPixel(x, y);
            com.badlogic.gdx.graphics.Color.rgba8888ToColor(c, rgba);
            sr += c.r; sg += c.g; sb += c.b; cnt++;
          }
        }
        if (cnt == 0) return new float[]{0.65f,0.67f,0.70f};
        return new float[]{ sr/cnt, sg/cnt, sb/cnt };
      } finally { pm.dispose(); }
    } catch (Throwable ignored) {
      return new float[]{0.65f,0.67f,0.70f};
    }
  }

  private void drawNonLoadedChunkPreviews() {
    if (map == null || terrain == null) return;
    var mf = map.getManifest();
    int S = mf.samplesPerSide; float tile = mf.tile; int cpc = S - 1; float chunkSize = cpc * tile;
    // Choose radius similar to last computed; use current view radius + 4 for safety
    int viewR = CVars.VIEW_RADIUS_CHUNKS.get();
    int drawR = Math.max(viewR + 4, viewR * 2);
    Vector3 f = edCam.focus();
    int camCx = (int)Math.floor(f.x / chunkSize);
    int camCz = (int)Math.floor(f.z / chunkSize);

    // Use shader+mesh path if available; fallback to immediate-mode otherwise
    if (ensureLodShader()) {
      rebuildLodMesh(camCx, camCz, drawR, chunkSize);
      if (lodMesh != null) {
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glCullFace(GL20.GL_BACK);
        // Use CW as front for this mesh so the top face is visible given our index order and coordinate setup
        Gdx.gl.glFrontFace(GL20.GL_CW);
        // Push LOD slightly away to avoid z-fighting with near geometry
        Gdx.gl.glEnable(GL20.GL_POLYGON_OFFSET_FILL);
        Gdx.gl.glPolygonOffset(1f, 1f);
        lodShader.bind();
        lodShader.setUniformMatrix("u_projView", cam.combined);
        lodShader.setUniformf("u_sunDir", sunDirWorld);
        lodShader.setUniformf("u_gridThickness", 0.035f);
        lodShader.setUniformf("u_gridColor", 0f, 0f, 0f, 0.85f);
        lodMesh.render(lodShader, GL20.GL_TRIANGLES);
        Gdx.gl.glDisable(GL20.GL_POLYGON_OFFSET_FILL);
        // Restore default CCW
        Gdx.gl.glFrontFace(GL20.GL_CCW);
      }
    } else {
      // Fallback (should rarely be used now). Left blank or keep prior IM path if needed.
    }
  }

  private boolean ensureLodShader() {
    if (lodShader != null) return true;
    String vert = Gdx.files.classpath("shaders/outerlod.vert").exists() ? "shaders/outerlod.vert" : "shaders/outerlod.vert";
    String frag = Gdx.files.classpath("shaders/outerlod.frag").exists() ? "shaders/outerlod.frag" : "shaders/outerlod.frag";
    String vs = Gdx.files.internal(vert).readString();
    String fs = Gdx.files.internal(frag).readString();
    com.badlogic.gdx.graphics.glutils.ShaderProgram.pedantic = false;
    lodShader = new com.badlogic.gdx.graphics.glutils.ShaderProgram(vs, fs);
    if (!lodShader.isCompiled()) {
      Gdx.app.error("LOD", "Shader compile error: " + lodShader.getLog());
      lodShader = null; return false;
    }
    return true;
  }

  private void rebuildLodMesh(int camCx, int camCz, int drawR, float chunkSize) {
    // Count chunks to render
    int count = 0;
    for (int cz = camCz - drawR; cz <= camCz + drawR; cz++) {
      for (int cx = camCx - drawR; cx <= camCx + drawR; cx++) {
        int dx = Math.abs(cx - camCx), dz = Math.abs(cz - camCz);
        int md = Math.max(dx, dz);
        if (md > drawR) continue;
        if (terrain.isChunkLoaded(cx, cz)) continue;
        count++;
      }
    }
    if (count <= 0) { if (lodMesh != null) { lodMesh.dispose(); lodMesh = null; } return; }

    int vertsPer = 4, idxPer = 6;
    int totalVerts = count * vertsPer;
    int totalIdx = count * idxPer;
    float[] verts = new float[totalVerts * (3 + 3 + 4 + 2)]; // pos3, normal3, color4, uv2
    short[] idx = new short[totalIdx];

    int vi = 0; int ii = 0; short base = 0;
    final float inset = 0.1f; // shrink quads slightly to avoid touching loaded chunk borders
    for (int cz = camCz - drawR; cz <= camCz + drawR; cz++) {
      for (int cx = camCx - drawR; cx <= camCx + drawR; cx++) {
        int dx = Math.abs(cx - camCx), dz = Math.abs(cz - camCz);
        int md = Math.max(dx, dz);
        if (md > drawR) continue;
        if (terrain.isChunkLoaded(cx, cz)) continue;
        int key = (cx << 16) ^ (cz & 0xFFFF);
        float[] h = chunkCornerHeights.getOrDefault(key, new float[]{0,0,0,0});
        float[] baseCol = chunkAlbedo.get(key);
        if (baseCol == null) baseCol = textureAvgCache.computeIfAbsent(map.getManifest().defaultTexture, this::computeTextureAverageColor);
        float br = baseCol != null ? baseCol[0] : 0.65f;
        float bg = baseCol != null ? baseCol[1] : 0.67f;
        float bb = baseCol != null ? baseCol[2] : 0.70f;

        float x0 = cx * chunkSize + inset, z0 = cz * chunkSize + inset;
        float x1 = (cx + 1) * chunkSize - inset, z1 = (cz + 1) * chunkSize - inset;
        float y00 = h[0], y10 = h[1], y01 = h[2], y11 = h[3];
        // Upward normal for simple Lambert; keep consistent top lighting
        float nx = 0f, ny = 1f, nz = 0f;

        // v0: (x0,y00,z0) uv(0,0)
        verts[vi++] = x0; verts[vi++] = y00; verts[vi++] = z0; // pos
        verts[vi++] = nx; verts[vi++] = ny; verts[vi++] = nz;  // normal
        verts[vi++] = br; verts[vi++] = bg; verts[vi++] = bb; verts[vi++] = 1f;  // color
        verts[vi++] = 0f; verts[vi++] = 0f;                    // uv
        // v1: (x1,y10,z0) uv(1,0)
        verts[vi++] = x1; verts[vi++] = y10; verts[vi++] = z0;
        verts[vi++] = nx; verts[vi++] = ny; verts[vi++] = nz;
        verts[vi++] = br; verts[vi++] = bg; verts[vi++] = bb; verts[vi++] = 1f;
        verts[vi++] = 1f; verts[vi++] = 0f;
        // v2: (x0,y01,z1) uv(0,1)
        verts[vi++] = x0; verts[vi++] = y01; verts[vi++] = z1;
        verts[vi++] = nx; verts[vi++] = ny; verts[vi++] = nz;
        verts[vi++] = br; verts[vi++] = bg; verts[vi++] = bb; verts[vi++] = 1f;
        verts[vi++] = 0f; verts[vi++] = 1f;
        // v3: (x1,y11,z1) uv(1,1)
        verts[vi++] = x1; verts[vi++] = y11; verts[vi++] = z1;
        verts[vi++] = nx; verts[vi++] = ny; verts[vi++] = nz;
        verts[vi++] = br; verts[vi++] = bg; verts[vi++] = bb; verts[vi++] = 1f;
        verts[vi++] = 1f; verts[vi++] = 1f;

        // Indices (CCW): (v0,v1,v3), (v0,v3,v2)
        idx[ii++] = (short)(base + 0);
        idx[ii++] = (short)(base + 1);
        idx[ii++] = (short)(base + 3);
        idx[ii++] = (short)(base + 0);
        idx[ii++] = (short)(base + 3);
        idx[ii++] = (short)(base + 2);
        base += 4;
      }
    }

    if (lodMesh != null) lodMesh.dispose();
    lodMesh = new com.badlogic.gdx.graphics.Mesh(true, totalVerts, totalIdx,
      new com.badlogic.gdx.graphics.VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.Position, 3, "a_position"),
      new com.badlogic.gdx.graphics.VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal, 3, "a_normal"),
      new com.badlogic.gdx.graphics.VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.ColorUnpacked, 4, "a_color"),
      new com.badlogic.gdx.graphics.VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.TextureCoordinates, 2, "a_texCoord0")
    );
    lodMesh.setVertices(verts);
    lodMesh.setIndices(idx);
  }

  private void drawHeightMinimap2D() {
    if (map == null) return;
    var mf = map.getManifest();
    int S = mf.samplesPerSide; float tile = mf.tile; int cpc = S - 1; float chunkSize = cpc * tile;
    Vector3 f = edCam.focus();
    int camCx = (int)Math.floor(f.x / chunkSize);
    int camCz = (int)Math.floor(f.z / chunkSize);
    int viewR = CVars.VIEW_RADIUS_CHUNKS.get();
    int drawR = Math.max(viewR + 4, viewR * 2);

    int w = Gdx.graphics.getWidth();
    int h = Gdx.graphics.getHeight();
    int size = 180; // minimap size (pixels)
    int grid = Math.max(1, Math.min(drawR * 2 + 1, size / 2)); // cap cells to fit in minimap area
    int cell = Math.max(1, size / grid);
    int mapW = cell * grid;
    int mapH = mapW;
    int ox = w - mapW - 12;
    int oy = h - mapH - 12;

    com.badlogic.gdx.math.Matrix4 ortho = new com.badlogic.gdx.math.Matrix4().setToOrtho2D(0,0,w,h);
    Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    // Find min/max heights in window for coloring
    float minH = Float.POSITIVE_INFINITY, maxH = Float.NEGATIVE_INFINITY;
    for (int dz = -drawR; dz <= drawR; dz++) {
      for (int dx = -drawR; dx <= drawR; dx++) {
        int cx = camCx + dx, cz = camCz + dz;
        int key = (cx << 16) ^ (cz & 0xFFFF);
        float[] h4 = chunkCornerHeights.get(key);
        if (h4 == null || h4.length < 4) h4 = new float[]{0,0,0,0};
        for (float v : h4) { if (v < minH) minH = v; if (v > maxH) maxH = v; }
      }
    }
    if (minH == Float.POSITIVE_INFINITY) { minH = 0f; maxH = 1f; }

    // Cells: iterate minimap cells and map each to a world chunk using camera forward/right (no trig)
    imAvg.begin(ortho, GL20.GL_TRIANGLES);
    // Forward (XZ) and Right (XZ) vectors
    float fx = cam.direction.x, fz = cam.direction.z;
    float fl = (float)Math.max(1e-6, Math.sqrt(fx*fx + fz*fz));
    fx /= fl; fz /= fl;                    // forward unit (world XZ)
    float rx = -fz; float rz =  fx;        // right = cross(forward, up) (flipped to fix mirroring)
    int grid2 = grid;
    for (int j = 0; j < grid2; j++) {
      for (int i = 0; i < grid2; i++) {
        // Centered minimap coordinates in chunk units
        float mx = (i + 0.5f) - grid2 * 0.5f; // right
        float my = (j + 0.5f) - grid2 * 0.5f; // up
        // Map minimap (right/up) to world offset (x,z): dx = mx*right + my*forward
        float dx = mx * rx + my * fx;
        float dz = mx * rz + my * fz;
        int cx = camCx + (int)Math.floor(dx);
        int cz = camCz + (int)Math.floor(dz);
        int key = (cx << 16) ^ (cz & 0xFFFF);
        float[] h4 = chunkCornerHeights.get(key);
        if (h4 == null || h4.length < 4) h4 = new float[]{0,0,0,0};
        float hAvg = 0.25f * (h4[0] + h4[1] + h4[2] + h4[3]);
        float t = (maxH > minH) ? MathUtils.clamp((hAvg - minH) / (maxH - minH), 0f, 1f) : 0.5f;
        // Hypsometric-like ramp: green -> tan -> light gray
        float r,g,b;
        if (t < 0.4f) {
          float u = t / 0.4f;
          float lowR=0.10f, lowG=0.35f, lowB=0.15f;
          float midR=0.25f, midG=0.50f, midB=0.20f;
          r = lowR + (midR - lowR) * u;
          g = lowG + (midG - lowG) * u;
          b = lowB + (midB - lowB) * u;
        } else if (t < 0.7f) {
          float u = (t - 0.4f) / 0.3f;
          float midR=0.25f, midG=0.50f, midB=0.20f;
          float tanR=0.60f, tanG=0.50f, tanB=0.30f;
          r = midR + (tanR - midR) * u;
          g = midG + (tanG - midG) * u;
          b = midB + (tanB - midB) * u;
        } else {
          float u = (t - 0.7f) / 0.3f;
          float tanR=0.60f, tanG=0.50f, tanB=0.30f;
          float hiR=0.90f,  hiG=0.90f,  hiB=0.90f;
          r = tanR + (hiR - tanR) * u;
          g = tanG + (hiG - tanG) * u;
          b = tanB + (hiB - tanB) * u;
        }
        int px = ox + i * cell;
        int py = oy + j * cell;
        float x0 = px, y0 = py, x1 = px + cell, y1 = py + cell;
        imAvg.color(r,g,b,1f); imAvg.vertex(x0, y0, 0);
        imAvg.color(r,g,b,1f); imAvg.vertex(x1, y0, 0);
        imAvg.color(r,g,b,1f); imAvg.vertex(x1, y1, 0);
        imAvg.color(r,g,b,1f); imAvg.vertex(x0, y0, 0);
        imAvg.color(r,g,b,1f); imAvg.vertex(x1, y1, 0);
        imAvg.color(r,g,b,1f); imAvg.vertex(x0, y1, 0);
      }
    }
    imAvg.end();

    // Border
    imAvg.begin(ortho, GL20.GL_LINES);
    float br=0, bg=0, bb=0, ba=1f;
    imAvg.color(br,bg,bb,ba); imAvg.vertex(ox-1, oy-1, 0);
    imAvg.color(br,bg,bb,ba); imAvg.vertex(ox+mapW+1, oy-1, 0);
    imAvg.color(br,bg,bb,ba); imAvg.vertex(ox+mapW+1, oy-1, 0);
    imAvg.color(br,bg,bb,ba); imAvg.vertex(ox+mapW+1, oy+mapH+1, 0);
    imAvg.color(br,bg,bb,ba); imAvg.vertex(ox+mapW+1, oy+mapH+1, 0);
    imAvg.color(br,bg,bb,ba); imAvg.vertex(ox-1, oy+mapH+1, 0);
    imAvg.color(br,bg,bb,ba); imAvg.vertex(ox-1, oy+mapH+1, 0);
    imAvg.color(br,bg,bb,ba); imAvg.vertex(ox-1, oy-1, 0);
    imAvg.end();
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
    // Reflect persisted outer LOD toggle to runtime flag
    CVars.RENDER_SHOW_OUTER_LOD.onChanged(v -> showOuterLod = Boolean.TRUE.equals(v));

    // HUD
    hud = new EditorHUD();
    layers.add(new TerrainLayer(terrain, hud.skin()));
    layers.add(new WalkLayer(terrain, hud.skin()));
    splinesLayerRef = new SplinesLayer(terrain, hud.skin());
    layers.add(splinesLayerRef);

    hud.build(layers, new EditorHUD.Callbacks() {
      @Override public void onBack() { app.goToMenu(); }

      @Override public void onSave() { saveMap(); performRefresh(); }

      @Override public void onHourChanged(float h) { CVars.RENDER_TIME_OF_DAY.set(h); }

      @Override public void onSunSizeChanged(float deg) { CVars.RENDER_SUN_SIZE_DEG.set(deg); }

      @Override public void onWarmthChanged(float w) { CVars.RENDER_SKY_WARMTH.set(w); }

      @Override public void onGodRaysChanged(float e) { CVars.RENDER_GODRAYS_EXP.set(e); }

      @Override public void onViewDistanceChanged(int r) { CVars.VIEW_RADIUS_CHUNKS.set(r); }

      @Override public void onRefresh() { performRefresh(); }

      @Override public void onOpenObjects() {
        java.util.List<com.example.game.World.Placeable> items = com.example.game.World.PlaceableRegistry.snapshot();
        com.example.game.UI.Editor.ObjectBrowser.show(hud.stage(), hud.skin(), items, p -> {
          if (p == null) return;
          com.badlogic.gdx.math.Vector3 v = p.getPosition();
          // Move the editor camera pivot to object position; keep current height for pivot Y? Use terrain height at XZ
          float y = terrain.heightAt(v.x, v.z, v.y);
          edCam.setFocus(v.x, y, v.z);
        });
      }

      @Override public void onToggleOuterLod(boolean enabled) {
        showOuterLod = enabled;
        CVars.RENDER_SHOW_OUTER_LOD.set(enabled);
      }
    },
    // Initial values for HUD sliders/knobs from CVars
    /* initialHour */ CVars.RENDER_TIME_OF_DAY.get(),
    /* initialWarmth */ CVars.RENDER_SKY_WARMTH.get(),
    /* initialSunSize */ CVars.RENDER_SUN_SIZE_DEG.get(),
    /* initialGodRays */ CVars.RENDER_GODRAYS_EXP.get(),
    /* initialViewRadius */ CVars.VIEW_RADIUS_CHUNKS.get()
    );

    // Initialize runtime flags from persisted CVars
    showOuterLod = Boolean.TRUE.equals(CVars.RENDER_SHOW_OUTER_LOD.get());

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

    // Draw low-LOD terrain for non-loaded chunks using per-corner heights
    if (showOuterLod) drawNonLoadedChunkPreviews();

    // Gizmos/overlays
    layers.render3D(cam);

    // UI
    Gdx.gl.glDisable(GL20.GL_CULL_FACE);
    Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    hud.setFps(Gdx.graphics.getFramesPerSecond());
    hud.actAndDraw(dt);

    // Minimap disabled by default; keep function available
    if (showMinimap) drawHeightMinimap2D();

    handleInput(dt);
  }

  private void handleInput(float dt) {
    if (Gdx.input.isKeyJustPressed(Input.Keys.F1)) {layers.setActive("terrain"); hud.rebuildLeftPane(layers);}
    if (Gdx.input.isKeyJustPressed(Input.Keys.F2)) {layers.setActive("walk"); hud.rebuildLeftPane(layers);}
    if (Gdx.input.isKeyJustPressed(Input.Keys.F3)) {layers.setActive("splines"); hud.rebuildLeftPane(layers);}

    boolean ctrl = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
    if (ctrl && Gdx.input.isKeyJustPressed(Input.Keys.S)) { saveMap(); performRefresh(); return;}
    // R: refresh
    if (Gdx.input.isKeyJustPressed(Input.Keys.R)) { performRefresh(); }
    if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
      Dialog dlg = new Dialog("Are you sure?", hud.skin()) {
        @Override protected void result(Object obj) { if (Boolean.TRUE.equals(obj)) app.goToMenu(); }
      };
      dlg.text("Exit to main menu?"); dlg.button("Exit", true); dlg.button("Cancel", false); dlg.show(hud.stage());
    }
    inputRouter.update(dt); // for WASD movement
  }

  private void performRefresh() {
    // Reload chunks to reapply live baking and current edits
    terrain.reloadAllChunks();
    // Ensure ribbons reflect current terrain
    if (splinesLayerRef != null) {
      for (var s : splinesLayerRef.getSplines()) {
        s.markRibbonDirty();
        s.rebuildRibbonAsync(terrain.getBaseHeightSampler(), 2);
      }
    }

    // Kick off background per-chunk corner height computation for a larger radius
    final var mfLocal = map.getManifest();
    final int Slocal = mfLocal.samplesPerSide;
    final float tileLocal = mfLocal.tile;
    final float yScaleLocal = mfLocal.yScale;
    final int cpcl = Slocal - 1;
    final float chunkSizeL = cpcl * tileLocal;
    final Vector3 focus = edCam.focus().cpy();
    final int camCxL = (int)Math.floor(focus.x / chunkSizeL);
    final int camCzL = (int)Math.floor(focus.z / chunkSizeL);
    final int viewRL = CVars.VIEW_RADIUS_CHUNKS.get();
    final int avgRL = Math.max(viewRL + 4, viewRL * 2); // compute at larger radius

    avgExec.submit(() -> {
      try {
        com.example.game.World.SparseChunkProvider provider = new com.example.game.World.SparseChunkProvider("", Slocal);
        for (int cz = camCzL - avgRL; cz <= camCzL + avgRL; cz++) {
          for (int cx = camCxL - avgRL; cx <= camCxL + avgRL; cx++) {
            int dx = Math.abs(cx - camCxL), dz = Math.abs(cz - camCzL);
            int md = Math.max(dx, dz);
            if (md > avgRL) continue;
            byte[] bytes = map.readChunkHmx(cx, cz);
            float y00, y10, y01, y11;
            int dominantTex = map.getManifest().defaultTexture;
            if (bytes == null) {
              y00 = y10 = y01 = y11 = 0f;
            } else {
              var lc = provider.loadFromBytes(bytes);
              short[] hh = lc.heights;
              int S2 = Slocal;
              int i00 = 0;
              int i10 = (S2 - 1);
              int i01 = (S2 - 1) * S2;
              int i11 = (S2 - 1) * S2 + (S2 - 1);
              y00 = hh[i00] * yScaleLocal;
              y10 = hh[i10] * yScaleLocal;
              y01 = hh[i01] * yScaleLocal;
              y11 = hh[i11] * yScaleLocal;

              java.util.HashMap<Integer, Long> acc = new java.util.HashMap<>();
              byte[] id0 = lc.id0, id1 = lc.id1, id2 = lc.id2;
              byte[] w0  = lc.w0,  w1  = lc.w1,  w2  = lc.w2;
              int n = hh.length;
              for (int i = 0; i < n; i++) {
                int t0 = id0[i] & 0xFF; int t1 = id1[i] & 0xFF; int t2 = id2[i] & 0xFF;
                int ww0 = w0[i] & 0xFF; int ww1 = w1[i] & 0xFF; int ww2 = w2[i] & 0xFF;
                if (ww0 > 0) acc.merge(t0, (long)ww0, Long::sum);
                if (ww1 > 0) acc.merge(t1, (long)ww1, Long::sum);
                if (ww2 > 0) acc.merge(t2, (long)ww2, Long::sum);
              }
              long bestW = -1; int bestId = dominantTex;
              for (var e : acc.entrySet()) { if (e.getValue() > bestW) { bestW = e.getValue(); bestId = e.getKey(); } }
              dominantTex = bestId;
            }
            int key = (cx << 16) ^ (cz & 0xFFFF);
            chunkCornerHeights.put(key, new float[]{ y00, y10, y01, y11 });
            float[] rgb = textureAvgCache.computeIfAbsent(dominantTex, EditorScreen.this::computeTextureAverageColor);
            if (rgb != null) chunkAlbedo.put(key, rgb);
          }
        }
      } catch (Throwable ignored) {}
    });
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
    if (lodMesh != null) { lodMesh.dispose(); lodMesh = null; }
    if (lodShader != null) { lodShader.dispose(); lodShader = null; }
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
