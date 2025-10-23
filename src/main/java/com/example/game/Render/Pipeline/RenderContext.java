package com.example.game.Render.Pipeline;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Vector3;

public final class RenderContext {
  public final PerspectiveCamera cam;
  public final Vector3 sunDir;   // world-space, normalized
  public float elapsedSeconds;
  // Optional: extra occluders hook (e.g., outer LOD) for passes like GodRays
  public Runnable occlusionExtra;
  // Optional: extra scene draw hook to run BETWEEN Sky and Terrain (e.g., outer LOD)
  public Runnable sceneExtraBeforeTerrain;
  // Scene FBO handle currently bound by EditorScreen (for passes that need to restore binding)
  public int sceneFboHandle = 0;

  public RenderContext(PerspectiveCamera cam, Vector3 sunDir) {
    this.cam = cam;
    this.sunDir = sunDir;
  }
}
