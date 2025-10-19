package com.example.game.World.Editor.Tools.Terrain;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.example.game.World.Editor.Tools.Tool;
import com.example.game.World.TerrainSystem;
import com.example.game.World.TerrainSystem.MountainStamp;
import com.example.game.World.Editor.EditorContext;

import java.util.ArrayList;
import java.util.List;

/** Mountain tool: import heightmap images, place rectangular nodes at y=0, always drawn while active,
 *  can scale/rotate, and bake as height modifiers with intensity and edge falloff. */
public final class MountainTool implements Tool {
  private final TerrainSystem terrain;
  private final List<Node> nodes = new ArrayList<>();

  private final Vector3 tmp = new Vector3();
  private final Vector3 mouseW = new Vector3();

  // UI state
  private Node selected;
  private Label selLabel;
  private Slider intensitySlider, falloffSlider, scaleXSlider, scaleZSlider, rotSlider;
  private Label intensityLabel, falloffLabel, scaleXLabel, scaleZLabel, rotLabel;
  private com.badlogic.gdx.scenes.scene2d.ui.CheckBox invertedCheck;
  private com.badlogic.gdx.scenes.scene2d.ui.List<String> nodeList;
  private TextButton deleteBtn;
  private boolean postInstalled = false;
  private String lastImportedName;
  private float handleMarginPct = 0.08f;
  private boolean updatingUI = false;
  private int selectedIndex = -1;
  private static String lastImageDir;

  // Simple textured quad shader for preview
  private ShaderProgram shader;
  private final ImmediateModeRenderer20 imLines = new ImmediateModeRenderer20(2048, false, true, 0);

  // Interaction state
  private enum DragMode { NONE, MOVE, RESIZE_L, RESIZE_R, RESIZE_T, RESIZE_B }
  private DragMode dragMode = DragMode.NONE;
  private float dragStartX, dragStartZ;
  private Node draggingNode;
  // Stable resize frame
  private final Vector3 dragC0 = new Vector3();
  private float dragHalfW0, dragHalfH0, dragRot0;
  private final Vector3 dragXw = new Vector3();
  private final Vector3 dragZw = new Vector3();
  private final Vector3 dragAnchor = new Vector3();

  public MountainTool(TerrainSystem terrain) {
    this.terrain = terrain;
  }

  /** Programmatic import: create a node from a given heightmap pixmap. */
  public void addImportedPixmap(com.badlogic.gdx.graphics.Pixmap pm,
                                String imageName,
                                float centerX, float centerZ,
                                float halfW, float halfH,
                                float rotationDeg,
                                float intensity,
                                float edgeFalloff,
                                boolean inverted) {
    if (pm == null) return;
    Node n = new Node();
    n.heightmap = pm; n.preview = new Texture(pm);
    n.preview.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
    n.preview.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
    n.imageName = (imageName == null ? "captured.png" : imageName);
    n.center.set(centerX, 0f, centerZ);
    n.halfW = Math.max(1f, halfW);
    n.halfH = Math.max(1f, halfH);
    n.rotationDeg = rotationDeg;
    n.intensity = Math.max(0.001f, intensity);
    n.edgeFalloff = Math.max(0f, Math.min(1f, edgeFalloff));
    n.inverted = inverted;
    nodes.add(n);
    select(n);
    rebuildNodeList();
    saveMountainsToMap();
  }

  /** Remove any existing nodes that overlap the given rectangle in world space; returns removed count. */
  public int removeNodesOverlapping(float cx, float cz, float halfW, float halfH, float rotDeg, float marginMeters) {
    if (nodes.isEmpty()) return 0;
    float s = com.badlogic.gdx.math.MathUtils.sinDeg(rotDeg), c = com.badlogic.gdx.math.MathUtils.cosDeg(rotDeg);
    java.util.Iterator<Node> it = nodes.iterator();
    int removed = 0;
    while (it.hasNext()) {
      Node n = it.next();
      if (rectsOverlap(cx, cz, halfW, halfH, s, c, n)) { it.remove(); removed++; }
    }
    if (removed > 0) { select(null); rebuildNodeList(); saveMountainsToMap(); }
    return removed;
  }

  private static boolean rectsOverlap(float cx, float cz, float hw, float hh, float s, float c, Node n) {
    // Quick radius test
    float rA = (float)Math.hypot(hw, hh);
    float rB = (float)Math.hypot(n.halfW, n.halfH);
    float dx = (n.center.x - cx), dz = (n.center.z - cz);
    if (dx*dx + dz*dz > (rA + rB) * (rA + rB)) return false;
    // SAT on 2D OBBs (XZ)
    float s1 = s, c1 = c;
    float s2 = com.badlogic.gdx.math.MathUtils.sinDeg(n.rotationDeg), c2 = com.badlogic.gdx.math.MathUtils.cosDeg(n.rotationDeg);
    // Axes (unit) for both boxes
    float ax1x = c1, ax1z = s1; // local X axis of A in world
    float az1x = -s1, az1z = c1; // local Z axis of A in world
    float ax2x = c2, ax2z = s2;
    float az2x = -s2, az2z = c2;
    // Vector between centers
    float tx = n.center.x - cx, tz = n.center.z - cz;
    // Projections of half-extents onto each axis
    // Test axes: ax1, az1, ax2, az2
    if (separatedOnAxis(tx, tz, ax1x, ax1z, hw, hh, ax2x, ax2z, n.halfW, n.halfH)) return false;
    if (separatedOnAxis(tx, tz, az1x, az1z, hw, hh, ax2x, ax2z, n.halfW, n.halfH)) return false;
    if (separatedOnAxis(tx, tz, ax2x, ax2z, hw, hh, ax2x, ax2z, n.halfW, n.halfH, ax1x, ax1z, az1x, az1z)) return false;
    if (separatedOnAxis(tx, tz, az2x, az2z, hw, hh, ax2x, ax2z, n.halfW, n.halfH, ax1x, ax1z, az1x, az1z)) return false;
    return true;
  }

