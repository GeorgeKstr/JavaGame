package com.example.game.World;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.IntMap;
import com.example.game.Render.SimpleShader;

final class TerrainRenderer {
  private final TerrainWorld w;
  private final LodController lod;

  TerrainRenderer(TerrainWorld w, LodController lod){
    this.w = w; this.lod = lod;
  }

  void render(SimpleShader shader, Camera cam) {
    shader.bind();
    shader.setProjectionView(cam.combined);
    shader.applyUniforms();

    if (w.groundTA != null) {
      Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
      w.groundTA.bind();
      var sp = shader.getProgram();
      sp.setUniformi("u_groundTex", 0);
      sp.setUniform1fv("u_uvScales", w.uvScales, 0, w.uvScales.length);
      sp.setUniformf("u_uvWorldScale", 1f / w.tile);
    }

    float camX = cam.position.x, camZ = cam.position.z;
    for (IntMap.Entry<TerrainChunk> e : w.chunks) {
      TerrainChunk c = e.value;
      if (!c.hasLods(w.MAX_LOD)) c.buildLods(w.MAX_LOD);
      int sel = lod.selectLod(camX, camZ, c);
      c.renderLod(shader, sel);
      c.renderLodSkirt(shader, sel);
    }
  }

  void dispose() { /* nothing yet; textures disposed in TerrainWorld */ }
}
