package com.example.game.Render.Passes;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.example.game.Render.GodRaysRenderer;
import com.example.game.Render.Pipeline.RenderContext;
import com.example.game.Render.Pipeline.RenderPass;

public final class GodRaysPass implements RenderPass {
  private final GodRaysRenderer godrays = new GodRaysRenderer();
  private final SkyPass skyPass;
  private final TerrainPass terrainPass;
  private final BirdsPass birdsPass;

  public float exposure = 0.8f; // default; HUD will change this

  public GodRaysPass(SkyPass skyPass, TerrainPass terrainPass, BirdsPass birdsPass) {
    this.skyPass = skyPass;
    this.terrainPass = terrainPass;
    this.birdsPass = birdsPass;
  }

  public void init(int w, int h) { godrays.init(w, h); }
  public void setExposure(float e) { exposure = e; }

  @Override public void render(RenderContext ctx) {
    // fade with camera facing the sun
    float facing = ctx.cam.direction.dot(ctx.sunDir);
    float fade = smoothstep(0.0f, 0.18f, Math.max(0f, facing));
    if (fade <= 0.001f) return;

    godrays.updateLightScreenPos(ctx.cam, ctx.sunDir);
    godrays.beginOcclusion();
      // bright sky
      skyPass.render(ctx);
      // black occluders
      birdsPass.renderOcclusion(ctx);
      terrainPass.renderSolidBlack(ctx);
    godrays.endOcclusion(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

    float prev = godrays.exposure;
    godrays.exposure = prev * exposure * fade;
    godrays.compositeAdd(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    godrays.exposure = prev;
  }

  @Override public void resize(int w, int h) { godrays.resize(w, h); }
  @Override public void dispose() { godrays.dispose(); }

  private static float smoothstep(float a, float b, float x) {
    float t = MathUtils.clamp((x - a) / (b - a), 0f, 1f);
    return t * t * (3f - 2f * t);
  }
}