  private static boolean separatedOnAxis(float tx, float tz, float axX, float axZ,
                                         float aHW, float aHH,
                                         float bxX, float bxZ, float bHW, float bHH) {
    // Overload uses only A axes; B axes provided via bx/bz for projections
    // Project center distance onto axis
    float dist = Math.abs(tx * axX + tz * axZ);
    // Radius of A on axis
    float rA = Math.abs(aHW * (axX) + aHH * (axZ));
    // Radius of B on axis: project its local axes onto axis and weight by half extents
    float rB = Math.abs(bHW * (bxX * axX + bxZ * axZ)) + Math.abs(bHH * ((-bxZ) * axX + (bxX) * axZ));
    return dist > (rA + rB);
  }

  private static boolean separatedOnAxis(float tx, float tz, float axX, float axZ,
                                         float aHW, float aHH,
                                         float bxX, float bxZ, float bHW, float bHH,
                                         float ax1x, float ax1z, float az1x, float az1z) {
    // Project center distance onto axis
    float dist = Math.abs(tx * axX + tz * axZ);
    // Radius of A on axis using its axes
    float rA = Math.abs(aHW * (ax1x * axX + ax1z * axZ)) + Math.abs(aHH * (az1x * axX + az1z * axZ));
    // Radius of B on axis using its axes
    float rB = Math.abs(bHW * (bxX * axX + bxZ * axZ)) + Math.abs(bHH * ((-bxZ) * axX + (bxX) * axZ));
    return dist > (rA + rB);
  }

  private static float[] cornersX(float cx, float cz, float hw, float hh, float s, float c){
    float[] lx = { -hw,  hw,  hw, -hw };
    float[] lz = { -hh, -hh,  hh,  hh };
    float[] wx = new float[4];
    for (int i=0;i<4;i++){ float rx =  lx[i]*c - lz[i]*s; float rz =  lx[i]*s + lz[i]*c; wx[i] = cx + rx; }
    return wx;
  }
  private static float[] cornersZ(float cx, float cz, float hw, float hh, float s, float c){
    float[] lx = { -hw,  hw,  hw, -hw };
    float[] lz = { -hh, -hh,  hh,  hh };
    float[] wz = new float[4];
    for (int i=0;i<4;i++){ float rx =  lx[i]*c - lz[i]*s; float rz =  lx[i]*s + lz[i]*c; wz[i] = cz + rz; }
    return wz;
  }
  private static float min4(float[] v){ return Math.min(Math.min(v[0], v[1]), Math.min(v[2], v[3])); }
  private static float max4(float[] v){ return Math.max(Math.max(v[0], v[1]), Math.max(v[2], v[3])); }

