package com.example.game.Render.Passes;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.example.game.Render.SimpleShader;
import com.example.game.Render.SimpleSolidShader;
import com.example.game.Render.Pipeline.RenderContext;
import com.example.game.Render.Pipeline.RenderPass;
import com.example.game.World.TerrainSystem;

public final class TerrainPass implements RenderPass {
  private final TerrainSystem terrain;
  private final SimpleShader shader = new SimpleShader();
  private final SimpleSolidShader solid = new SimpleSolidShader();

  public TerrainPass(TerrainSystem terrain) { this.terrain = terrain; }

  public void init() {
    shader.init();
    shader.setColor(new Color(0.52f, 0.75f, 0.45f, 1f));
    solid.init();
  }

  @Override public void render(RenderContext ctx) {
    shader.bind();
    shader.setProjectionView(ctx.cam.combined);
    shader.setSunDirection(-ctx.sunDir.x, -ctx.sunDir.y, -ctx.sunDir.z);
    shader.applyUniforms();
    terrain.render(shader, ctx.cam);
  }

  /** Black occluders for god-rays in the currently bound target. */
  public void renderSolidBlack(RenderContext ctx) {
    SimpleShader proxy = new SimpleShader() {
      @Override public void bind() { solid.bind(ctx.cam.combined); }
      @Override public ShaderProgram getProgram() { return solid.getProgram(); }
      @Override public void applyUniforms() { /* none */ }
    };
    terrain.render(proxy, ctx.cam);
  }

  public void setViewRadius(int r) { terrain.setViewRadius(r); }

  @Override public void dispose() {
    shader.dispose();
    solid.dispose();
  }
}
