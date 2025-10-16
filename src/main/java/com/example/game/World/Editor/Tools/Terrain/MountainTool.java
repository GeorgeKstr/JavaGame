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

  public MountainTool(TerrainSystem terrain) {
    this.terrain = terrain;
    // Stage latest mountains on chunk changes (so a following save writes them)
    this.terrain.addChunksChangedListener(() -> { try { saveMountainsToMap(); } catch (Throwable ignored) {} });
  }

  // Apply the currently selected mountain into baseline HMXS across all affected chunks, save, and remove node
  private void makeSelectedPermanent() {
    if (terrain == null || selected == null || selected.heightmap == null) return;
    var map = terrain.getMap();
    var mf = map.getManifest();
    final int S = mf.samplesPerSide;
    final float tile = mf.tile;
    final float yScale = terrain.getYScale();

    Node n = selected;
    float cxw = n.center.x, czw = n.center.z;
    float radius = (float)Math.hypot(n.halfW, n.halfH);
    int cpc = S - 1;
    float chunkSize = cpc * tile;
    int minCx = (int)Math.floor((cxw - radius) / chunkSize);
    int maxCx = (int)Math.floor((cxw + radius) / chunkSize);
    int minCz = (int)Math.floor((czw - radius) / chunkSize);
    int maxCz = (int)Math.floor((czw + radius) / chunkSize);

    java.util.Map<String, byte[]> toWrite = new java.util.LinkedHashMap<>();

    // Precompute rotation and inverses
    float sin = (float)Math.sin(Math.toRadians(n.rotationDeg));
    float cos = (float)Math.cos(Math.toRadians(n.rotationDeg));
    float invHalfW = 1f / Math.max(1e-6f, n.halfW);
    float invHalfH = 1f / Math.max(1e-6f, n.halfH);

    for (int cz = minCz; cz <= maxCz; cz++) {
      for (int cx = minCx; cx <= maxCx; cx++) {
        boolean listed = false;
        for (int[] c : mf.chunks) { if (c != null && c.length >= 2 && c[0]==cx && c[1]==cz) { listed = true; break; } }
        if (!listed) mf.chunks.add(new int[]{cx, cz});
        // Load existing HMXS or create blank
        com.example.game.World.SparseChunkProvider.LoadedChunk lc;
        byte[] raw = map.readChunkHmx(cx, cz);
        if (raw != null && raw.length > 0) {
          lc = new com.example.game.World.SparseChunkProvider("", S).loadFromBytes(raw);
        } else {
          // create empty arrays
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

        // Iterate overlap region inside this chunk
        int x0 = Math.max(0, (int)Math.floor((cxw - radius - originX) / tile));
        int x1 = Math.min(S - 1, (int)Math.ceil((cxw + radius - originX) / tile));
        int z0 = Math.max(0, (int)Math.floor((czw - radius - originZ) / tile));
        int z1 = Math.min(S - 1, (int)Math.ceil((czw + radius - originZ) / tile));

        for (int gz = z0; gz <= z1; gz++) {
          float wz = originZ + gz * tile;
          for (int gx = x0; gx <= x1; gx++) {
            // 4x4 supersampling within the cell to further reduce banding
            float wx = originX + gx * tile;
            float deltaMetersAvg = 0f;
            for (int sy = 0; sy < 4; sy++) {
              for (int sx = 0; sx < 4; sx++) {
                float fx = (sx + 0.5f) / 4f; // 0.125, 0.375, 0.625, 0.875
                float fz = (sy + 0.5f) / 4f;
                float ox = fx * tile;
                float oz = fz * tile;
                float px = wx + ox;
                float pz = wz + oz;
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
                float t = Math.max(ax, az);
                float bandStart = 1f - ff;
                float denom = Math.max(1e-6f, 1f - bandStart);
                float s2 = Math.max(0f, Math.min(1f, (t - bandStart) / denom));
                float shapedEdge = 1f - (s2*s2*(3f - 2f*s2));
                deltaMetersAvg += n.intensity * lum * shapedEdge;
              }
            }
            deltaMetersAvg *= (1f / 16f); // average of 16 sub-samples
            if (selected != null && selected.inverted) deltaMetersAvg = -deltaMetersAvg;
            int idx = gz * S + gx;
            int deltaUnits = Math.round(deltaMetersAvg / Math.max(1e-6f, yScale));
            int nv = heights[idx] + deltaUnits;
            if (nv < Short.MIN_VALUE) nv = Short.MIN_VALUE; else if (nv > Short.MAX_VALUE) nv = Short.MAX_VALUE;
            heights[idx] = (short)nv;
          }
        }

        try {
          byte[] blob = com.example.game.World.SparseChunkWriter.encodeToBytes(S, heights, id0, id1, id2, w0, w1, w2);
          toWrite.put(cx + "_" + cz + ".hmx", blob);
        } catch (Exception ex) {
          // fail this chunk but continue others
        }
      }
    }

    // Remove node first and persist mountains.json so the deletion is included in this delta save
    int old = selectedIndex; nodes.remove(selected); selected = null; selectedIndex = Math.min(old, nodes.size()-1); if (selectedIndex >= 0) selected = nodes.get(selectedIndex);
    rebuildNodeList(); selLabel.setText(selected==null?"Selected: none":String.format("Selected: %s (%.1f, %.1f)",(selected.imageName==null?"-":selected.imageName), selected.center.x, selected.center.z));
    saveMountainsToMap();

    // Now delta-save chunks + staged extras (updated mountains.json)
    if (!toWrite.isEmpty()) {
      map.saveInPlaceDeltaIncludingStaged(toWrite);
    } else {
      // still write mountains.json removal even if no chunk bytes changed
      map.saveInPlaceDeltaIncludingStaged(java.util.Collections.emptyMap());
    }

    // Refresh terrain so baked chunks update in view
    terrain.reloadAllChunks();
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

  @Override public String name() { return "Heightmap"; }

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
    // Install refresh-bake post-processor once; supplier reflects current mountains list
    if (!postInstalled) {
      terrain.addChunkHeightPostProcessor(new TerrainSystem.MountainBakePostProcessor(() -> buildStampsFromNodes()));
      postInstalled = true;
    }
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
    invertedCheck.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ if (updatingUI || selected == null) return; selected.inverted = invertedCheck.isChecked(); terrain.reloadAllChunks(); saveMountainsToMap(); }});
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
        if (lastImageDir != null) fc.setCurrentDirectory(new java.io.File(lastImageDir));
        if (fc.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
          abs[0] = fc.getSelectedFile().getAbsolutePath();
          java.io.File sel = fc.getSelectedFile();
          if (sel != null && sel.getParent() != null) lastImageDir = sel.getParent();
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
      // Resize along the selected edge in local space
      float s = MathUtils.sinDeg(draggingNode.rotationDeg), c = MathUtils.cosDeg(draggingNode.rotationDeg);
      // transform delta to local axes
      float lx =  c*dx + s*dz;
      float lz = -s*dx + c*dz;
      switch (dragMode) {
        case RESIZE_L: draggingNode.halfW = Math.max(1f, draggingNode.halfW - lx); draggingNode.center.mulAdd(new Vector3(-c,0f,-s), lx*0.5f); break;
        case RESIZE_R: draggingNode.halfW = Math.max(1f, draggingNode.halfW + lx); draggingNode.center.mulAdd(new Vector3( c,0f, s), lx*0.5f); break;
        case RESIZE_B: draggingNode.halfH = Math.max(1f, draggingNode.halfH - lz); draggingNode.center.mulAdd(new Vector3( s,0f,-c), lz*0.5f); break;
        case RESIZE_T: draggingNode.halfH = Math.max(1f, draggingNode.halfH + lz); draggingNode.center.mulAdd(new Vector3(-s,0f, c), lz*0.5f); break;
        default: break;
      }
      dragStartX = mouseW.x; dragStartZ = mouseW.z;
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

  @Override public void onPointerUp(int button, boolean shift, boolean ctrl) { if (dragMode != DragMode.NONE) terrain.reloadAllChunks(); dragMode = DragMode.NONE; draggingNode = null; saveMountainsToMap(); }

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

      float r = (n == selected ? 1.0f : 0.2f), g = (n == selected ? 0.9f : 0.8f), b = (n == selected ? 0.2f : 1.0f), a = (n == selected ? 1f : 0.2f);
      Gdx.gl.glLineWidth(n == selected ? 3f : 2f);
      imLines.begin(cam.combined, GL20.GL_LINES);
      imLines.color(r,g,b,a); imLines.vertex(wx[0], 0f, wz[0]);
      imLines.color(r,g,b,a); imLines.vertex(wx[1], 0f, wz[1]);
      imLines.color(r,g,b,a); imLines.vertex(wx[1], 0f, wz[1]);
      imLines.color(r,g,b,a); imLines.vertex(wx[2], 0f, wz[2]);
      imLines.color(r,g,b,a); imLines.vertex(wx[2], 0f, wz[2]);
      imLines.color(r,g,b,a); imLines.vertex(wx[3], 0f, wz[3]);
      imLines.color(r,g,b,a); imLines.vertex(wx[3], 0f, wz[3]);
      imLines.color(r,g,b,a); imLines.vertex(wx[0], 0f, wz[0]);
      float base = Math.max(hw, hh);
      float marginMeters = Math.max(0.5f, handleMarginPct * base);
      float iw = Math.max(0f, hw - marginMeters);
      float ih = Math.max(0f, hh - marginMeters);
      float[] ilx = new float[]{ -iw,  iw,  iw, -iw };
      float[] ilz = new float[]{ -ih, -ih,  ih,  ih };
      float[] ix = new float[4];
      float[] iz = new float[4];
      for (int i=0;i<4;i++){
        float rx2 =  ilx[i]*c - ilz[i]*s;
        float rz2 =  ilx[i]*s + ilz[i]*c;
        ix[i] = cx + rx2;
        iz[i] = cz + rz2;
      }
      float rr = 1.0f, gg = 1.0f, bb = 1.0f, aa = 0.25f;
      imLines.color(rr,gg,bb,aa); imLines.vertex(ix[0], 0f, iz[0]);
      imLines.color(rr,gg,bb,aa); imLines.vertex(ix[1], 0f, iz[1]);
      imLines.color(rr,gg,bb,aa); imLines.vertex(ix[1], 0f, iz[1]);
      imLines.color(rr,gg,bb,aa); imLines.vertex(ix[2], 0f, iz[2]);
      imLines.color(rr,gg,bb,aa); imLines.vertex(ix[2], 0f, iz[2]);
      imLines.color(rr,gg,bb,aa); imLines.vertex(ix[3], 0f, iz[3]);
      imLines.color(rr,gg,bb,aa); imLines.vertex(ix[3], 0f, iz[3]);
      imLines.color(rr,gg,bb,aa); imLines.vertex(ix[0], 0f, iz[0]);
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