  // Apply the currently selected mountain into baseline HMXS across all affected chunks, save, and remove node
  private void makeSelectedPermanent() {
    if (terrain == null || selected == null || selected.heightmap == null) return;
    // Show non-blocking loading overlay in the editor while baking and reloading
    com.example.game.World.Editor.EditorContext.requestRefresh("Baking heightmap...");
    var map = terrain.getMap();
    var mf = map.getManifest();
    final int S = mf.samplesPerSide;
    final float tile = mf.tile;
    final float yScale = terrain.getYScale();

    // Capture current selection and ranges on main thread
    Node n = selected;
    final float cxw = n.center.x, czw = n.center.z;
    final float radius = (float)Math.hypot(n.halfW, n.halfH);
    int cpc = S - 1;
    final float chunkSize = cpc * tile;
    final int minCx = (int)Math.floor((cxw - radius) / chunkSize);
    final int maxCx = (int)Math.floor((cxw + radius) / chunkSize);
    final int minCz = (int)Math.floor((czw - radius) / chunkSize);
    final int maxCz = (int)Math.floor((czw + radius) / chunkSize);

    // Snapshot existing HMXS bytes for the affected chunks on main thread to avoid thread-safety issues
    java.util.Map<String, byte[]> rawByChunk = new java.util.LinkedHashMap<>();
    for (int cz = minCz; cz <= maxCz; cz++) {
      for (int cx = minCx; cx <= maxCx; cx++) {
        byte[] raw = map.readChunkHmx(cx, cz);
        rawByChunk.put(cx + ":" + cz, (raw == null ? null : java.util.Arrays.copyOf(raw, raw.length)));
        boolean listed = false;
        for (int[] c : mf.chunks) { if (c != null && c.length >= 2 && c[0]==cx && c[1]==cz) { listed = true; break; } }
        if (!listed) mf.chunks.add(new int[]{cx, cz});
      }
    }

    // Precompute rotation and inverses (immutable for the thread)
    final float sin = (float)Math.sin(Math.toRadians(n.rotationDeg));
    final float cos = (float)Math.cos(Math.toRadians(n.rotationDeg));
    final float invHalfW = 1f / Math.max(1e-6f, n.halfW);
    final float invHalfH = 1f / Math.max(1e-6f, n.halfH);

    // Heavy compute off the render thread
    new Thread(() -> {
      java.util.Map<String, byte[]> toWrite = new java.util.LinkedHashMap<>();
      try {
        for (int cz = minCz; cz <= maxCz; cz++) {
          for (int cx = minCx; cx <= maxCx; cx++) {
            byte[] raw = rawByChunk.get(cx + ":" + cz);
            com.example.game.World.SparseChunkProvider.LoadedChunk lc;
            if (raw != null && raw.length > 0) {
              lc = new com.example.game.World.SparseChunkProvider("", S).loadFromBytes(raw);
            } else {
              int samples = S * S;
              short[] heights = new short[samples];
              byte[] id0 = new byte[samples];
              byte[] id1 = new byte[samples];
              byte[] id2 = new byte[samples];
              byte[] w0  = new byte[samples];
              byte[] w1  = new byte[samples];
              byte[] w2  = new byte[samples];
              for (int i = 0; i < samples; i++) { id0[i]=0; id1[i]=0; id2[i]=0; w0[i]=(byte)255; w1[i]=0; w2[i]=0; }
              lc = new com.example.game.World.SparseChunkProvider.LoadedChunk(heights, id0, id1, id2, w0, w1, w2);
            }

            short[] heights = lc.heights;
            byte[] id0 = lc.id0, id1 = lc.id1, id2 = lc.id2, w0 = lc.w0, w1 = lc.w1, w2 = lc.w2;
            float originX = cx * chunkSize;
            float originZ = cz * chunkSize;
            short[] baseHeights = java.util.Arrays.copyOf(heights, heights.length);

            int x0 = Math.max(0, (int)Math.floor((cxw - radius - originX) / tile));
            int x1 = Math.min(S - 1, (int)Math.ceil((cxw + radius - originX) / tile));
            int z0 = Math.max(0, (int)Math.floor((czw - radius - originZ) / tile));
            int z1 = Math.min(S - 1, (int)Math.ceil((czw + radius - originZ) / tile));

            for (int gz = z0; gz <= z1; gz++) {
              float wz = originZ + gz * tile;
              for (int gx = x0; gx <= x1; gx++) {
                float wx = originX + gx * tile;
                float deltaMetersAvg = 0f;
                for (int sy = 0; sy < 4; sy++) {
                  for (int sx = 0; sx < 4; sx++) {
                    float fx = (sx + 0.5f) / 4f;
                    float fz = (sy + 0.5f) / 4f;
                    float ox = fx * tile, oz = fz * tile;
                    float px = wx + ox, pz = wz + oz;
                    float lx = px - cxw, lz = pz - czw;
                    float rx =  lx * cos + lz * sin;
                    float rz = -lx * sin + lz * cos;
                    float ax = Math.abs(rx) * invHalfW;
                    float az = Math.abs(rz) * invHalfH;
                    if (ax > 1f || az > 1f) continue;
                    float u = (rx * 0.5f / n.halfW) + 0.5f;
                    float v = (rz * 0.5f / n.halfH) + 0.5f;
                    float lum = sampleLumBilinear(n.heightmap, u, v);
                    float ff = Math.max(0f, Math.min(1f, n.edgeFalloff));
                    float p = 2.5f;
                    float t = (float)Math.pow(Math.pow(ax, p) + Math.pow(az, p), 1.0 / p);
                    float bandStart = 1f - ff;
                    float denom = Math.max(1e-6f, 1f - bandStart);
                    float s2 = Math.max(0f, Math.min(1f, (t - bandStart) / denom));
                    float shapedEdge = 1f - (s2*s2*(3f - 2f*s2));
                    float sign = (n != null && n.inverted) ? -1f : 1f;
                    deltaMetersAvg += sign * n.intensity * lum * shapedEdge;
                  }
                }
                deltaMetersAvg *= (1f / 16f);
                int idx = gz * S + gx;
                int deltaUnits = Math.round(deltaMetersAvg / Math.max(1e-6f, yScale));
                int base = baseHeights[idx];
                int preserved = heights[idx] - base;
                int nv = base + preserved + deltaUnits;
                if (nv < Short.MIN_VALUE) nv = Short.MIN_VALUE; else if (nv > Short.MAX_VALUE) nv = Short.MAX_VALUE;
                heights[idx] = (short)nv;
              }
            }

            try {
              byte[] blob = com.example.game.World.SparseChunkWriter.encodeToBytes(S, heights, id0, id1, id2, w0, w1, w2);
              toWrite.put(cx + "_" + cz + ".hmx", blob);
            } catch (Exception ex) {
              // continue
            }
          }
        }
      } catch (Throwable ignored) {}

      // Apply results on the render thread
      com.badlogic.gdx.Gdx.app.postRunnable(() -> {
        // Remove node and persist mountains.json so the deletion is included in this delta save
        int old = selectedIndex; nodes.remove(selected); selected = null; selectedIndex = Math.min(old, nodes.size()-1); if (selectedIndex >= 0) selected = nodes.get(selectedIndex);
        rebuildNodeList(); selLabel.setText(selected==null?"Selected: none":String.format("Selected: %s (%.1f, %.1f)",(selected.imageName==null?"-":selected.imageName), selected.center.x, selected.center.z));
        saveMountainsToMap();

        if (!toWrite.isEmpty()) {
          map.saveInPlaceDeltaIncludingStaged(toWrite);
        } else {
          map.saveInPlaceDeltaIncludingStaged(java.util.Collections.emptyMap());
        }
        // Trigger reload asynchronously to finish the flow and let the overlay close after stability
        terrain.reloadAllChunksAsyncInPlace();
      });
    }, "MountainBakeThread").start();
  }

