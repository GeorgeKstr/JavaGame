package com.example.game.Render.Pipeline;

import com.badlogic.gdx.utils.Array;

public final class RenderPipeline implements AutoCloseable {
  private final Array<RenderPass> passes = new Array<>();

  public RenderPipeline add(RenderPass pass) { passes.add(pass); return this; }

  public void update(float dt, RenderContext ctx) {
    for (RenderPass p : passes) p.update(dt, ctx);
  }

  public void render(RenderContext ctx) {
    for (RenderPass p : passes) p.render(ctx);
  }

  public void resize(int w, int h) { for (RenderPass p : passes) p.resize(w, h); }

  @Override public void close() { for (RenderPass p : passes) p.dispose(); }
}
