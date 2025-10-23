package com.example.game.World.Editor.Tools.Terrain;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.example.game.World.Editor.EditorContext;
import com.example.game.World.Editor.Tools.Tool;
import com.example.game.World.TerrainSystem;

import java.util.List;

/**
 * CaptureAreaTool: place a rectangle on y=0 and capture current mountain delta under it
 * into a heightmap image + a new Mountain node with same transform.
 */
public final class CaptureAreaTool implements Tool {
  private final TerrainSystem terrain;
  private final MountainTool mountainTool;
  private final ImmediateModeRenderer20 lines = new ImmediateModeRenderer20(2048, false, true, 0);

  private final Vector3 center = new Vector3();
  private float halfW = 50f, halfH = 50f, rotDeg = 0f;
  // No falloff control in Capture: captured stamp is normalized and imported with zero falloff
  private float handleMarginPct = 0.12f;

  private Label infoLabel;
  private Slider wSlider, hSlider, rotSlider;
  private TextField nameField;

  private enum DragMode { NONE, MOVE, RESIZE_L, RESIZE_R, RESIZE_T, RESIZE_B }
  private DragMode dragMode = DragMode.NONE;
  private float dragStartX, dragStartZ;
  // Stable resize frame
  private final Vector3 dragC0 = new Vector3();
  private float dragHalfW0, dragHalfH0, dragRot0;
  private final Vector3 dragXw = new Vector3();
  private final Vector3 dragZw = new Vector3();
  private final Vector3 dragAnchor = new Vector3();

  public CaptureAreaTool(TerrainSystem terrain, MountainTool mountainTool) {
    this.terrain = terrain;
    this.mountainTool = mountainTool;
    EditorContext.getFocusPosition(center);
  }

  @Override public String name() { return "Capture"; }

  @Override public void onSelected() {
    EditorContext.getFocusPosition(center);
  }
  @Override public void onDeselected() { }

