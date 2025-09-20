package com.example.game.Render.Pipeline;

import com.badlogic.gdx.utils.Disposable;

public interface RenderPass extends Disposable {
  default void update(float dt, RenderContext ctx) {}
  void render(RenderContext ctx);
  default void resize(int w, int h) {}
}
