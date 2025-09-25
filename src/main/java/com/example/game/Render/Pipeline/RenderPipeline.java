package com.example.game.Render.Pipeline;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple render pipeline that runs a list of passes in order.
 * Supports fluent .add(...), update/render/resize/close, and lookup via get(Class).
 */
public final class RenderPipeline {

  private final List<RenderPass> passes = new ArrayList<>();

  /** Add a pass (in order) and return this for chaining. */
  public RenderPipeline add(RenderPass pass) {
    if (pass != null) passes.add(pass);
    return this;
  }

  /** Get the first pass that is an instance of the given class (or null if absent). */
  public <T extends RenderPass> T get(Class<T> type) {
    for (RenderPass p : passes) {
      if (type.isInstance(p)) return type.cast(p);
    }
    return null;
  }

  /** Optional strict version if you prefer exceptions: */
  public <T extends RenderPass> T getOrThrow(Class<T> type) {
    T found = get(type);
    if (found == null) {
      throw new IllegalStateException("RenderPipeline: no pass of type " + type.getName() + " found.");
    }
    return found;
  }

  /** Per-frame updates before rendering (if your passes need it). */
  public void update(float dt, RenderContext ctx) {
    for (RenderPass p : passes) p.update(dt, ctx);
  }

  /** Render all passes in order. */
  public void render(RenderContext ctx) {
    for (RenderPass p : passes) p.render(ctx);
  }

  /** Notify passes about backbuffer size changes. */
  public void resize(int width, int height) {
    for (RenderPass p : passes) p.resize(width, height);
  }

  /** Dispose/close all passes in reverse order (last added, first disposed). */
  public void close() {
    for (int i = passes.size() - 1; i >= 0; i--) {
      try {
        passes.get(i).dispose();
      } catch (Exception ignored) {}
    }
    passes.clear();
  }
}
