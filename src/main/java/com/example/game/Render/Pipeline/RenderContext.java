package com.example.game.Render.Pipeline;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Vector3;

public final class RenderContext {
  public final PerspectiveCamera cam;
  public final Vector3 sunDir;   // world-space, normalized
  public float elapsedSeconds;

  public RenderContext(PerspectiveCamera cam, Vector3 sunDir) {
    this.cam = cam;
    this.sunDir = sunDir;
  }
}
