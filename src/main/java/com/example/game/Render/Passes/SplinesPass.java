package com.example.game.Render.Passes;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.example.game.Render.Pipeline.RenderContext;
import com.example.game.Render.Pipeline.RenderPass;
import com.example.game.World.Editor.Layers.SplinesLayer;

/** Renders spline ribbons into the scene FBO so terrain depth occludes them. */
public final class SplinesPass implements RenderPass {
  private final SplinesLayer splines;
  public SplinesPass(SplinesLayer splines) { this.splines = splines; }

  @Override public void update(float dt, RenderContext ctx) { }

  @Override public void render(RenderContext ctx) {
    if (splines == null || ctx == null || ctx.cam == null) return;
    try { Gdx.gl.glEnable(GL20.GL_DEPTH_TEST); } catch (Throwable ignored) {}
    try { Gdx.gl.glDepthFunc(GL20.GL_LEQUAL); } catch (Throwable ignored) {}
    try { Gdx.gl.glDepthMask(false); } catch (Throwable ignored) {} // transparent ribbons should not write depth
    try { Gdx.gl.glEnable(GL20.GL_BLEND); } catch (Throwable ignored) {}
    try { Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA); } catch (Throwable ignored) {}
    try { Gdx.gl.glDisable(GL20.GL_CULL_FACE); } catch (Throwable ignored) {}
    // Draw ribbons only (no tool overlays) using the layer's renderer
    splines.renderRibbonsOnly(ctx.cam);
    try { Gdx.gl.glDisable(GL20.GL_BLEND); } catch (Throwable ignored) {}
    try { Gdx.gl.glDepthMask(true); } catch (Throwable ignored) {}
  }

  @Override public void resize(int w, int h) { }

  @Override public void dispose() { }
}
