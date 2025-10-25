package com.example.game.Render.Pipeline;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Texture;
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
  // Scene color texture bound to the scene FBO
  public Texture sceneColorTex;
  // Eye adaptation exposure to apply during post (1 = no change)
  public float eyeExposure = 1f;
  // Pre-tonemap, center-weighted log-average luminance measured in HDRPrePass (0..1)
  public float hdrLuminance = 0f;
  // Robust histogram-based metering stats (pre-tonemap, 0..1 linear)
  public float hdrLuminanceRef = 0f;   // percentile Lref (e.g., 88th)
  public float hdrLuminanceP98 = 0f;   // 98th percentile luminance
  public float hdrBrightFrac = 0f;     // fraction of pixels above high threshold

  public RenderContext(PerspectiveCamera cam, Vector3 sunDir) {
    this.cam = cam;
    this.sunDir = sunDir;
  }
}