  @Override public void buildUI(Table container, Skin skin) {
    container.add(new Label("Capture area into heightmap", skin, "title")).left().row();

    infoLabel = new Label("Center: ("+String.format("%.1f, %.1f", center.x, center.z)+")", skin);
    container.add(infoLabel).left().padTop(6).row();

    Table nameRow = new Table(); container.add(nameRow).left().padTop(4).row();
    nameRow.add(new Label("Heightmap name:", skin)).left().padRight(6);
    nameField = new TextField("captured_area.png", skin);
    nameField.setMessageText("captured_area.png");
    nameRow.add(nameField).width(240).left();

    Table t = new Table(); container.add(t).left().row();
    t.add(new Label("Half-width X (m)", skin)).left().row();
    wSlider = new Slider(5f, 1500f, 1f, false, skin); wSlider.setValue(halfW);
    wSlider.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ halfW = wSlider.getValue(); }});
    t.add(wSlider).width(260).row();

    t.add(new Label("Half-height Z (m)", skin)).left().row();
    hSlider = new Slider(5f, 1500f, 1f, false, skin); hSlider.setValue(halfH);
    hSlider.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ halfH = hSlider.getValue(); }});
    t.add(hSlider).width(260).row();

    t.add(new Label("Rotation (deg)", skin)).left().row();
    rotSlider = new Slider(0f, 360f, 1f, false, skin); rotSlider.setValue(rotDeg);
    rotSlider.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ rotDeg = rotSlider.getValue(); }});
    t.add(rotSlider).width(260).row();

    // No falloff slider: falloff is always 0 on import

    // Removed intensity control: captured heightmap is normalized (0..1) and MountainTool controls intensity.

    TextButton cap = new TextButton("Capture Area", skin);
    cap.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ capture(); }});
    container.add(cap).left().padTop(8).row();
  }

  @Override public void update(float dt, Camera cam) {
    if (infoLabel != null) infoLabel.setText("Center: ("+String.format("%.1f, %.1f", center.x, center.z)+")");
  }

  @Override public void render3D(Camera cam) {
    // Draw rectangle outline + highlight hovered resize edges on y=0
    float s = MathUtils.sinDeg(rotDeg), c = MathUtils.cosDeg(rotDeg);
    float[] lx = { -halfW,  halfW,  halfW, -halfW };
    float[] lz = { -halfH, -halfH,  halfH,  halfH };
    float[] wx = new float[4];
    float[] wz = new float[4];
    for (int i=0;i<4;i++){ float rx =  lx[i]*c - lz[i]*s; float rz =  lx[i]*s + lz[i]*c; wx[i] = center.x + rx; wz[i] = center.z + rz; }

    // Determine hover edge
    DragMode hover = DragMode.NONE;
    if (cam != null && groundHit(cam, tmpHover)) {
      float base = Math.max(halfW, halfH);
      float marginMeters = Math.max(0.5f, handleMarginPct * base);
      hover = detectHandle(tmpHover.x, tmpHover.z, marginMeters);
    }
    com.badlogic.gdx.Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    com.badlogic.gdx.Gdx.gl.glDepthMask(false);
    com.badlogic.gdx.Gdx.gl.glEnable(GL20.GL_BLEND);
    com.badlogic.gdx.Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

    lines.begin(cam.combined, GL20.GL_LINES);
    // Base outline color
    float br=1f,bg=0.85f,bb=0.2f,ba=1f;
    // Edge highlight color
    float hr=0.2f,hg=0.95f,hb=1f,ha=1f;
    // Edges: bottom(0-1), right(1-2), top(2-3), left(3-0)
    // Bottom
    if (hover == DragMode.RESIZE_B) { lines.color(hr,hg,hb,ha); } else { lines.color(br,bg,bb,ba); }
    lines.vertex(wx[0], 0f, wz[0]);
    if (hover == DragMode.RESIZE_B) { lines.color(hr,hg,hb,ha); } else { lines.color(br,bg,bb,ba); }
    lines.vertex(wx[1], 0f, wz[1]);
    // Right
    if (hover == DragMode.RESIZE_R) { lines.color(hr,hg,hb,ha); } else { lines.color(br,bg,bb,ba); }
    lines.vertex(wx[1], 0f, wz[1]);
    if (hover == DragMode.RESIZE_R) { lines.color(hr,hg,hb,ha); } else { lines.color(br,bg,bb,ba); }
    lines.vertex(wx[2], 0f, wz[2]);
    // Top
    if (hover == DragMode.RESIZE_T) { lines.color(hr,hg,hb,ha); } else { lines.color(br,bg,bb,ba); }
    lines.vertex(wx[2], 0f, wz[2]);
    if (hover == DragMode.RESIZE_T) { lines.color(hr,hg,hb,ha); } else { lines.color(br,bg,bb,ba); }
    lines.vertex(wx[3], 0f, wz[3]);
    // Left
    if (hover == DragMode.RESIZE_L) { lines.color(hr,hg,hb,ha); } else { lines.color(br,bg,bb,ba); }
    lines.vertex(wx[3], 0f, wz[3]);
    if (hover == DragMode.RESIZE_L) { lines.color(hr,hg,hb,ha); } else { lines.color(br,bg,bb,ba); }
    lines.vertex(wx[0], 0f, wz[0]);
    lines.end();

    com.badlogic.gdx.Gdx.gl.glDisable(GL20.GL_BLEND);
    com.badlogic.gdx.Gdx.gl.glDepthMask(true);
    com.badlogic.gdx.Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
  }

  // ------------ Pointer interaction (matches MountainTool style) ------------
  @Override public void onPointerDown(int button, boolean shift, boolean ctrl) {
    Camera cam = EditorContext.getCamera(); if (cam == null) return;
    if (!groundHit(cam, tmpW)) return;
    float base = Math.max(halfW, halfH);
    float marginMeters = Math.max(0.5f, handleMarginPct * base);
    DragMode m = detectHandle(tmpW.x, tmpW.z, marginMeters);
    dragMode = m;
    dragStartX = tmpW.x; dragStartZ = tmpW.z;
    // Snapshot state for stable resizing
    dragC0.set(center); dragHalfW0 = halfW; dragHalfH0 = halfH; dragRot0 = rotDeg;
    float s0 = MathUtils.sinDeg(dragRot0), c0 = MathUtils.cosDeg(dragRot0);
    dragXw.set(c0, 0f, s0); dragZw.set(-s0, 0f, c0);
    switch (dragMode) {
      case RESIZE_L: dragAnchor.set(dragC0).mulAdd(dragXw, +dragHalfW0); break; // right edge fixed
      case RESIZE_R: dragAnchor.set(dragC0).mulAdd(dragXw, -dragHalfW0); break; // left edge fixed
      case RESIZE_B: dragAnchor.set(dragC0).mulAdd(dragZw, +dragHalfH0); break; // top edge fixed
      case RESIZE_T: dragAnchor.set(dragC0).mulAdd(dragZw, -dragHalfH0); break; // bottom edge fixed
      default: break;
    }
  }

  @Override public void onPointerDrag(int button, boolean shift, boolean ctrl) {
    Camera cam = EditorContext.getCamera(); if (cam == null) return;
    if (!groundHit(cam, tmpW)) return;
    float dx = tmpW.x - dragStartX; float dz = tmpW.z - dragStartZ;
    if (dragMode == DragMode.MOVE) {
      center.add(dx, 0f, dz);
      dragStartX = tmpW.x; dragStartZ = tmpW.z;
    } else if (dragMode != DragMode.NONE) {
      // Use snapshot frame for stability
      float lx = dragXw.x * dx + dragXw.z * dz;
      float lz = dragZw.x * dx + dragZw.z * dz;
      switch (dragMode) {
        case RESIZE_L: {
          float newHalf = Math.max(1f, dragHalfW0 - lx * 0.5f);
          halfW = newHalf; center.set(dragAnchor).mulAdd(dragXw, -newHalf);
          break;
        }
        case RESIZE_R: {
          float newHalf = Math.max(1f, dragHalfW0 + lx * 0.5f);
          halfW = newHalf; center.set(dragAnchor).mulAdd(dragXw, +newHalf);
          break;
        }
        case RESIZE_B: {
          float newHalf = Math.max(1f, dragHalfH0 - lz * 0.5f);
          halfH = newHalf; center.set(dragAnchor).mulAdd(dragZw, -newHalf);
          break;
        }
        case RESIZE_T: {
          float newHalf = Math.max(1f, dragHalfH0 + lz * 0.5f);
          halfH = newHalf; center.set(dragAnchor).mulAdd(dragZw, +newHalf);
          break;
        }
        default: break;
      }
      // Keep dragStart fixed during resize for stable deltas
      if (wSlider != null) wSlider.setValue(halfW);
      if (hSlider != null) hSlider.setValue(halfH);
    }
  }

  @Override public void onPointerUp(int button, boolean shift, boolean ctrl) { dragMode = DragMode.NONE; }

  private final Vector3 tmpW = new Vector3();
  private final Vector3 tmpHover = new Vector3();
  private boolean groundHit(Camera cam, Vector3 out){
    com.badlogic.gdx.math.collision.Ray ray = ((com.badlogic.gdx.graphics.PerspectiveCamera)cam).getPickRay(com.badlogic.gdx.Gdx.input.getX(), com.badlogic.gdx.Gdx.input.getY());
    if (Math.abs(ray.direction.y) < 1e-5f) return false;
    float t = -ray.origin.y / ray.direction.y; if (t < 0f) return false; out.set(ray.origin).mulAdd(ray.direction, t); return true;
  }
  private DragMode detectHandle(float x, float z, float margin){
    float s = MathUtils.sinDeg(rotDeg), c = MathUtils.cosDeg(rotDeg);
    float lx = x - center.x, lz = z - center.z;
    float rx =  c*lx + s*lz; float rz = -s*lx + c*lz;
    float dx = Math.abs(Math.abs(rx) - halfW);
    float dz = Math.abs(Math.abs(rz) - halfH);
    boolean nearX = dx <= margin && Math.abs(rz) <= halfH + margin;
    boolean nearZ = dz <= margin && Math.abs(rx) <= halfW + margin;
    if (nearX) return rx < 0 ? DragMode.RESIZE_L : DragMode.RESIZE_R;
    if (nearZ) return rz < 0 ? DragMode.RESIZE_B : DragMode.RESIZE_T;
    if (Math.abs(rx) <= halfW && Math.abs(rz) <= halfH) return DragMode.MOVE;
    return DragMode.NONE;
  }

  // ------------ Capture flow ------------
  private void capture(){
    if (terrain == null || mountainTool == null) return;
    final String imageName = (nameField != null && nameField.getText() != null && !nameField.getText().isEmpty()) ? nameField.getText() : "captured_area.png";
    EditorContext.requestRefresh("Capturing area...");

    final float cx = center.x, cz = center.z;
    final float hw = Math.max(1f, halfW), hh = Math.max(1f, halfH);
    final float rot = rotDeg;

    // Run heavy work off the render thread
    new Thread(() -> {
      try {
        // Build heightmap by sampling COMBINED meters = baseHmx + live stamp delta (what you see now).
        // Then write a NORMALIZED heightmap (0..1) and store the oriented range in 'intensity'.
        int res = 512;
        Pixmap pm = new Pixmap(res, res, Pixmap.Format.RGBA8888);
        float[] metersBuf = new float[res * res];
        java.util.List<TerrainSystem.MountainStamp> stamps = mountainTool.getStampsSupplier().get();
        // Decode chunks needed for bilinear base height sampling
        var mapCap = terrain.getMap(); var mfCap = mapCap.getManifest();
        final int SCap = mfCap.samplesPerSide; final float tileCap = mfCap.tile; final float yScaleCap = terrain.getYScale();
        int cpcCap = SCap - 1; float chunkSizeCap = cpcCap * tileCap;
        int minCxCap = (int)Math.floor((cx - hw) / chunkSizeCap); int maxCxCap = (int)Math.floor((cx + hw) / chunkSizeCap);
        int minCzCap = (int)Math.floor((cz - hh) / chunkSizeCap); int maxCzCap = (int)Math.floor((cz + hh) / chunkSizeCap);
        java.util.Map<String, com.example.game.World.SparseChunkProvider.LoadedChunk> chunksCap = new java.util.HashMap<>();
        for (int czI=minCzCap; czI<=maxCzCap; czI++) for (int cxI=minCxCap; cxI<=maxCxCap; cxI++) {
          byte[] raw = mapCap.readChunkHmx(cxI, czI);
          com.example.game.World.SparseChunkProvider.LoadedChunk lc;
          if (raw != null && raw.length > 0) lc = new com.example.game.World.SparseChunkProvider("", SCap).loadFromBytes(raw);
          else lc = new com.example.game.World.SparseChunkProvider.LoadedChunk(new short[SCap*SCap], new byte[SCap*SCap], new byte[SCap*SCap], new byte[SCap*SCap], new byte[SCap*SCap], new byte[SCap*SCap], new byte[SCap*SCap]);
          chunksCap.put(cxI+":"+czI, lc);
        }

        float sin = MathUtils.sinDeg(rot), cos = MathUtils.cosDeg(rot);
        float minMeters = Float.POSITIVE_INFINITY, maxMeters = Float.NEGATIVE_INFINITY;
        for (int y = 0; y < res; y++) {
          for (int x = 0; x < res; x++) {
            float accum = 0f;
            int samples = 0;
            for (int sy = 0; sy < 4; sy++) {
              for (int sx = 0; sx < 4; sx++) {
                float u = (x + (sx + 0.5f) / 4f) / res;
                float v = (y + (sy + 0.5f) / 4f) / res;
                float rx = (u - 0.5f) * (2f * hw), rz = (v - 0.5f) * (2f * hh);
                float lx =  rx * cos - rz * sin, lz =  rx * sin + rz * cos;
                float wx = cx + lx, wz = cz + lz;
                // base meters from HMX
                int cxI = (int)Math.floor(wx / chunkSizeCap), czI = (int)Math.floor(wz / chunkSizeCap);
                com.example.game.World.SparseChunkProvider.LoadedChunk lc = chunksCap.get(cxI+":"+czI);
                float originX = cxI * chunkSizeCap, originZ = czI * chunkSizeCap;
                float gx = (wx - originX) / tileCap, gz = (wz - originZ) / tileCap;
                int x0 = Math.max(0, Math.min(SCap - 1, (int)Math.floor(gx)));
                int z0 = Math.max(0, Math.min(SCap - 1, (int)Math.floor(gz)));
                int x1 = Math.min(SCap - 1, x0 + 1), z1 = Math.min(SCap - 1, z0 + 1);
                float tx = gx - x0, tz = gz - z0;
                short[] h = (lc != null ? lc.heights : null);
                float h00 = (h != null ? h[z0 * SCap + x0] * yScaleCap : 0f);
                float h10 = (h != null ? h[z0 * SCap + x1] * yScaleCap : 0f);
                float h01 = (h != null ? h[z1 * SCap + x0] * yScaleCap : 0f);
                float h11 = (h != null ? h[z1 * SCap + x1] * yScaleCap : 0f);
                float hx0 = h00 + (h10 - h00) * tx; float hx1 = h01 + (h11 - h01) * tx;
                float baseMeters = hx0 + (hx1 - hx0) * tz;
                // live delta meters from stamps
                float deltaMeters = sampleMountainsMeters(stamps, wx, wz);
                accum += baseMeters + deltaMeters;
                samples++;
              }
            }
            float meters = accum / Math.max(1, samples);
            if (meters < minMeters) minMeters = meters; if (meters > maxMeters) maxMeters = meters;
            metersBuf[y * res + x] = meters;
          }
        }
        // Normalize to 0..1 heightmap; preserve orientation separately
        boolean inverted = (-minMeters > maxMeters);
        float orientedPeak = inverted ? (-minMeters) : maxMeters;
        float denom = Math.max(1e-6f, orientedPeak);
        for (int y = 0; y < res; y++) for (int x = 0; x < res; x++) {
          float meters = metersBuf[y * res + x];
          float m = inverted ? -meters : meters; // ensure non-negative
          float v = MathUtils.clamp(m / denom, 0f, 1f);
          pm.setColor(v, v, v, 1f);
          pm.drawPixel(x, y);
        }

        // Phase 1: flatten HMX heights inside rectangle to baseline 0
        var map = terrain.getMap();
        var mf = map.getManifest();
        final int S = mf.samplesPerSide; final float tile = mf.tile; final float yScale = terrain.getYScale();
        int cpc = S - 1; float chunkSize = cpc * tile;
        int minCx = (int)Math.floor((cx - hw) / chunkSize); int maxCx = (int)Math.floor((cx + hw) / chunkSize);
        int minCz = (int)Math.floor((cz - hh) / chunkSize); int maxCz = (int)Math.floor((cz + hh) / chunkSize);

        // Decode overlapping chunks for flattening
        java.util.Map<String, com.example.game.World.SparseChunkProvider.LoadedChunk> chunks = new java.util.HashMap<>();
        for (int czI=minCz; czI<=maxCz; czI++) for (int cxI=minCx; cxI<=maxCx; cxI++) {
          byte[] raw = map.readChunkHmx(cxI, czI);
          com.example.game.World.SparseChunkProvider.LoadedChunk lc;
          if (raw != null && raw.length > 0) lc = new com.example.game.World.SparseChunkProvider("", S).loadFromBytes(raw);
          else lc = new com.example.game.World.SparseChunkProvider.LoadedChunk(new short[S*S], new byte[S*S], new byte[S*S], new byte[S*S], new byte[S*S], new byte[S*S], new byte[S*S]);
          chunks.put(cxI+":"+czI, lc);
        }

        java.util.Map<String, byte[]> toWrite = new java.util.LinkedHashMap<>();
        for (int czI=minCz; czI<=maxCz; czI++) for (int cxI=minCx; cxI<=maxCx; cxI++) {
          com.example.game.World.SparseChunkProvider.LoadedChunk lc = chunks.get(cxI+":"+czI);
          short[] heights = lc.heights; if (heights == null) continue;
          float originX = cxI * chunkSize, originZ = czI * chunkSize;
          int x0 = Math.max(0, (int)Math.floor((cx - hw - originX) / tile));
          int x1 = Math.min(S - 1, (int)Math.ceil((cx + hw - originX) / tile));
          int z0 = Math.max(0, (int)Math.floor((cz - hh - originZ) / tile));
          int z1 = Math.min(S - 1, (int)Math.ceil((cz + hh - originZ) / tile));
          for (int gz=z0; gz<=z1; gz++) {
            float wz = originZ + gz * tile;
            for (int gx=x0; gx<=x1; gx++) {
              float wx = originX + gx * tile;
              // test inside rotated rect
              float lx = wx - cx, lz = wz - cz; float rx =  lx * cos + lz * sin; float rz = -lx * sin + lz * cos;
              if (Math.abs(rx) <= hw && Math.abs(rz) <= hh) heights[gz * S + gx] = 0; // baseline 0 units
            }
          }
          byte[] blob = com.example.game.World.SparseChunkWriter.encodeToBytes(S, heights, lc.id0, lc.id1, lc.id2, lc.w0, lc.w1, lc.w2);
          toWrite.put(cxI + "_" + czI + ".hmx", blob);
        }

        // Apply on render thread: write, remove overlapping stamps, reload; then import mountain and reload
        com.badlogic.gdx.Gdx.app.postRunnable(() -> {
          try {
            var map2 = terrain.getMap();
            if (!toWrite.isEmpty()) map2.saveInPlaceDeltaIncludingStaged(toWrite);
          } catch (Throwable ignored) {}
          mountainTool.removeNodesOverlapping(cx, cz, hw, hh, rot, 0.0f);
          EditorContext.requestRefresh("Flattening baselines...");
          terrain.reloadAllChunksAsyncInPlace();

          // After flatten, import mountain
          com.badlogic.gdx.Gdx.app.postRunnable(new Runnable(){
            long start = System.currentTimeMillis();
            @Override public void run() {
              float p = terrain.getReloadProgress01();
              if (p >= 0.999f || System.currentTimeMillis() - start > 8000) {
                // Re-import with intensity computed from mountain peak height (orientedPeak) and zero falloff
                mountainTool.addImportedPixmap(pm, imageName, cx, cz, hw, hh, rot, orientedPeak, 0f, inverted);
                EditorContext.requestRefresh("Applying captured heightmap...");
                terrain.reloadAllChunksAsyncInPlace();
              } else {
                com.badlogic.gdx.Gdx.app.postRunnable(this);
              }
            }
          });
        });
      } catch (Throwable t) {
        // swallow to avoid crashing UI
      }
    }, "CaptureAreaThread").start();
  }

  private static float sampleMountainsMeters(List<TerrainSystem.MountainStamp> stamps, float wx, float wz) {
    if (stamps == null || stamps.isEmpty()) return 0f;
    float total = 0f;
    for (TerrainSystem.MountainStamp s : stamps) {
      if (s == null || s.heightmap == null) continue;
      float cx = s.centerX, cz = s.centerZ;
      float sin = (float)Math.sin(Math.toRadians(s.rotationDeg));
      float cos = (float)Math.cos(Math.toRadians(s.rotationDeg));
      float lx = wx - cx, lz = wz - cz;
      float rx =  lx * cos + lz * sin;
      float rz = -lx * sin + lz * cos;
      float ax = Math.abs(rx) / Math.max(1e-6f, s.halfW);
      float az = Math.abs(rz) / Math.max(1e-6f, s.halfH);
      if (ax > 1f || az > 1f) continue;
      float u = (rx * 0.5f / s.halfW) + 0.5f;
      float v = (rz * 0.5f / s.halfH) + 0.5f;
      int iw = s.heightmap.getWidth(), ih = s.heightmap.getHeight();
      // bilinear luminance
      float lum = sampleLumBilinear(s.heightmap, u, v);
      float ff = clamp01(s.edgeFalloff);
      float p = 2.5f;
      float t = (float)Math.pow(Math.pow(ax, p) + Math.pow(az, p), 1.0 / p);
      float bandStart = 1f - ff;
      float denom = Math.max(1e-6f, 1f - bandStart);
      float s2 = clamp01((t - bandStart) / denom);
      float shapedEdge = 1f - smoothstep01(s2);
      float sign = s.inverted ? -1f : 1f;
      total += sign * s.intensity * lum * shapedEdge;
    }
    return total;
  }

  private static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }
  private static float smoothstep01(float x){ float t = clamp01(x); return t*t*(3f - 2f*t); }
  private static float sampleLumBilinear(Pixmap pm, float u, float v) {
    if (pm == null) return 0f;
    int iw = pm.getWidth(); int ih = pm.getHeight();
    if (iw <= 0 || ih <= 0) return 0f;
    float fx = Math.max(0f, Math.min(iw - 1f, u * (iw - 1)));
    float fy = Math.max(0f, Math.min(ih - 1f, v * (ih - 1)));
    int x0 = (int)Math.floor(fx), x1 = Math.min(iw - 1, x0 + 1);
    int y0 = (int)Math.floor(fy), y1 = Math.min(ih - 1, y0 + 1);
    float tx = fx - x0, ty = fy - y0;
    int p00 = pm.getPixel(x0, y0);
    int p10 = pm.getPixel(x1, y0);
    int p01 = pm.getPixel(x0, y1);
    int p11 = pm.getPixel(x1, y1);
    float r00 = ((p00 >>> 24) & 0xff) / 255f; float g00 = ((p00 >>> 16) & 0xff) / 255f; float b00 = ((p00 >>> 8) & 0xff) / 255f;
    float r10 = ((p10 >>> 24) & 0xff) / 255f; float g10 = ((p10 >>> 16) & 0xff) / 255f; float b10 = ((p10 >>> 8) & 0xff) / 255f;
    float r01 = ((p01 >>> 24) & 0xff) / 255f; float g01 = ((p01 >>> 16) & 0xff) / 255f; float b01 = ((p01 >>> 8) & 0xff) / 255f;
    float r11 = ((p11 >>> 24) & 0xff) / 255f; float g11 = ((p11 >>> 16) & 0xff) / 255f; float b11 = ((p11 >>> 8) & 0xff) / 255f;
    float l00 = (r00 + g00 + b00) / 3f;
    float l10 = (r10 + g10 + b10) / 3f;
    float l01 = (r01 + g01 + b01) / 3f;
    float l11 = (r11 + g11 + b11) / 3f;
    float lx0 = l00 + (l10 - l00) * tx;
    float lx1 = l01 + (l11 - l01) * tx;
    return lx0 + (lx1 - lx0) * ty;
  }
}