  private static float sampleLumBilinear(Pixmap pm, float u, float v) {
    if (pm == null) return 0f;
    int iw = pm.getWidth();
    int ih = pm.getHeight();
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

  // Supplier used by centralized processor installation (EditorScreen)
  public java.util.function.Supplier<java.util.List<TerrainSystem.MountainStamp>> getStampsSupplier() {
    return this::buildStampsFromNodes;
  }

  @Override public String name() { return "Import"; }

  @Override public void onSelected() {
    if (shader == null) {
      ShaderProgram.pedantic = false;
      String vert =
          "attribute vec3 a_pos;\n"+
          "attribute vec2 a_uv;\n"+
          "uniform mat4 uProjView;\n"+
          "varying vec2 vUV;\n"+
          "void main(){ vUV=a_uv; gl_Position=uProjView*vec4(a_pos,1.0); }";
      String frag =
          "#ifdef GL_ES\nprecision mediump float;\n#endif\n"+
          "uniform sampler2D uTex;\n"+
          "uniform float uAlpha;\n"+
          "varying vec2 vUV;\n"+
          "void main(){ vec4 c=texture2D(uTex,vUV); gl_FragColor=vec4(c.rgb, c.a*uAlpha); }";
      shader = new ShaderProgram(vert, frag);
      if (!shader.isCompiled()) throw new IllegalStateException("MountainTool shader: "+shader.getLog());
    }
    // Post-processor is installed centrally in EditorScreen to avoid duplication
    // Load from map if present (only once on first selection)
    if (nodes.isEmpty()) {
      try { loadMountainsFromMap(); rebuildNodeList(); } catch (Throwable ignored) {}
    }
  }

  @Override public void onDeselected() { }

  private void rebuildNodeList() {
    if (nodeList == null) return;
    updatingUI = true;
    try {
      com.badlogic.gdx.utils.Array<String> items = new com.badlogic.gdx.utils.Array<>();
      for (int i=0;i<nodes.size();i++) {
        Node n = nodes.get(i);
        String nm = (n.imageName == null || n.imageName.isEmpty()) ? "-" : n.imageName;
        items.add(String.format("%02d: %s (%.1f, %.1f)", i, nm, n.center.x, n.center.z, n.rotationDeg));
      }
      nodeList.setItems(items);
      if (selectedIndex >= 0 && selectedIndex < nodes.size()) nodeList.setSelectedIndex(selectedIndex);
    } finally { updatingUI = false; }
  }

  @Override public void buildUI(Table container, Skin skin) {
    container.add(new Label("Mountain heightmap stamps", skin, "title")).left().row();
    Table row = new Table(); container.add(row).left().padTop(6).row();

    TextButton importBtn = new TextButton("Import Heightmap", skin);
    row.add(importBtn).padRight(8);

    // Node list
    nodeList = new com.badlogic.gdx.scenes.scene2d.ui.List<>(skin);
    nodeList.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
      if (updatingUI) return;
      int idx = nodeList.getSelectedIndex();
      if (idx >= 0 && idx < nodes.size()) select(nodes.get(idx));
    }});
    ScrollPane sp = new ScrollPane(nodeList, skin); sp.setFadeScrollBars(false);
    container.add(sp).width(320).height(140).left().padTop(8).row();

    selLabel = new Label("Selected: none", skin);
    container.add(selLabel).left().padTop(6).row();

    // Sliders
    Table t = new Table(); container.add(t).left().row();

    intensityLabel = new Label("Intensity: -", skin); intensitySlider = new Slider(0f, 2000f, 1f, false, skin);
    intensitySlider.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ if (updatingUI || selected==null) return; selected.intensity = intensitySlider.getValue(); intensityLabel.setText(String.format("Intensity: %.1f m", selected.intensity)); }});
    t.add(intensityLabel).left().row(); t.add(intensitySlider).width(260).row();

    falloffLabel = new Label("Edge falloff: -", skin); falloffSlider = new Slider(0f, 1f, 0.01f, false, skin);
    falloffSlider.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ if (updatingUI || selected==null) return; selected.edgeFalloff = falloffSlider.getValue(); falloffLabel.setText(String.format("Edge falloff: %.2f", selected.edgeFalloff)); }});
    t.add(falloffLabel).left().row(); t.add(falloffSlider).width(260).row();

    // Inverted checkbox
    invertedCheck = new com.badlogic.gdx.scenes.scene2d.ui.CheckBox(" Inverted (depth)", skin);
    invertedCheck.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ if (updatingUI || selected == null) return; selected.inverted = invertedCheck.isChecked(); com.example.game.World.Editor.EditorContext.requestRefresh("Updating mountains..."); saveMountainsToMap(); }});
    t.add(invertedCheck).left().padTop(6).row();

    scaleXLabel = new Label("Half-width X: -", skin); scaleXSlider = new Slider(5f, 1500f, 1f, false, skin);
    scaleXSlider.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ if (updatingUI || selected==null) return; selected.halfW = scaleXSlider.getValue(); scaleXLabel.setText(String.format("Half-width X: %.1f m", selected.halfW)); }});
    t.add(scaleXLabel).left().padTop(6).row(); t.add(scaleXSlider).width(260).row();

    scaleZLabel = new Label("Half-height Z: -", skin); scaleZSlider = new Slider(5f, 1500f, 1f, false, skin);
    scaleZSlider.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ if (updatingUI || selected==null) return; selected.halfH = scaleZSlider.getValue(); scaleZLabel.setText(String.format("Half-height Z: %.1f m", selected.halfH)); }});
    t.add(scaleZLabel).left().padTop(6).row(); t.add(scaleZSlider).width(260).row();

    rotLabel = new Label("Rotation: -", skin); rotSlider = new Slider(0f, 360f, 1f, false, skin);
    rotSlider.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ if (updatingUI || selected==null) return; selected.rotationDeg = rotSlider.getValue(); rotLabel.setText(String.format("Rotation: %.0f°", selected.rotationDeg)); }});
    t.add(rotLabel).left().padTop(6).row(); t.add(rotSlider).width(260).row();

    // Delete
    deleteBtn = new TextButton("Delete Selected", skin);
    deleteBtn.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
      if (selected == null) return; int old = selectedIndex; nodes.remove(selected); selected = null; selectedIndex = Math.min(old, nodes.size()-1); if (selectedIndex >= 0) selected = nodes.get(selectedIndex); rebuildNodeList(); selLabel.setText(selected==null?"Selected: none":String.format("Selected: %s (%.1f, %.1f)",(selected.imageName==null?"-":selected.imageName), selected.center.x, selected.center.z));
      saveMountainsToMap();
    }});
    container.add(deleteBtn).left().padTop(8).row();

    // Make Permanent
    TextButton makePerm = new TextButton("Make Permanent", skin);
    makePerm.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
      makeSelectedPermanent();
    }});
    container.add(makePerm).left().padTop(4).row();

    // Buttons logic
    importBtn.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor) {
      Pixmap pm = fileDialogPixmap();
      if (pm == null) return;
      Node n = new Node();
      n.heightmap = pm; n.preview = new Texture(pm);
      n.imageName = lastImportedName;
      n.intensity = 20f; n.edgeFalloff = 0.5f; n.halfW = 50f; n.halfH = 50f; n.rotationDeg = 0f;
      EditorContext.getFocusPosition(n.center);
      nodes.add(n); select(n); rebuildNodeList();
      saveMountainsToMap();
    }});


    

    // Initialize UI with current selection
    select(selected);
    rebuildNodeList();
  }

  private void select(Node n){
    selected = n;
    selectedIndex = (n == null ? -1 : nodes.indexOf(n));
    if (selLabel != null) selLabel.setText("Selected: "+ (n==null?"none":String.format("%s (%.1f, %.1f)", (n.imageName==null?"-":n.imageName), n.center.x, n.center.z)) );
    if (n != null) {
      updatingUI = true;
      try {
        if (intensitySlider != null) intensitySlider.setValue(n.intensity);
        if (falloffSlider != null) falloffSlider.setValue(n.edgeFalloff);
        if (scaleXSlider != null) scaleXSlider.setValue(n.halfW);
        if (scaleZSlider != null) scaleZSlider.setValue(n.halfH);
        if (rotSlider != null) rotSlider.setValue(n.rotationDeg);
        if (intensityLabel != null) intensityLabel.setText(String.format("Intensity: %.1f m", n.intensity));
        if (falloffLabel != null) falloffLabel.setText(String.format("Edge falloff: %.2f", n.edgeFalloff));
        if (scaleXLabel != null) scaleXLabel.setText(String.format("Half-width X: %.1f m", n.halfW));
        if (scaleZLabel != null) scaleZLabel.setText(String.format("Half-height Z: %.1f m", n.halfH));
        if (rotLabel != null) rotLabel.setText(String.format("Rotation: %.0f°", n.rotationDeg));
      } finally { updatingUI = false; }
    }
  }

  private Pixmap fileDialogPixmap(){
    final String[] abs = { null };
    try {
      javax.swing.SwingUtilities.invokeAndWait(() -> {
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        fc.setDialogTitle("Import Heightmap");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Images", "png", "jpg", "jpeg", "bmp"));
        try {
          // Persisted last dir
          com.badlogic.gdx.Preferences prefs = com.badlogic.gdx.Gdx.app != null ? com.badlogic.gdx.Gdx.app.getPreferences("editor_prefs") : null;
          String persisted = (prefs != null ? prefs.getString("lastHeightmapDir", null) : null);
          String startDir = (lastImageDir != null ? lastImageDir : persisted);
          if (startDir != null) fc.setCurrentDirectory(new java.io.File(startDir));
        } catch (Throwable ignored) {}
        if (fc.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
          abs[0] = fc.getSelectedFile().getAbsolutePath();
          java.io.File sel = fc.getSelectedFile();
          if (sel != null && sel.getParent() != null) {
            lastImageDir = sel.getParent();
            try {
              com.badlogic.gdx.Preferences prefs = com.badlogic.gdx.Gdx.app != null ? com.badlogic.gdx.Gdx.app.getPreferences("editor_prefs") : null;
              if (prefs != null) { prefs.putString("lastHeightmapDir", lastImageDir); prefs.flush(); }
            } catch (Throwable ignored) {}
          }
        }
      });
    } catch (Exception ignored) {}
    if (abs[0] == null) return null;
    var fh = Gdx.files.absolute(abs[0]);
    if (!fh.exists()) return null;
    lastImportedName = fh.name();
    Pixmap pm = new Pixmap(fh);
    normalizeHeightmap(pm);
    return pm;
  }

  // Normalize luminance of a Pixmap to [0..1] and write back as grayscale (keeps alpha)
  private static void normalizeHeightmap(Pixmap pm) {
    if (pm == null) return;
    int w = pm.getWidth(), h = pm.getHeight();
    if (w <= 0 || h <= 0) return;
    float min = 1f, max = 0f;
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        int p = pm.getPixel(x, y);
        float r = ((p >>> 24) & 0xff) / 255f;
        float g = ((p >>> 16) & 0xff) / 255f;
        float b = ((p >>> 8)  & 0xff) / 255f;
        float l = (r + g + b) / 3f;
        if (l < min) min = l;
        if (l > max) max = l;
      }
    }
    float range = Math.max(1e-6f, max - min);
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        int p = pm.getPixel(x, y);
        int a = (p & 0xff);
        float r = ((p >>> 24) & 0xff) / 255f;
        float g = ((p >>> 16) & 0xff) / 255f;
        float b = ((p >>> 8)  & 0xff) / 255f;
        float l = (r + g + b) / 3f;
        float ln = (l - min) / range;
        int gi = Math.max(0, Math.min(255, Math.round(ln * 255f)));
        int rgba = ((gi & 0xff) << 24) | ((gi & 0xff) << 16) | ((gi & 0xff) << 8) | (a & 0xff);
        pm.drawPixel(x, y, rgba);
      }
    }
  }

  private List<MountainStamp> buildStampsFromNodes(){
    List<MountainStamp> list = new ArrayList<>();
    for (Node n : nodes) {
      if (n.heightmap == null) continue;
      MountainStamp s = new MountainStamp();
      s.heightmap = n.heightmap; s.centerX = n.center.x; s.centerZ = n.center.z;
      s.halfW = n.halfW; s.halfH = n.halfH; s.rotationDeg = n.rotationDeg;
      s.intensity = n.intensity; s.edgeFalloff = n.edgeFalloff; s.inverted = n.inverted;
      list.add(s);
    }
    return list;
  }

  // Ray-plane (y=0) intersection
  private boolean groundHit(Camera cam, Vector3 out){
    com.badlogic.gdx.math.collision.Ray ray = ((com.badlogic.gdx.graphics.PerspectiveCamera)cam).getPickRay(Gdx.input.getX(), Gdx.input.getY());
    if (Math.abs(ray.direction.y) < 1e-5f) return false;
    float t = -ray.origin.y / ray.direction.y;
    if (t < 0f) return false;
    out.set(ray.origin).mulAdd(ray.direction, t);
    return true;
  }

  @Override public void onPointerDown(int button, boolean shift, boolean ctrl) {
    Camera cam = EditorContext.getCamera();
    if (cam == null) return;
    if (!groundHit(cam, mouseW)) return;
    Node hit = pickNode(mouseW.x, mouseW.z);
    if (hit != null) {
      select(hit);
      draggingNode = hit;
      float base = Math.max(hit.halfW, hit.halfH);
      float marginMeters = Math.max(0.5f, handleMarginPct * base);
      DragMode m = detectHandle(hit, mouseW.x, mouseW.z, marginMeters);
      dragMode = m;
      dragStartX = mouseW.x; dragStartZ = mouseW.z;
      // Snapshot initial state for stable resizing
      dragC0.set(hit.center); dragHalfW0 = hit.halfW; dragHalfH0 = hit.halfH; dragRot0 = hit.rotationDeg;
      float s0 = MathUtils.sinDeg(dragRot0), c0 = MathUtils.cosDeg(dragRot0);
      dragXw.set(c0, 0f, s0); dragZw.set(-s0, 0f, c0);
      switch (dragMode) {
        case RESIZE_L: dragAnchor.set(dragC0).mulAdd(dragXw, +dragHalfW0); break; // right edge fixed
        case RESIZE_R: dragAnchor.set(dragC0).mulAdd(dragXw, -dragHalfW0); break; // left edge fixed
        case RESIZE_B: dragAnchor.set(dragC0).mulAdd(dragZw, +dragHalfH0); break; // top edge fixed
        case RESIZE_T: dragAnchor.set(dragC0).mulAdd(dragZw, -dragHalfH0); break; // bottom edge fixed
        default: break;
      }
    } else {
      dragMode = DragMode.NONE;
      draggingNode = null;
      select(null);
      rebuildNodeList();
    }
  }

  @Override public void onPointerDrag(int button, boolean shift, boolean ctrl) {
    Camera cam = EditorContext.getCamera();
    if (cam == null || draggingNode == null) return;
    if (!groundHit(cam, mouseW)) return;
    float dx = mouseW.x - dragStartX;
    float dz = mouseW.z - dragStartZ;
    if (dragMode == DragMode.MOVE) {
      draggingNode.center.add(dx, 0f, dz);
      dragStartX = mouseW.x; dragStartZ = mouseW.z;
      rebuildNodeList();
    } else if (dragMode != DragMode.NONE) {
      // Stable local deltas in the snapshot frame
      float lx = dragXw.x * dx + dragXw.z * dz; // projection on Xw
      float lz = dragZw.x * dx + dragZw.z * dz; // projection on Zw
      switch (dragMode) {
        case RESIZE_L: {
          float newHalf = Math.max(1f, dragHalfW0 - lx * 0.5f);
          draggingNode.halfW = newHalf;
          draggingNode.center.set(dragAnchor).mulAdd(dragXw, -newHalf);
          break;
        }
        case RESIZE_R: {
          float newHalf = Math.max(1f, dragHalfW0 + lx * 0.5f);
          draggingNode.halfW = newHalf;
          draggingNode.center.set(dragAnchor).mulAdd(dragXw, +newHalf);
          break;
        }
        case RESIZE_B: {
          float newHalf = Math.max(1f, dragHalfH0 - lz * 0.5f);
          draggingNode.halfH = newHalf;
          draggingNode.center.set(dragAnchor).mulAdd(dragZw, -newHalf);
          break;
        }
        case RESIZE_T: {
          float newHalf = Math.max(1f, dragHalfH0 + lz * 0.5f);
          draggingNode.halfH = newHalf;
          draggingNode.center.set(dragAnchor).mulAdd(dragZw, +newHalf);
          break;
        }
        default: break;
      }
      // do not update dragStart here; we want deltas from the original pointer-down for stability
      // Sync sliders
      updatingUI = true;
      try {
        if (scaleXSlider != null) scaleXSlider.setValue(draggingNode.halfW);
        if (scaleZSlider != null) scaleZSlider.setValue(draggingNode.halfH);
        if (scaleXLabel != null) scaleXLabel.setText(String.format("Half-width X: %.1f m", draggingNode.halfW));
        if (scaleZLabel != null) scaleZLabel.setText(String.format("Half-height Z: %.1f m", draggingNode.halfH));
        rebuildNodeList();
      } finally { updatingUI = false; }
    }
  }

  @Override public void onPointerUp(int button, boolean shift, boolean ctrl) { if (dragMode != DragMode.NONE) com.example.game.World.Editor.EditorContext.requestRefresh("Updating mountains..."); dragMode = DragMode.NONE; draggingNode = null; Gdx.app.postRunnable(this::saveMountainsToMap); }

  private Node pickNode(float x, float z){
    for (int i = nodes.size()-1; i>=0; i--) {
      Node n = nodes.get(i);
      float base = Math.max(n.halfW, n.halfH);
      float marginMeters = Math.max(0.5f, handleMarginPct * base);
      if (pointInRect(n, x, z, marginMeters)) return n;
    }
    return null;
  }

  private DragMode detectHandle(Node n, float x, float z, float margin){
    // Transform to local
    float s = MathUtils.sinDeg(n.rotationDeg), c = MathUtils.cosDeg(n.rotationDeg);
    float lx = x - n.center.x, lz = z - n.center.z;
    float rx =  c*lx + s*lz;
    float rz = -s*lx + c*lz;
    float dx = Math.abs(Math.abs(rx) - n.halfW);
    float dz = Math.abs(Math.abs(rz) - n.halfH);
    boolean nearX = dx <= margin && Math.abs(rz) <= n.halfH + margin;
    boolean nearZ = dz <= margin && Math.abs(rx) <= n.halfW + margin;
    if (nearX) return rx < 0 ? DragMode.RESIZE_L : DragMode.RESIZE_R;
    if (nearZ) return rz < 0 ? DragMode.RESIZE_B : DragMode.RESIZE_T;
    // inside -> move
    if (Math.abs(rx) <= n.halfW && Math.abs(rz) <= n.halfH) return DragMode.MOVE;
    return DragMode.NONE;
  }

  private boolean pointInRect(Node n, float x, float z, float margin){
    float s = MathUtils.sinDeg(n.rotationDeg), c = MathUtils.cosDeg(n.rotationDeg);
    float lx = x - n.center.x, lz = z - n.center.z;
    float rx =  c*lx + s*lz;
    float rz = -s*lx + c*lz;
    return Math.abs(rx) <= n.halfW + margin && Math.abs(rz) <= n.halfH + margin;
  }

  @Override public void update(float dt, Camera cam) {}

  @Override public void render3D(Camera cam) {
    if (nodes.isEmpty()) return;

    Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    Gdx.gl.glDepthMask(false);
    Gdx.gl.glDisable(GL20.GL_CULL_FACE);
    Gdx.gl.glEnable(GL20.GL_BLEND);
    Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

    if (shader != null) {
      shader.bind();
      shader.setUniformMatrix("uProjView", cam.combined);
      shader.setUniformf("uAlpha", 0.35f);
    }

    for (Node n : nodes) {
      // Textured fill (if available)
      if (shader != null && n.preview != null) {
        n.preview.bind(0);
        shader.setUniformi("uTex", 0);
      }

      float cx = n.center.x, cz = n.center.z;
      float hw = n.halfW, hh = n.halfH;
      float s = MathUtils.sinDeg(n.rotationDeg), c = MathUtils.cosDeg(n.rotationDeg);
      // corners in local space
      float[] lx = { -hw,  hw,  hw, -hw };
      float[] lz = { -hh, -hh,  hh,  hh };
      float[] wx = new float[4];
      float[] wz = new float[4];
      for (int i=0;i<4;i++){
        float rx =  lx[i]*c - lz[i]*s;
        float rz =  lx[i]*s + lz[i]*c;
        wx[i] = cx + rx;
        wz[i] = cz + rz;
      }
      // draw fill as two triangles if texture available
      if (shader != null && n.preview != null) {
        float[] verts = new float[] {
            wx[0], 0f, wz[0], 0f, 0f,
            wx[1], 0f, wz[1], 1f, 0f,
            wx[2], 0f, wz[2], 1f, 1f,
            wx[3], 0f, wz[3], 0f, 1f
        };
        short[] idx = new short[] {0,1,2, 0,2,3};
        com.badlogic.gdx.graphics.Mesh mesh = new com.badlogic.gdx.graphics.Mesh(true, 4, 6,
            new com.badlogic.gdx.graphics.VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.Position, 3, "a_pos"),
            new com.badlogic.gdx.graphics.VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.TextureCoordinates, 2, "a_uv"));
        mesh.setVertices(verts); mesh.setIndices(idx);
        mesh.render(shader, GL20.GL_TRIANGLES);
        mesh.dispose();
      }

      // Edge highlighting like other tools
      float base = Math.max(hw, hh);
      float marginMeters = Math.max(0.5f, handleMarginPct * base);
      DragMode hover = DragMode.NONE;
      if (groundHit(cam, mouseW)) {
        hover = detectHandle(n, mouseW.x, mouseW.z, marginMeters);
      }
      float br = (n == selected ? 1.0f : 0.2f);
      float bg = (n == selected ? 0.9f : 0.8f);
      float bb = (n == selected ? 0.2f : 1.0f);
      float ba = (n == selected ? 1f : 0.6f);
      float hr = 0.2f, hg = 0.95f, hb = 1f, ha = 1f;
      Gdx.gl.glLineWidth(n == selected ? 3f : 2f);
      imLines.begin(cam.combined, GL20.GL_LINES);
      // bottom (0-1)
      if (hover == DragMode.RESIZE_B && n == selected) imLines.color(hr,hg,hb,ha); else imLines.color(br,bg,bb,ba);
      imLines.vertex(wx[0], 0f, wz[0]);
      if (hover == DragMode.RESIZE_B && n == selected) imLines.color(hr,hg,hb,ha); else imLines.color(br,bg,bb,ba);
      imLines.vertex(wx[1], 0f, wz[1]);
      // right (1-2)
      if (hover == DragMode.RESIZE_R && n == selected) imLines.color(hr,hg,hb,ha); else imLines.color(br,bg,bb,ba);
      imLines.vertex(wx[1], 0f, wz[1]);
      if (hover == DragMode.RESIZE_R && n == selected) imLines.color(hr,hg,hb,ha); else imLines.color(br,bg,bb,ba);
      imLines.vertex(wx[2], 0f, wz[2]);
      // top (2-3)
      if (hover == DragMode.RESIZE_T && n == selected) imLines.color(hr,hg,hb,ha); else imLines.color(br,bg,bb,ba);
      imLines.vertex(wx[2], 0f, wz[2]);
      if (hover == DragMode.RESIZE_T && n == selected) imLines.color(hr,hg,hb,ha); else imLines.color(br,bg,bb,ba);
      imLines.vertex(wx[3], 0f, wz[3]);
      // left (3-0)
      if (hover == DragMode.RESIZE_L && n == selected) imLines.color(hr,hg,hb,ha); else imLines.color(br,bg,bb,ba);
      imLines.vertex(wx[3], 0f, wz[3]);
      if (hover == DragMode.RESIZE_L && n == selected) imLines.color(hr,hg,hb,ha); else imLines.color(br,bg,bb,ba);
      imLines.vertex(wx[0], 0f, wz[0]);
      imLines.end();
    }

    Gdx.gl.glDisable(GL20.GL_BLEND);
    Gdx.gl.glDepthMask(true);
    Gdx.gl.glEnable(GL20.GL_CULL_FACE);
    Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
  }

  // ---------------- Persistence: serialize/deserialize mountains ----------------
  private void saveMountainsToMap() {
    if (terrain == null) return;
    StringBuilder sb = new StringBuilder(1024);
    sb.append('{').append("\"mountains\":[");
    for (int i = 0; i < nodes.size(); i++) {
      Node n = nodes.get(i);
      if (i > 0) sb.append(',');
      String imgPath = "mountains/images/mountain_" + i + ".png";
      if (n.heightmap != null) {
        try {
          java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(1<<16);
          com.badlogic.gdx.graphics.PixmapIO.PNG encoder = new com.badlogic.gdx.graphics.PixmapIO.PNG();
          encoder.setFlipY(false);
          encoder.write(bos, n.heightmap);
          encoder.dispose();
          terrain.getMap().stageExtra(imgPath, bos.toByteArray());
        } catch (Throwable ignored) {}
      }
      String nm = (n.imageName == null ? "" : n.imageName.replace("\"", "\\\""));
      sb.append('{')
        .append("\"image\":\"").append(imgPath).append('\"')
        .append(",\"name\":\"").append(nm).append('\"')
        .append(",\"cx\":").append(n.center.x)
        .append(",\"cz\":").append(n.center.z)
        .append(",\"halfW\":").append(n.halfW)
        .append(",\"halfH\":").append(n.halfH)
        .append(",\"rot\":").append(n.rotationDeg)
        .append(",\"intensity\":").append(n.intensity)
        .append(",\"falloff\":").append(n.edgeFalloff)
        .append(",\"inverted\":").append(n.inverted)
        .append('}');
    }
    sb.append("]}");
    byte[] bytes = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    terrain.getMap().stageExtra("mountains/mountains.json", bytes);
  }

  private void loadMountainsFromMap() {
    if (terrain == null) return;
    byte[] data = terrain.getMap().readEntry("mountains/mountains.json");
    if (data == null || data.length == 0) return;
    String json = new String(data, java.nio.charset.StandardCharsets.UTF_8);
    com.badlogic.gdx.utils.JsonValue root = new com.badlogic.gdx.utils.JsonReader().parse(json);
    com.badlogic.gdx.utils.JsonValue arr = root.get("mountains");
    if (arr == null) return;
    nodes.clear();
    for (com.badlogic.gdx.utils.JsonValue e : arr) {
      String img = e.getString("image", null);
      String name = e.getString("name", "");
      float cx = e.getFloat("cx", 0f);
      float cz = e.getFloat("cz", 0f);
      float halfW = e.getFloat("halfW", 50f);
      float halfH = e.getFloat("halfH", 50f);
      float rot = e.getFloat("rot", 0f);
      float intensity = e.getFloat("intensity", 20f);
      float falloff = e.getFloat("falloff", 0.5f);
      boolean inverted = e.getBoolean("inverted", false);
      Pixmap pm = null;
      if (img != null) {
        byte[] ib = terrain.getMap().readEntry(img);
        if (ib != null && ib.length > 0) pm = new Pixmap(ib, 0, ib.length);
      }
      Node n = new Node();
      n.center.set(cx, 0f, cz);
      n.halfW = halfW; n.halfH = halfH; n.rotationDeg = rot;
      n.intensity = intensity; n.edgeFalloff = falloff; n.inverted = inverted;
      n.heightmap = pm; n.preview = (pm != null ? new Texture(pm) : null);
      n.imageName = name;
      nodes.add(n);
    }
    if (!nodes.isEmpty()) select(nodes.get(0));
  }

  // Public helper: ensure nodes are loaded from the map once (for EditorScreen initial bake)
  public void ensureLoadedFromMap() {
    if (nodes.isEmpty()) {
      try { loadMountainsFromMap(); rebuildNodeList(); } catch (Throwable ignored) {}
    }
  }

  // Public: stage current mountains (JSON+images) into MapResource for upcoming save
  public void stageMountainsForSave() {
    try { saveMountainsToMap(); } catch (Throwable ignored) {}
  }

  private static final class Node {
    final Vector3 center = new Vector3();
    float halfW, halfH;
    float rotationDeg;
    float intensity, edgeFalloff;
    Pixmap heightmap; Texture preview;
    String imageName;
    boolean inverted;

    Node cloneNode(){
      Node n = new Node();
      n.center.set(center);
      n.halfW = halfW; n.halfH = halfH; n.rotationDeg = rotationDeg;
      n.intensity = intensity; n.edgeFalloff = edgeFalloff; n.inverted = inverted;
      n.heightmap = heightmap; n.preview = preview; // share preview for now
      n.imageName = imageName;
      return n;
    }
  }
}
