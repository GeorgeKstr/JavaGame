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
  private com.badlogic.gdx.scenes.scene2d.ui.List<String> nodeList;
  private TextButton deleteBtn;
  private boolean postInstalled = false;

  // Simple textured quad shader for preview
  private ShaderProgram shader;
  private final ImmediateModeRenderer20 imLines = new ImmediateModeRenderer20(2048, false, true, 0);

  // Interaction state
  private enum DragMode { NONE, MOVE, RESIZE_L, RESIZE_R, RESIZE_T, RESIZE_B }
  private DragMode dragMode = DragMode.NONE;
  private float dragStartX, dragStartZ;

  public MountainTool(TerrainSystem terrain) {
    this.terrain = terrain;
  }

  // Supplier used by centralized processor installation (EditorScreen)
  public java.util.function.Supplier<java.util.List<TerrainSystem.MountainStamp>> getStampsSupplier() {
    return this::buildStampsFromNodes;
  }

  @Override public String name() { return "Mountain"; }

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
  }

  @Override public void onDeselected() { }

  private void rebuildNodeList() {
    if (nodeList == null) return;
    com.badlogic.gdx.utils.Array<String> items = new com.badlogic.gdx.utils.Array<>();
    for (int i=0;i<nodes.size();i++) {
      Node n = nodes.get(i);
      items.add(String.format("%02d: (%.1f, %.1f) %.0f°", i, n.center.x, n.center.z, n.rotationDeg));
    }
    nodeList.setItems(items);
    if (selected != null) nodeList.setSelectedIndex(nodes.indexOf(selected));
  }

  @Override public void buildUI(Table container, Skin skin) {
    container.add(new Label("Mountain heightmap stamps", skin, "title")).left().row();
    Table row = new Table(); container.add(row).left().padTop(6).row();

    TextButton importBtn = new TextButton("Import Heightmap", skin);
    TextButton addNodeBtn = new TextButton("Add Node at Focus", skin);
    row.add(importBtn).padRight(8);
    row.add(addNodeBtn).padRight(8);

    // Node list
    nodeList = new com.badlogic.gdx.scenes.scene2d.ui.List<>(skin);
    nodeList.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
      int idx = nodeList.getSelectedIndex();
      if (idx >= 0 && idx < nodes.size()) select(nodes.get(idx));
    }});
    ScrollPane sp = new ScrollPane(nodeList, skin); sp.setFadeScrollBars(false);
    container.add(sp).width(220).height(140).left().padTop(8).row();

    selLabel = new Label("Selected: none", skin);
    container.add(selLabel).left().padTop(6).row();

    // Sliders
    Table t = new Table(); container.add(t).left().row();

    intensityLabel = new Label("Intensity: -", skin); intensitySlider = new Slider(0f, 2000f, 1f, false, skin);
    intensitySlider.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ if (selected==null) return; selected.intensity = intensitySlider.getValue(); intensityLabel.setText(String.format("Intensity: %.1f m", selected.intensity)); }});
    t.add(intensityLabel).left().row(); t.add(intensitySlider).width(260).row();

    falloffLabel = new Label("Edge falloff: -", skin); falloffSlider = new Slider(0f, 1f, 0.01f, false, skin);
    falloffSlider.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ if (selected==null) return; selected.edgeFalloff = falloffSlider.getValue(); falloffLabel.setText(String.format("Edge falloff: %.2f", selected.edgeFalloff)); }});
    t.add(falloffLabel).left().padTop(6).row(); t.add(falloffSlider).width(260).row();

    scaleXLabel = new Label("Half-width X: -", skin); scaleXSlider = new Slider(5f, 1500f, 1f, false, skin);
    scaleXSlider.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ if (selected==null) return; selected.halfW = scaleXSlider.getValue(); scaleXLabel.setText(String.format("Half-width X: %.1f m", selected.halfW)); }});
    t.add(scaleXLabel).left().padTop(6).row(); t.add(scaleXSlider).width(260).row();

    scaleZLabel = new Label("Half-height Z: -", skin); scaleZSlider = new Slider(5f, 1500f, 1f, false, skin);
    scaleZSlider.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ if (selected==null) return; selected.halfH = scaleZSlider.getValue(); scaleZLabel.setText(String.format("Half-height Z: %.1f m", selected.halfH)); }});
    t.add(scaleZLabel).left().padTop(6).row(); t.add(scaleZSlider).width(260).row();

    rotLabel = new Label("Rotation: -", skin); rotSlider = new Slider(0f, 360f, 1f, false, skin);
    rotSlider.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ if (selected==null) return; selected.rotationDeg = rotSlider.getValue(); rotLabel.setText(String.format("Rotation: %.0f°", selected.rotationDeg)); }});
    t.add(rotLabel).left().padTop(6).row(); t.add(rotSlider).width(260).row();

    // Delete
    deleteBtn = new TextButton("Delete Selected", skin);
    deleteBtn.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
      if (selected == null) return; nodes.remove(selected); selected = null; rebuildNodeList(); selLabel.setText("Selected: none");
    }});
    container.add(deleteBtn).left().padTop(8).row();

    // Buttons logic
    importBtn.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor) {
      Pixmap pm = fileDialogPixmap();
      if (pm == null) return;
      Node n = new Node();
      n.heightmap = pm; n.preview = new Texture(pm);
      n.intensity = 20f; n.edgeFalloff = 0.5f; n.halfW = 50f; n.halfH = 50f; n.rotationDeg = 0f;
      EditorContext.getFocusPosition(n.center);
      nodes.add(n); select(n); rebuildNodeList();
    }});

    addNodeBtn.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor) {
      if (nodes.isEmpty()) return;
      Node src = selected != null ? selected : nodes.get(nodes.size()-1);
      Node n = src.cloneNode();
      EditorContext.getFocusPosition(n.center);
      nodes.add(n); select(n); rebuildNodeList();
    }});

    

    // Initialize UI with current selection
    select(selected);
    rebuildNodeList();
  }

  private void select(Node n){
    selected = n;
    if (selLabel != null) selLabel.setText("Selected: "+ (n==null?"none":String.format("(%.1f, %.1f)", n.center.x, n.center.z)) );
    if (n != null) {
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
    }
  }

  private Pixmap fileDialogPixmap(){
    final String[] abs = { null };
    try {
      javax.swing.SwingUtilities.invokeAndWait(() -> {
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        fc.setDialogTitle("Import Heightmap");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Images", "png", "jpg", "jpeg", "bmp"));
        if (fc.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
          abs[0] = fc.getSelectedFile().getAbsolutePath();
        }
      });
    } catch (Exception ignored) {}
    if (abs[0] == null) return null;
    var fh = Gdx.files.absolute(abs[0]);
    if (!fh.exists()) return null;
    return new Pixmap(fh);
  }

  private List<MountainStamp> buildStampsFromNodes(){
    List<MountainStamp> list = new ArrayList<>();
    for (Node n : nodes) {
      if (n.heightmap == null) continue;
      MountainStamp s = new MountainStamp();
      s.heightmap = n.heightmap; s.centerX = n.center.x; s.centerZ = n.center.z;
      s.halfW = n.halfW; s.halfH = n.halfH; s.rotationDeg = n.rotationDeg;
      s.intensity = n.intensity; s.edgeFalloff = n.edgeFalloff;
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
    // Select closest node under cursor, prefer later nodes
    Node hit = pickNode(mouseW.x, mouseW.z);
    if (hit != null) {
      select(hit);
      // detect edge handles (margin in meters)
      DragMode m = detectHandle(hit, mouseW.x, mouseW.z, 1.5f);
      dragMode = m;
      dragStartX = mouseW.x; dragStartZ = mouseW.z;
    } else {
      dragMode = DragMode.NONE;
    }
  }

  @Override public void onPointerDrag(int button, boolean shift, boolean ctrl) {
    Camera cam = EditorContext.getCamera();
    if (cam == null || selected == null) return;
    if (!groundHit(cam, mouseW)) return;
    float dx = mouseW.x - dragStartX;
    float dz = mouseW.z - dragStartZ;
    if (dragMode == DragMode.MOVE) {
      selected.center.add(dx, 0f, dz);
      dragStartX = mouseW.x; dragStartZ = mouseW.z;
      rebuildNodeList();
    } else if (dragMode != DragMode.NONE) {
      // Resize along the selected edge in local space
      float s = MathUtils.sinDeg(selected.rotationDeg), c = MathUtils.cosDeg(selected.rotationDeg);
      // transform delta to local axes
      float lx =  c*dx + s*dz;
      float lz = -s*dx + c*dz;
      switch (dragMode) {
        case RESIZE_L: selected.halfW = Math.max(1f, selected.halfW - lx); selected.center.mulAdd(new Vector3(-c,0f,-s), lx*0.5f); break;
        case RESIZE_R: selected.halfW = Math.max(1f, selected.halfW + lx); selected.center.mulAdd(new Vector3( c,0f, s), lx*0.5f); break;
        case RESIZE_B: selected.halfH = Math.max(1f, selected.halfH - lz); selected.center.mulAdd(new Vector3( s,0f,-c), lz*0.5f); break;
        case RESIZE_T: selected.halfH = Math.max(1f, selected.halfH + lz); selected.center.mulAdd(new Vector3(-s,0f, c), lz*0.5f); break;
        default: break;
      }
      dragStartX = mouseW.x; dragStartZ = mouseW.z;
      // Sync sliders
      if (scaleXSlider != null) scaleXSlider.setValue(selected.halfW);
      if (scaleZSlider != null) scaleZSlider.setValue(selected.halfH);
      if (scaleXLabel != null) scaleXLabel.setText(String.format("Half-width X: %.1f m", selected.halfW));
      if (scaleZLabel != null) scaleZLabel.setText(String.format("Half-height Z: %.1f m", selected.halfH));
      rebuildNodeList();
    }
  }

  @Override public void onPointerUp(int button, boolean shift, boolean ctrl) { dragMode = DragMode.NONE; terrain.reloadAllChunks(); }

  private Node pickNode(float x, float z){
    for (int i = nodes.size()-1; i>=0; i--) {
      Node n = nodes.get(i);
      if (pointInRect(n, x, z, 0.0f)) return n;
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
      shader.setUniformf("uAlpha", 0.8f);
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

      // draw edge lines (always)
      float r = 0.2f, g = 0.8f, b = 1.0f, a = 0.9f;
      Gdx.gl.glLineWidth(2f);
      imLines.begin(cam.combined, GL20.GL_LINES);
      // 0-1, 1-2, 2-3, 3-0
      imLines.color(r,g,b,a); imLines.vertex(wx[0], 0f, wz[0]);
      imLines.color(r,g,b,a); imLines.vertex(wx[1], 0f, wz[1]);
      imLines.color(r,g,b,a); imLines.vertex(wx[1], 0f, wz[1]);
      imLines.color(r,g,b,a); imLines.vertex(wx[2], 0f, wz[2]);
      imLines.color(r,g,b,a); imLines.vertex(wx[2], 0f, wz[2]);
      imLines.color(r,g,b,a); imLines.vertex(wx[3], 0f, wz[3]);
      imLines.color(r,g,b,a); imLines.vertex(wx[3], 0f, wz[3]);
      imLines.color(r,g,b,a); imLines.vertex(wx[0], 0f, wz[0]);
      imLines.end();
    }

    Gdx.gl.glDisable(GL20.GL_BLEND);
    Gdx.gl.glDepthMask(true);
    Gdx.gl.glEnable(GL20.GL_CULL_FACE);
    Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
  }

  private static final class Node {
    final Vector3 center = new Vector3();
    float halfW, halfH;
    float rotationDeg;
    float intensity, edgeFalloff;
    Pixmap heightmap; Texture preview;

    Node cloneNode(){
      Node n = new Node();
      n.center.set(center);
      n.halfW = halfW; n.halfH = halfH; n.rotationDeg = rotationDeg;
      n.intensity = intensity; n.edgeFalloff = edgeFalloff;
      n.heightmap = heightmap; n.preview = preview; // share preview for now
      return n;
    }
  }
}
