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

  public float exposure = 0.5f; // default; HUD will change this

  public GodRaysPass(SkyPass skyPass, TerrainPass terrainPass, BirdsPass birdsPass) {
    this.skyPass = skyPass;
    this.terrainPass = terrainPass;
    this.birdsPass = birdsPass;
  }

  public void init(int w, int h) { godrays.init(w, h); }
  public void setExposure(float e) { exposure = e; }

  @Override public void render(RenderContext ctx) {
    // Fade strictly by camera facing the sun: zero when looking away
    float facing = ctx.cam.direction.dot(ctx.sunDir);
    float fade = smoothstep(0.10f, 0.45f, facing); // 0 below ~0.1, 1 above ~0.45
    // optional sharpening
    fade = fade * fade;

    godrays.updateLightScreenPos(ctx.cam, ctx.sunDir);
    godrays.beginOcclusion();
      // bright sky is already provided by the white clear in beginOcclusion(); do not overdraw with colored sky
      // black occluders: birds do not need depth; terrain occlusion respects depth
      com.badlogic.gdx.Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_DEPTH_TEST);
      birdsPass.renderOcclusion(ctx);
      com.badlogic.gdx.Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_DEPTH_TEST);
      com.badlogic.gdx.Gdx.gl.glDepthMask(true);
      terrainPass.renderSolidBlack(ctx);
      if (ctx.occlusionExtra != null) {
        try { ctx.occlusionExtra.run(); } catch (Throwable ignored) {}
      }
    godrays.endOcclusion(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

    // After using our own FBO for occlusion, rebind the scene FBO so we composite into it
    try {
      if (com.badlogic.gdx.Gdx.gl30 != null && ctx.sceneFboHandle != 0) {
        com.badlogic.gdx.Gdx.gl30.glBindFramebuffer(com.badlogic.gdx.graphics.GL30.GL_FRAMEBUFFER, ctx.sceneFboHandle);
      }
    } catch (Throwable ignored) {}

    // Composite rays additively into the scene
    float prev = godrays.exposure;
    godrays.exposure = Math.max(0.0f, exposure) * fade;
    godrays.compositeAdd(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    godrays.exposure = prev;
  }

  @Override public void resize(int w, int h) { godrays.resize(w, h); }
  @Override public void dispose() { godrays.dispose(); }

  // no debug helpers

  private static float smoothstep(float a, float b, float x) {
    float t = MathUtils.clamp((x - a) / (b - a), 0f, 1f);
    return t * t * (3f - 2f * t);
  }
}
