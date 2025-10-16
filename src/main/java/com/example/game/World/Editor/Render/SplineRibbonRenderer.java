package com.example.game.World.Editor.Render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.example.game.World.Editor.Layers.SplinesLayer;
import com.example.game.World.Splines.TerrainSpline;
import com.example.game.World.TerrainSystem;

public final class SplineRibbonRenderer implements Renderable {
  private final SplinesLayer layer;
  private final TerrainSystem terrain;
  private final ImmediateModeRenderer20 im = new ImmediateModeRenderer20(65536, false, true, 1);
  private final ImmediateModeRenderer20 imSolid = new ImmediateModeRenderer20(65536, false, true, 0);
  private ShaderProgram waterShader;
  private Mesh ribbonMesh;
  private float timeAccum = 0f;
  private final java.util.HashMap<Integer, Texture> texCache = new java.util.HashMap<>();

  public SplineRibbonRenderer(SplinesLayer layer, TerrainSystem terrain) {
    this.layer = layer;
    this.terrain = terrain;
    // Compile water shader from classpath files (same as SplinesLayer had)
    ShaderProgram.pedantic = false;
    String vert = Gdx.files.classpath("shaders/water.vert.glsl").readString();
    String frag = Gdx.files.classpath("shaders/water.frag.glsl").readString();
    waterShader = new ShaderProgram(vert, frag);
    if (!waterShader.isCompiled()) {
      Gdx.app.error("SplineRibbonRenderer", "Water shader compile error: "+waterShader.getLog());
    }
    ribbonMesh = new Mesh(true, 15000, 0,
      new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
      new VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 4, "a_color"),
      new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "a_texCoord0")
    );
  }

  @Override
  public void render(Camera cam, RenderMode mode) {
    Gdx.gl.glEnable(GL20.GL_BLEND);
    Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    Gdx.gl.glDisable(GL20.GL_CULL_FACE);

    java.util.List<TerrainSpline> iter;
    synchronized (layer) { // safety; layer.getSplines already snapshots but we batch per-frame
      iter = new java.util.ArrayList<>(layer.getSplines());
    }

    for (TerrainSpline s : iter) {
      // Mode filter
      if (mode == RenderMode.TOOL_RIVER && s.getType() != TerrainSpline.Type.RIVER) continue;
      if (mode == RenderMode.TOOL_ROAD && s.getType() != TerrainSpline.Type.ROAD) continue;
      // Non-textured roads policy:
      // - In SPLINES_LAYER (background), skip non-textured road ribbons
      // - In TOOL_ROAD, render only non-textured road ribbons (textured ones are already in background)
      if (s.getType() == TerrainSpline.Type.ROAD) {
        if (mode == RenderMode.SPLINES_LAYER && !s.textured) continue;
        if (mode == RenderMode.TOOL_ROAD && s.textured) continue;
      }

      if (s.isRibbonDirty() && !s.isRibbonRebuilding()) {
        s.rebuildRibbonAsync(this.terrain, 2);
      }
      if (!s.hasRibbon()) continue;

      if (s.getType() == TerrainSpline.Type.RIVER) {
        renderRiver(cam, s);
      } else if (s.getType() == TerrainSpline.Type.ROAD) {
        renderRoad(cam, s);
      }
    }

    Gdx.gl.glDisable(GL20.GL_BLEND);
  }

  private void renderRiver(Camera cam, TerrainSpline s) {
    float aa = Math.max(0f, Math.min(1f, s.transparency));
    float[] sunTint = computeSunTint(); float rr = sunTint[0], gg = sunTint[1], bb = sunTint[2];
    float[] lx = s.getLX(), ly = s.getLY(), lz = s.getLZ();
    float[] rx = s.getRX(), ry = s.getRY(), rz = s.getRZ();
    if (lx == null || ly == null || lz == null || rx == null || ry == null || rz == null) return;
    int nFromTri = s.getTriPairs() + 1;
    int len = Math.min(Math.min(lx.length, rx.length), Math.min(ly.length, ry.length));
    len = Math.min(len, Math.min(lz.length, rz.length));
    int n = Math.min(nFromTri, len);
    if (n < 2) return;
    float yLift = s.heightOffset + 0.02f;
    float ratio = Math.max(0.01f, s.ribbonWidthRatio);

    int i = 0; final int maxSegmentsPerBatch = 2500; float cumV = 0f;
    timeAccum += Gdx.graphics.getDeltaTime();
    while (i < n - 1) {
      int end = Math.min(n - 1, i + maxSegmentsPerBatch);
      float vLen = 0f;
      int segs = end - i; int vertsCount = segs * 6; int stride = 9; float[] verts = new float[vertsCount * stride]; int idx = 0;
      for (int k = i; k < end; k++) {
        float clx0 = 0.5f * (lx[k] + rx[k]); float clz0 = 0.5f * (lz[k] + rz[k]);
        float clx1 = 0.5f * (lx[k+1] + rx[k+1]); float clz1 = 0.5f * (lz[k+1] + rz[k+1]);
        float ax = clx0, az = clz0; float bx = clx1, bz = clz1;
        float du = (float)Math.sqrt((bx-ax)*(bx-ax) + (bz-az)*(bz-az)); float v2 = vLen + du / Math.max(0.1f, s.width);
        float nlx0 = ax + (lx[k]   - ax) * ratio; float nlz0 = az + (lz[k]   - az) * ratio;
        float nrx0 = ax + (rx[k]   - ax) * ratio; float nrz0 = az + (rz[k]   - az) * ratio;
        float nlx1 = bx + (lx[k+1] - bx) * ratio; float nlz1 = bz + (lz[k+1] - bz) * ratio;
        float nrx1 = bx + (rx[k+1] - bx) * ratio; float nrz1 = bz + (rz[k+1] - bz) * ratio;
        idx = putVert(verts, idx, nlx0, ly[k]   +yLift, nlz0, rr,gg,bb,aa, 0f, cumV + vLen);
        idx = putVert(verts, idx, nrx0, ry[k]   +yLift, nrz0, rr,gg,bb,aa, 1f, cumV + vLen);
        idx = putVert(verts, idx, nlx1, ly[k+1] +yLift, nlz1, rr,gg,bb,aa, 0f, cumV + v2);
        idx = putVert(verts, idx, nrx0, ry[k]   +yLift, nrz0, rr,gg,bb,aa, 1f, cumV + vLen);
        idx = putVert(verts, idx, nrx1, ry[k+1] +yLift, nrz1, rr,gg,bb,aa, 1f, cumV + v2);
        idx = putVert(verts, idx, nlx1, ly[k+1] +yLift, nlz1, rr,gg,bb,aa, 0f, cumV + v2);
        vLen = v2;
      }
      if (waterShader != null && waterShader.isCompiled()) {
        ribbonMesh.setVertices(verts, 0, verts.length);
        waterShader.bind();
        waterShader.setUniformMatrix("u_projTrans", cam.combined);
        waterShader.setUniformf("u_time", timeAccum);
        // camera position
        waterShader.setUniformf("u_camPos", cam.position);
        // sun direction and colors
        com.badlogic.gdx.math.Vector3 sd = computeSunDir();
        waterShader.setUniformf("u_sunDir", sd);
        float[] sky = computeSunTint();
        waterShader.setUniformf("u_skyColor", sky[0], sky[1], sky[2]);
        waterShader.setUniformf("u_sunColor", 1.0f, 0.95f, 0.85f);
        waterShader.setUniformf("u_reverseFlow", s.reverseFlow ? 1f : 0f);
        ribbonMesh.render(waterShader, GL20.GL_TRIANGLES);
      }
      i = end; cumV += vLen;
    }
  }

  private void renderRoad(Camera cam, TerrainSpline s) {
    float aa = Math.max(0f, Math.min(1f, s.transparency));
    Texture tex = (s.textured ? getSplineTexture(s.textureId) : null);
    float rr, gg, bb;
    if (tex != null) {
      float[] sunTint = computeSunTint(); rr = sunTint[0]; gg = sunTint[1]; bb = sunTint[2];
    } else { rr = 0.08f; gg = 0.50f; bb = 1.00f; }

    float[] lx = s.getLX(), ly = s.getLY(), lz = s.getLZ();
    float[] rx = s.getRX(), ry = s.getRY(), rz = s.getRZ();
    int n = s.getTriPairs() + 1; float yLift = s.heightOffset + 0.02f; float ratio = Math.max(0.01f, s.ribbonWidthRatio);

    int i = 0; final int maxSegmentsPerBatch = 2500; float cumV = 0f;
    while (i < n - 1) {
      int end = Math.min(n - 1, i + maxSegmentsPerBatch); float vLen = 0f;
      if (tex != null) {
        im.begin(cam.combined, GL20.GL_TRIANGLES);
        for (int k = i; k < end; k++) {
          float clx0 = 0.5f * (lx[k] + rx[k]); float clz0 = 0.5f * (lz[k] + rz[k]);
          float clx1 = 0.5f * (lx[k+1] + rx[k+1]); float clz1 = 0.5f * (lz[k+1] + rz[k+1]);
          float ax = clx0, az = clz0; float bx = clx1, bz = clz1; float du = (float)Math.sqrt((bx-ax)*(bx-ax) + (bz-az)*(bz-az));
          float v2 = vLen + du / Math.max(0.1f, s.width);
          tex.bind();
          float nlx0 = ax + (lx[k]   - ax) * ratio; float nlz0 = az + (lz[k]   - az) * ratio;
          float nrx0 = ax + (rx[k]   - ax) * ratio; float nrz0 = az + (rz[k]   - az) * ratio;
          float nlx1 = bx + (lx[k+1] - bx) * ratio; float nlz1 = bz + (lz[k+1] - bz) * ratio;
          float nrx1 = bx + (rx[k+1] - bx) * ratio; float nrz1 = bz + (rz[k+1] - bz) * ratio;
          im.texCoord(0f, cumV + vLen); im.color(rr,gg,bb,aa); im.vertex(nlx0, ly[k]   +yLift, nlz0);
          im.texCoord(1f, cumV + vLen); im.color(rr,gg,bb,aa); im.vertex(nrx0, ry[k]   +yLift, nrz0);
          im.texCoord(0f, cumV + v2);   im.color(rr,gg,bb,aa); im.vertex(nlx1, ly[k+1] +yLift, nlz1);
          im.texCoord(1f, cumV + vLen); im.color(rr,gg,bb,aa); im.vertex(nrx0, ry[k]   +yLift, nrz0);
          im.texCoord(1f, cumV + v2);   im.color(rr,gg,bb,aa); im.vertex(nrx1, ry[k+1] +yLift, nrz1);
          im.texCoord(0f, cumV + v2);   im.color(rr,gg,bb,aa); im.vertex(nlx1, ly[k+1] +yLift, nlz1);
          vLen = v2;
        }
        im.end();
      } else {
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, 0);
        imSolid.begin(cam.combined, GL20.GL_TRIANGLES);
        for (int k = i; k < end; k++) {
          float clx0 = 0.5f * (lx[k] + rx[k]); float clz0 = 0.5f * (lz[k] + rz[k]);
          float clx1 = 0.5f * (lx[k+1] + rx[k+1]); float clz1 = 0.5f * (lz[k+1] + rz[k+1]);
          float ax = clx0, az = clz0; float bx = clx1, bz = clz1; float du = (float)Math.sqrt((bx-ax)*(bx-ax) + (bz-az)*(bz-az));
          float v2 = vLen + du / Math.max(0.1f, s.width);
          float nlx0 = ax + (lx[k]   - ax) * ratio; float nlz0 = az + (lz[k]   - az) * ratio;
          float nrx0 = ax + (rx[k]   - ax) * ratio; float nrz0 = az + (rz[k]   - az) * ratio;
          float nlx1 = bx + (lx[k+1] - bx) * ratio; float nlz1 = bz + (lz[k+1] - bz) * ratio;
          float nrx1 = bx + (rx[k+1] - bx) * ratio; float nrz1 = bz + (rz[k+1] - bz) * ratio;
          imSolid.color(rr,gg,bb,aa); imSolid.vertex(nlx0, ly[k]   +yLift, nlz0);
          imSolid.color(rr,gg,bb,aa); imSolid.vertex(nrx0, ry[k]   +yLift, nrz0);
          imSolid.color(rr,gg,bb,aa); imSolid.vertex(nlx1, ly[k+1] +yLift, nlz1);
          imSolid.color(rr,gg,bb,aa); imSolid.vertex(nrx0, ry[k]   +yLift, nrz0);
          imSolid.color(rr,gg,bb,aa); imSolid.vertex(nrx1, ry[k+1] +yLift, nrz1);
          imSolid.color(rr,gg,bb,aa); imSolid.vertex(nlx1, ly[k+1] +yLift, nlz1);
          vLen = v2;
        }
        imSolid.end();
      }
      i = end; cumV += vLen;
    }
  }

  private Texture getSplineTexture(int id) {
    if (id < 0) return null;
    Texture cached = texCache.get(id);
    if (cached != null) return cached;
    var map = terrain.getMap();
    var list = map.getManifest().groundTextures;
    if (id >= list.size()) return null;
    String path = list.get(id).file;
    byte[] bytes = map.getTextureBytes(path);
    if (bytes == null || bytes.length == 0) return null;
    Texture t = new Texture(new com.badlogic.gdx.graphics.Pixmap(bytes, 0, bytes.length));
    t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
    t.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
    texCache.put(id, t);
    return t;
  }

  private static int putVert(float[] arr, int idx, float x, float y, float z,
                              float r, float g, float b, float a,
                              float u, float v) {
    arr[idx++] = x; arr[idx++] = y; arr[idx++] = z;
    arr[idx++] = r; arr[idx++] = g; arr[idx++] = b; arr[idx++] = a;
    arr[idx++] = u; arr[idx++] = v;
    return idx;
  }

  private float[] computeSunTint() {
    float hour = com.example.game.Config.cvar.CVars.RENDER_TIME_OF_DAY.get();
    float t = (hour % 24f) / 24f;
    float az = (float)(t * Math.PI * 2.0);
    float maxElevDeg = 75f;
    float maxElevRad = (float)(Math.toRadians(maxElevDeg));
    float elev = (float)(maxElevRad * Math.sin(az - Math.PI * 0.5));
    float sinE = (float)Math.sin(elev);
    float sunY = sinE;
    float intensity = Math.max(0f, sunY);
    intensity = 0.2f + 0.8f * intensity;
    float warmth = com.example.game.Config.cvar.CVars.RENDER_SKY_WARMTH.get();
    float coolR=0.60f, coolG=0.70f, coolB=0.80f;
    float warmR=0.95f, warmG=0.88f, warmB=0.72f;
    float r = coolR + (warmR - coolR) * warmth;
    float g = coolG + (warmG - coolG) * warmth;
    float b = coolB + (warmB - coolB) * warmth;
    r *= intensity; g *= intensity; b *= intensity;
    return new float[]{ r, g, b };
  }

  private com.badlogic.gdx.math.Vector3 computeSunDir() {
    float hour = com.example.game.Config.cvar.CVars.RENDER_TIME_OF_DAY.get();
    float t = (hour % 24f) / 24f;
    float az = (float)(t * Math.PI * 2.0);
    float maxElevDeg = 75f;
    float maxElevRad = (float)(Math.toRadians(maxElevDeg));
    float elev = (float)(maxElevRad * Math.sin(az - Math.PI * 0.5));
    float ce = (float)Math.cos(elev);
    float se = (float)Math.sin(elev);
    float sa = (float)Math.sin(az);
    float ca = (float)Math.cos(az);
    com.badlogic.gdx.math.Vector3 dir = new com.badlogic.gdx.math.Vector3(ce * sa, se, ce * ca);
    return dir.nor();
  }
}
