package com.example.game.Render.Passes;

import com.example.game.Render.SkyRenderer;
import com.example.game.Render.Pipeline.RenderContext;
import com.example.game.Render.Pipeline.RenderPass;

public final class SkyPass implements RenderPass {
  private final SkyRenderer sky = new SkyRenderer();

  public void init() { sky.init(); }
  public SkyRenderer sky() { return sky; } // expose for other passes that need to re-draw sky

  @Override public void render(RenderContext ctx) {
    // Sky wants depth/cull disabled typically; leave to renderer if it manages state itself
    sky.render(ctx.cam, ctx.sunDir, ctx.elapsedSeconds);
  }

  public void setSunSizeDeg(float d) { sky.setSunSizeDeg(d); }
  public void setWarmth(float w)     { sky.setWarmth(w); }

  @Override public void resize(int w, int h) {}
  @Override public void dispose() { sky.dispose(); }
}
