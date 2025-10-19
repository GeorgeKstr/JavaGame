package com.example.game.World.Editor.Tools.Terrain;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.example.game.World.Editor.EditorContext;
import com.example.game.World.Editor.Tools.Tool;
import com.example.game.World.MapResource;
import com.example.game.World.SparseChunkProvider;
import com.example.game.World.SparseChunkWriter;
import com.example.game.World.TerrainSystem;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PaintHeightmapTool implements Tool {
  private final TerrainSystem terrain;
  private final ImmediateModeRenderer20 lines = new ImmediateModeRenderer20(2048, false, true, 0);

  private final Vector3 center = new Vector3();
  private float halfW = 50f, halfH = 50f, rotDeg = 0f;
  private float steepStartDeg = 35f, steepEndDeg = 55f;
  private float altThreshold = 0.6f, altWidth = 0.15f;

  private int flatTexId = 0, altTexId = 1, steepTexId = 2;
  private Label infoLabel, flatLbl, altLbl, steepLbl;
  private Slider wSlider, hSlider, rotSlider, steepStartSlider, steepEndSlider, altPercentSlider, altWidthSlider;

  public PaintHeightmapTool(TerrainSystem terrain) {
    this.terrain = terrain;
    EditorContext.getFocusPosition(center);
  }

  @Override public String name() { return "Paint"; }

  @Override public void onSelected() {
    com.example.game.World.Editor.EditorContext.getFocusPosition(center);
  }

  @Override public void buildUI(Table container, Skin skin) {
    container.add(new Label("Heightmap Paint (rect)", skin, "title")).left().row();

    infoLabel = new Label("Center: ("+fmt(center.x)+", "+fmt(center.z)+")", skin);
    container.add(infoLabel).left().padTop(6).row();

    Table dims = new Table(); container.add(dims).left().row();
    dims.add(new Label("Half-width X (m)", skin)).left().row();
    wSlider = new Slider(5f, 1500f, 1f, false, skin); wSlider.setValue(halfW);
    wSlider.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ halfW = wSlider.getValue(); }});
    dims.add(wSlider).width(260).row();
    dims.add(new Label("Half-height Z (m)", skin)).left().row();
    hSlider = new Slider(5f, 1500f, 1f, false, skin); hSlider.setValue(halfH);
    hSlider.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ halfH = hSlider.getValue(); }});
    dims.add(hSlider).width(260).row();
    dims.add(new Label("Rotation (deg)", skin)).left().row();
    rotSlider = new Slider(0f, 360f, 1f, false, skin); rotSlider.setValue(rotDeg);
    rotSlider.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ rotDeg = rotSlider.getValue(); }});
    dims.add(rotSlider).width(260).row();

    Table texSel = new Table(); container.add(texSel).left().padTop(6).row();
    flatLbl = new Label("Flat: "+flatTexId, skin);
    altLbl = new Label("Altitude: "+altTexId, skin);
    steepLbl = new Label("Steep: "+steepTexId, skin);
    TextButton flatBtn = new TextButton("Select Flat...", skin);
    flatBtn.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
      TextureBrowser(container, skin, "ground", id -> { flatTexId = id; flatLbl.setText("Flat: "+flatTexId); });
    }});
    TextButton altBtn = new TextButton("Select Altitude...", skin);
    altBtn.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
      TextureBrowser(container, skin, "ground", id -> { altTexId = id; altLbl.setText("Altitude: "+altTexId); });
    }});
    TextButton steepBtn = new TextButton("Select Steep...", skin);
    steepBtn.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
      TextureBrowser(container, skin, "ground", id -> { steepTexId = id; steepLbl.setText("Steep: "+steepTexId); });
    }});
    texSel.add(flatLbl).left().padRight(10);
    texSel.add(flatBtn).left().row();
    texSel.add(altLbl).left().padRight(10);
    texSel.add(altBtn).left().row();
    texSel.add(steepLbl).left().padRight(10);
    texSel.add(steepBtn).left().row();

    Table ctrl = new Table(); container.add(ctrl).left().padTop(6).row();
    ctrl.add(new Label("Steep start angle (deg)", skin)).left().row();
    steepStartSlider = new Slider(0f, 89f, 1f, false, skin); steepStartSlider.setValue(steepStartDeg);
    steepStartSlider.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ steepStartDeg = steepStartSlider.getValue(); }});
    ctrl.add(steepStartSlider).width(260).row();
    ctrl.add(new Label("Steep full angle (deg)", skin)).left().row();
    steepEndSlider = new Slider(1f, 89f, 1f, false, skin); steepEndSlider.setValue(steepEndDeg);
    steepEndSlider.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ steepEndDeg = steepEndSlider.getValue(); }});
    ctrl.add(steepEndSlider).width(260).row();
    ctrl.add(new Label("Altitude threshold (%)", skin)).left().row();
    altPercentSlider = new Slider(0f, 100f, 1f, false, skin); altPercentSlider.setValue(altThreshold * 100f);
    altPercentSlider.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ altThreshold = altPercentSlider.getValue() / 100f; }});
    ctrl.add(altPercentSlider).width(260).row();
    ctrl.add(new Label("Altitude blend width (%)", skin)).left().row();
    altWidthSlider = new Slider(1f, 50f, 1f, false, skin); altWidthSlider.setValue(altWidth * 100f);
    altWidthSlider.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ altWidth = altWidthSlider.getValue() / 100f; }});
    ctrl.add(altWidthSlider).width(260).row();

    TextButton apply = new TextButton("Apply Paint", skin);
    apply.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ applyPaint(); }});
    container.add(apply).left().padTop(8).row();
  }

  private interface IdConsumer { void accept(int id); }
  private void TextureBrowser(Table parent, Skin skin, String category, IdConsumer set) {
    var tb = new com.example.game.World.Editor.TextureBrowser(skin, terrain, category, tr -> {
      if (tr == null) return; set.accept(tr.id);
    });
    if (parent != null && parent.getStage() != null) tb.show(parent.getStage());
  }

  @Override public void update(float dt, Camera cam) {
    if (infoLabel != null) infoLabel.setText("Center: ("+fmt(center.x)+", "+fmt(center.z)+")");
  }

  @Override public void render3D(Camera cam) {
    float s = MathUtils.sinDeg(rotDeg), c = MathUtils.cosDeg(rotDeg);
    float[] lx = { -halfW,  halfW,  halfW, -halfW };
    float[] lz = { -halfH, -halfH,  halfH,  halfH };
    float[] wx = new float[4];
    float[] wz = new float[4];
    for (int i=0;i<4;i++){ float rx =  lx[i]*c - lz[i]*s; float rz =  lx[i]*s + lz[i]*c; wx[i] = center.x + rx; wz[i] = center.z + rz; }
    // Determine hover edge
    DragMode hover = DragMode.NONE;
    if (cam != null && groundHit(cam, tmpW)) {
      float base = Math.max(halfW, halfH);
      float margin = Math.max(0.5f, 0.12f * base);
      hover = detectHandle(tmpW.x, tmpW.z, margin);
    }
    com.badlogic.gdx.Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    com.badlogic.gdx.Gdx.gl.glDepthMask(false);
    com.badlogic.gdx.Gdx.gl.glEnable(GL20.GL_BLEND);
    com.badlogic.gdx.Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    lines.begin(cam.combined, GL20.GL_LINES);
    float br=0.3f,bg=0.9f,bb=0.4f,ba=1f;
    float hr=0.2f,hg=0.95f,hb=1f,ha=1f;
    if (hover == DragMode.RESIZE_B) { lines.color(hr,hg,hb,ha); } else { lines.color(br,bg,bb,ba); }
    lines.vertex(wx[0], 0f, wz[0]);
    if (hover == DragMode.RESIZE_B) { lines.color(hr,hg,hb,ha); } else { lines.color(br,bg,bb,ba); }
    lines.vertex(wx[1], 0f, wz[1]);
    if (hover == DragMode.RESIZE_R) { lines.color(hr,hg,hb,ha); } else { lines.color(br,bg,bb,ba); }
    lines.vertex(wx[1], 0f, wz[1]);
    if (hover == DragMode.RESIZE_R) { lines.color(hr,hg,hb,ha); } else { lines.color(br,bg,bb,ba); }
    lines.vertex(wx[2], 0f, wz[2]);
    if (hover == DragMode.RESIZE_T) { lines.color(hr,hg,hb,ha); } else { lines.color(br,bg,bb,ba); }
    lines.vertex(wx[2], 0f, wz[2]);
    if (hover == DragMode.RESIZE_T) { lines.color(hr,hg,hb,ha); } else { lines.color(br,bg,bb,ba); }
    lines.vertex(wx[3], 0f, wz[3]);
    if (hover == DragMode.RESIZE_L) { lines.color(hr,hg,hb,ha); } else { lines.color(br,bg,bb,ba); }
    lines.vertex(wx[3], 0f, wz[3]);
    if (hover == DragMode.RESIZE_L) { lines.color(hr,hg,hb,ha); } else { lines.color(br,bg,bb,ba); }
    lines.vertex(wx[0], 0f, wz[0]);
    lines.end();
    com.badlogic.gdx.Gdx.gl.glDisable(GL20.GL_BLEND);
    com.badlogic.gdx.Gdx.gl.glDepthMask(true);
    com.badlogic.gdx.Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
  }

  private final Vector3 tmpW = new Vector3();
  private enum DragMode { NONE, MOVE, RESIZE_L, RESIZE_R, RESIZE_T, RESIZE_B }
  private DragMode drag = DragMode.NONE; private float dragX, dragZ;

  @Override public void onPointerDown(int button, boolean shift, boolean ctrl) {
    Camera cam = EditorContext.getCamera(); if (cam == null) return; if (!groundHit(cam, tmpW)) return;
    float base = Math.max(halfW, halfH); float margin = Math.max(0.5f, 0.12f * base);
    DragMode m = detectHandle(tmpW.x, tmpW.z, margin); drag = m; dragX = tmpW.x; dragZ = tmpW.z;
  }
  @Override public void onPointerDrag(int button, boolean shift, boolean ctrl) {
    Camera cam = EditorContext.getCamera(); if (cam == null) return; if (!groundHit(cam, tmpW)) return;
    float dx = tmpW.x - dragX, dz = tmpW.z - dragZ;
    if (drag == DragMode.MOVE) { center.add(dx,0f,dz); dragX = tmpW.x; dragZ = tmpW.z; return; }
    if (drag != DragMode.NONE) {
      float s = MathUtils.sinDeg(rotDeg), c = MathUtils.cosDeg(rotDeg);
      float lx =  c*dx + s*dz; float lz = -s*dx + c*dz;
      switch(drag){
        case RESIZE_L: {
          Vector3 Xw = new Vector3(c,0f,s);
          Vector3 R = new Vector3(center).mulAdd(Xw, halfW);
          float newHalf = Math.max(1f, halfW - lx * 0.5f);
          halfW = newHalf; center.set(R).mulAdd(Xw, -newHalf);
          break;
        }
        case RESIZE_R: {
          Vector3 Xw = new Vector3(c,0f,s);
          Vector3 L = new Vector3(center).mulAdd(Xw, -halfW);
          float newHalf = Math.max(1f, halfW + lx * 0.5f);
          halfW = newHalf; center.set(L).mulAdd(Xw, newHalf);
          break;
        }
        case RESIZE_B: {
          Vector3 Zw = new Vector3(-s,0f,c);
          Vector3 T = new Vector3(center).mulAdd(Zw, halfH);
          float newHalf = Math.max(1f, halfH - lz * 0.5f);
          halfH = newHalf; center.set(T).mulAdd(Zw, -newHalf);
          break;
        }
        case RESIZE_T: {
          Vector3 Zw = new Vector3(-s,0f,c);
          Vector3 B = new Vector3(center).mulAdd(Zw, -halfH);
          float newHalf = Math.max(1f, halfH + lz * 0.5f);
          halfH = newHalf; center.set(B).mulAdd(Zw, newHalf);
          break;
        }
        case MOVE: /* no-op here, handled above */ break;
        case NONE: default: break;
      }
      dragX = tmpW.x; dragZ = tmpW.z; if (wSlider!=null) wSlider.setValue(halfW); if (hSlider!=null) hSlider.setValue(halfH);
    }
  }
  @Override public void onPointerUp(int button, boolean shift, boolean ctrl) { drag = DragMode.NONE; }

  private boolean groundHit(Camera cam, Vector3 out){
    com.badlogic.gdx.math.collision.Ray ray = ((com.badlogic.gdx.graphics.PerspectiveCamera)cam).getPickRay(com.badlogic.gdx.Gdx.input.getX(), com.badlogic.gdx.Gdx.input.getY());
    if (Math.abs(ray.direction.y) < 1e-5f) return false; float t = -ray.origin.y / ray.direction.y; if (t < 0f) return false; out.set(ray.origin).mulAdd(ray.direction, t); return true;
  }
  private DragMode detectHandle(float x, float z, float margin){
    float s = MathUtils.sinDeg(rotDeg), c = MathUtils.cosDeg(rotDeg); float lx = x - center.x, lz = z - center.z; float rx =  c*lx + s*lz; float rz = -s*lx + c*lz;
    float dx = Math.abs(Math.abs(rx) - halfW); float dz = Math.abs(Math.abs(rz) - halfH); boolean nearX = dx <= margin && Math.abs(rz) <= halfH + margin; boolean nearZ = dz <= margin && Math.abs(rx) <= halfW + margin;
    if (nearX) return rx < 0 ? DragMode.RESIZE_L : DragMode.RESIZE_R; if (nearZ) return rz < 0 ? DragMode.RESIZE_B : DragMode.RESIZE_T; if (Math.abs(rx) <= halfW && Math.abs(rz) <= halfH) return DragMode.MOVE; return DragMode.NONE;
  }

  private void applyPaint(){
    if (terrain == null) return;
    EditorContext.requestRefresh("Painting rectangle...");
    final float cx = center.x, cz = center.z; final float hw = Math.max(1f, halfW), hh = Math.max(1f, halfH); final float rot = rotDeg;
    final float steepA = Math.min(steepStartDeg, steepEndDeg), steepB = Math.max(steepStartDeg, steepEndDeg);
    final float altT = MathUtils.clamp(altThreshold, 0f, 1f), altW = MathUtils.clamp(altWidth, 0.01f, 1f);
    final int flatId = flatTexId, altId = altTexId, steepId = steepTexId;

    var map = terrain.getMap(); var mf = map.getManifest(); final int S = mf.samplesPerSide; final float tile = mf.tile; final float yScale = terrain.getYScale();
    int cpc = S - 1; final float chunkSize = cpc * tile;
    final int minCx = (int)Math.floor((cx - hw) / chunkSize); final int maxCx = (int)Math.floor((cx + hw) / chunkSize);
    final int minCz = (int)Math.floor((cz - hh) / chunkSize); final int maxCz = (int)Math.floor((cz + hh) / chunkSize);

    final float sin = MathUtils.sinDeg(rot), cos = MathUtils.cosDeg(rot);

    new Thread(() -> {
      try {
        float minH = Float.POSITIVE_INFINITY, maxH = Float.NEGATIVE_INFINITY;
        Map<String, SparseChunkProvider.LoadedChunk> chunks = new LinkedHashMap<>();
        for (int czI=minCz; czI<=maxCz; czI++) for (int cxI=minCx; cxI<=maxCx; cxI++) {
          byte[] raw = map.readChunkHmx(cxI, czI);
          SparseChunkProvider.LoadedChunk lc;
          if (raw != null && raw.length > 0) lc = new SparseChunkProvider("", S).loadFromBytes(raw);
          else lc = new SparseChunkProvider.LoadedChunk(new short[S*S], new byte[S*S], new byte[S*S], new byte[S*S], new byte[S*S], new byte[S*S], new byte[S*S]);
          chunks.put(cxI+":"+czI, lc);
          float originX = cxI * chunkSize, originZ = czI * chunkSize;
          int x0 = Math.max(0, (int)Math.floor((cx - hw - originX) / tile)); int x1 = Math.min(S - 1, (int)Math.ceil((cx + hw - originX) / tile));
          int z0 = Math.max(0, (int)Math.floor((cz - hh - originZ) / tile)); int z1 = Math.min(S - 1, (int)Math.ceil((cz + hh - originZ) / tile));
          for (int gz=z0; gz<=z1; gz++) for (int gx=x0; gx<=x1; gx++) {
            float wx = originX + gx*tile, wz = originZ + gz*tile; float lx = wx - cx, lz = wz - cz; float rx =  lx * cos + lz * sin; float rz = -lx * sin + lz * cos; if (Math.abs(rx) > hw || Math.abs(rz) > hh) continue;
            float hM = lc.heights[gz*S+gx] * yScale; if (hM < minH) minH = hM; if (hM > maxH) maxH = hM;
          }
        }
        float denomH = Math.max(1e-6f, maxH - minH);

        Map<String, byte[]> toWrite = new LinkedHashMap<>();
        for (int czI=minCz; czI<=maxCz; czI++) for (int cxI=minCx; cxI<=maxCx; cxI++) {
          SparseChunkProvider.LoadedChunk lc = chunks.get(cxI+":"+czI);
          short[] heights = lc.heights; byte[] id0 = lc.id0, id1 = lc.id1, id2 = lc.id2, w0 = lc.w0, w1 = lc.w1, w2 = lc.w2;
          float originX = cxI * chunkSize, originZ = czI * chunkSize;
          int x0 = Math.max(0, (int)Math.floor((cx - hw - originX) / tile)); int x1 = Math.min(S - 1, (int)Math.ceil((cx + hw - originX) / tile));
          int z0 = Math.max(0, (int)Math.floor((cz - hh - originZ) / tile)); int z1 = Math.min(S - 1, (int)Math.ceil((cz + hh - originZ) / tile));
          for (int gz=z0; gz<=z1; gz++) for (int gx=x0; gx<=x1; gx++) {
            float wx = originX + gx*tile, wz = originZ + gz*tile; float lx = wx - cx, lz = wz - cz; float rx =  lx * cos + lz * sin; float rz = -lx * sin + lz * cos; if (Math.abs(rx) > hw || Math.abs(rz) > hh) continue;
            int idx = gz*S + gx;
            float hM = heights[idx] * yScale; float hn = MathUtils.clamp((hM - minH) / denomH, 0f, 1f);
            float hx = ((gx+1 < S ? heights[gz*S+(gx+1)] : heights[idx]) - (gx-1 >=0 ? heights[gz*S+(gx-1)] : heights[idx])) * yScale * 0.5f / tile;
            float hz = ((gz+1 < S ? heights[(gz+1)*S+gx] : heights[idx]) - (gz-1 >=0 ? heights[(gz-1)*S+gx] : heights[idx])) * yScale * 0.5f / tile;
            float slopeDeg = (float)Math.toDegrees(Math.atan(Math.sqrt(hx*hx + hz*hz)));
            float s = smoothstep(steepA, steepB, slopeDeg);
            float a = smoothstep(altT - 0.5f*altW, altT + 0.5f*altW, hn);
            float f = (1f - s) * (1f - a);
            float alt = (1f - s) * a;
            float stp = s;
            float sum = f + alt + stp; if (sum < 1e-6f) { f = 1f; alt = 0f; stp = 0f; sum = 1f; }
            f /= sum; alt /= sum; stp /= sum;
            id0[idx] = (byte)flatId; id1[idx] = (byte)altId; id2[idx] = (byte)steepId;
            w0[idx] = (byte)Math.max(0, Math.min(255, Math.round(f * 255f)));
            w1[idx] = (byte)Math.max(0, Math.min(255, Math.round(alt * 255f)));
            w2[idx] = (byte)Math.max(0, Math.min(255, Math.round(stp * 255f)));
          }
          try { byte[] blob = SparseChunkWriter.encodeToBytes(S, heights, id0, id1, id2, w0, w1, w2); toWrite.put(cxI+"_"+czI+".hmx", blob);} catch (Exception ignored) {}
        }

        com.badlogic.gdx.Gdx.app.postRunnable(() -> {
          try { if (!toWrite.isEmpty()) terrain.getMap().saveInPlaceDeltaIncludingStaged(toWrite); } catch (Throwable ignored) {}
          EditorContext.requestRefresh("Rebuilding..."); terrain.reloadAllChunksAsyncInPlace();
        });
      } catch (Throwable ignored) {}
    }, "PaintHMTool").start();
  }

  private static float smoothstep(float e0, float e1, float x){ float t = MathUtils.clamp((x - e0) / Math.max(1e-6f, (e1 - e0)), 0f, 1f); return t*t*(3f - 2f*t); }
  private static String fmt(float v){ return String.format("%.1f", v); }
}
