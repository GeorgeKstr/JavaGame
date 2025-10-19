package com.example.game.World;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20;
import com.badlogic.gdx.utils.IntMap;
import com.example.game.Render.SimpleShader;

final class TerrainRenderer {
  private final TerrainWorld w;
  private boolean wireframe = false;
  private boolean drawGrid = false;
  private final ImmediateModeRenderer20 im = new ImmediateModeRenderer20(false, true, 0);

  TerrainRenderer(TerrainWorld w){
    this.w = w;
  }

  void render(SimpleShader shader, Camera cam) {
    shader.bind();
    // Apply current non-destructive height modifier to mesh builders/samplers
    TerrainChunk.setGlobalModifier(w.heightModifier);

    // Build view with zero translation (don't mutate cam.view directly)
    com.badlogic.gdx.math.Matrix4 viewNoTrans = new com.badlogic.gdx.math.Matrix4(cam.view);
    viewNoTrans.setTranslation(0f, 0f, 0f);

    // proj * view(no translation)
    com.badlogic.gdx.math.Matrix4 projViewNoTrans =
        new com.badlogic.gdx.math.Matrix4(cam.projection).mul(viewNoTrans);

    // Feed the shader:
    // - u_projViewTrans  -> proj * view(no translation)
    // - u_cameraWorld    -> camera.position (world space)
    shader.setProjectionView(projViewNoTrans);                 // writes u_projViewTrans
    shader.getProgram().setUniformf("u_cameraWorld", cam.position);

    shader.applyUniforms();

    if (w.groundTA != null) {
      Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
      w.groundTA.bind();
      var sp = shader.getProgram();
      sp.setUniformi("u_groundTex", 0);
      sp.setUniform1fv("u_uvScales", w.uvScales, 0, w.uvScales.length);
      sp.setUniformf("u_uvWorldScale", 1f / w.tile);
    }

    boolean restoredPoly = false;
    // Desktop-only wireframe via LWJGL (safe no-op on other platforms)
    if (wireframe && Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Desktop) {
      try {
        org.lwjgl.opengl.GL11.glPolygonMode(org.lwjgl.opengl.GL11.GL_FRONT_AND_BACK, org.lwjgl.opengl.GL11.GL_LINE);
        restoredPoly = true;
      } catch (Throwable ignored) {}
    }

    float camX = cam.position.x, camZ = cam.position.z;
    // Cap expensive LOD mesh builds per frame to smooth spikes during streaming/refresh
    final int MAX_BUILDS_PER_FRAME = 4;
    int builds = 0;
    for (IntMap.Entry<TerrainChunk> e : w.chunks) {
      TerrainChunk c = e.value;
      // If painting changed CPU arrays, ensure LOD meshes are rebuilt; throttle to avoid hitches
      if (c.isDirty()) {
        if (builds < MAX_BUILDS_PER_FRAME) {
          c.buildLods(w.MAX_LOD);
          c.setDirty(false);
          w.defaultFlatChunks.remove(e.key);
          builds++;
        }
        // else: render with existing meshes this frame; will rebuild next frames
      }
      if (!c.hasLods(w.MAX_LOD)) {
        if (builds < MAX_BUILDS_PER_FRAME) {
          c.buildLods(w.MAX_LOD);
          builds++;
        } else {
          // No meshes yet and build budget spent: skip this chunk this frame to keep FPS stable
          continue;
        }
      }
      int sel = 0;
      c.renderLod(shader, sel);
      c.renderLodSkirt(shader, sel);
    }

    // Restore polygon mode
    if (restoredPoly) {
      try {
        org.lwjgl.opengl.GL11.glPolygonMode(org.lwjgl.opengl.GL11.GL_FRONT_AND_BACK, org.lwjgl.opengl.GL11.GL_FILL);
      } catch (Throwable ignored) {}
    }

    // Optional chunk grid overlay
    if (drawGrid) drawChunkGrid(cam);

    // Drain GL disposals after draws to push delete cost out of main render path
    TerrainChunk.processPendingDisposals(16);
  }

