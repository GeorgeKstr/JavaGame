package com.example.game.Render.Passes;

import com.example.game.Render.Pipeline.RenderContext;
import com.example.game.Render.Pipeline.RenderPass;

/** A simple pass that invokes ctx.sceneExtraBeforeTerrain if set. */
public final class SceneExtraPass implements RenderPass {
  @Override public void render(RenderContext ctx) {
    if (ctx != null && ctx.sceneExtraBeforeTerrain != null) {
      ctx.sceneExtraBeforeTerrain.run();
    }
  }
  @Override public void resize(int w, int h) {}
  @Override public void dispose() {}
}