  void setWireframe(boolean on) { this.wireframe = on; }
  void setDrawGrid(boolean on) { this.drawGrid = on; }

  private void drawChunkGrid(Camera cam) {
    // Ensure grid overlays terrain: disable depth test and depth writes during grid draw
    Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    Gdx.gl.glDepthMask(false);

    // Attempt line smoothing on desktop (may be ignored on some drivers)
    boolean smoothing = false;
    if (Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Desktop) {
      try {
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_LINE_SMOOTH);
        org.lwjgl.opengl.GL11.glHint(org.lwjgl.opengl.GL11.GL_LINE_SMOOTH_HINT, org.lwjgl.opengl.GL11.GL_NICEST);
        smoothing = true;
      } catch (Throwable ignored) {}
    }

    Gdx.gl.glEnable(GL20.GL_BLEND);
    Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    // Modest line width where supported (desktop)
    boolean setWidth = false;
    if (Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Desktop) {
      try { org.lwjgl.opengl.GL11.glLineWidth(1.5f); setWidth = true; } catch (Throwable ignored) {}
    }
    im.begin(cam.combined, GL20.GL_LINES);
    float baseR = 0.1f, baseG = 0.8f, baseB = 1.0f;
    final float yOffset = 0.02f; // lift slightly to avoid z-fight
    // Fade farther based on camera far plane
    final float fadeStart = Math.max(100f, cam.far * 0.25f);
    final float fadeEnd   = Math.max(fadeStart + 1f, cam.far * 0.90f);
    for (IntMap.Entry<TerrainChunk> e : w.chunks) {
      TerrainChunk c = e.value;
      int S = c.getSize();
      float tile = c.getTile();
      float ox = c.getOriginX();
      float oz = c.getOriginZ();
      // sample heights at four corners
      short[] h = c.getHeightsArray();
      float ys = c.getYScale();
      float y00 = h[0] * ys + yOffset;
      float y10 = h[S - 1] * ys + yOffset;
      float y01 = h[(S - 1) * S] * ys + yOffset;
      float y11 = h[(S - 1) * S + (S - 1)] * ys + yOffset;
      float x0 = ox;
      float x1 = ox + (S - 1) * tile;
      float z0 = oz;
      float z1 = oz + (S - 1) * tile;

      // distance-based alpha fade
      float cx = (x0 + x1) * 0.5f;
      float cz = (z0 + z1) * 0.5f;
      float dx = cam.position.x - cx, dz = cam.position.z - cz;
      float dist = (float)Math.sqrt(dx*dx + dz*dz);
      float a = 0.9f;
      if (dist > fadeStart) {
        float t = (dist - fadeStart) / Math.max(1f, (fadeEnd - fadeStart));
        a *= Math.max(0f, 1f - t);
      }

      // lines: (x0,z0)->(x1,z0), (x1,z0)->(x1,z1), (x1,z1)->(x0,z1), (x0,z1)->(x0,z0)
      im.color(baseR, baseG, baseB, a); im.vertex(x0, y00, z0);
      im.color(baseR, baseG, baseB, a); im.vertex(x1, y10, z0);
      im.color(baseR, baseG, baseB, a); im.vertex(x1, y10, z0);
      im.color(baseR, baseG, baseB, a); im.vertex(x1, y11, z1);
      im.color(baseR, baseG, baseB, a); im.vertex(x1, y11, z1);
      im.color(baseR, baseG, baseB, a); im.vertex(x0, y01, z1);
      im.color(baseR, baseG, baseB, a); im.vertex(x0, y01, z1);
      im.color(baseR, baseG, baseB, a); im.vertex(x0, y00, z0);
    }
    im.end();
    Gdx.gl.glDisable(GL20.GL_BLEND);
    // Restore depth state
    Gdx.gl.glDepthMask(true);
    Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    if (setWidth) { try { org.lwjgl.opengl.GL11.glLineWidth(1.0f); } catch (Throwable ignored) {} }

    if (smoothing) {
      try { org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_LINE_SMOOTH); } catch (Throwable ignored) {}
    }
  }

  void dispose() { /* nothing yet; textures disposed in TerrainWorld */ }
}
