package com.example.game.Config.cvar;

/** Global CVar registry and definitions. */
public final class CVars {
  public static final CVarRegistry reg = new CVarRegistry();

  // Editor / camera
  public static final CVar<Boolean> EDITOR_INVERT_Y      = reg.defineBool("editor.invertY", false);
  public static final CVar<Float>   EDITOR_MOUSE_SENS    = reg.defineFloat("editor.mouseSensitivity", 0.3f);
  public static final CVar<Float>   EDITOR_PAN_SPEED     = reg.defineFloat("editor.panSpeedFactor", 0.0025f);
  public static final CVar<Integer> BRUSH_MAX_RADIUS     = reg.defineInt("editor.brushMaxRadius", 64);
  public static final CVar<Boolean> BRUSH_ALWAYS_ON_TOP  = reg.defineBool("editor.brushAlwaysOnTop", false);
  public static final CVar<Float>   EDITOR_MOVE_SPEED    = reg.defineFloat("editor.editorMoveSpeed", 1000f);
  // Brush params (shared across brush tools)
  public static final CVar<Float>   BRUSH_RADIUS         = reg.defineFloat("editor.brush.radius", 6f);
  public static final CVar<Float>   BRUSH_STRENGTH       = reg.defineFloat("editor.brush.strength", 0.5f);
  public static final CVar<Float>   BRUSH_FALLOFF        = reg.defineFloat("editor.brush.falloff", 0.8f);

  // Editor camera (RTS) persistence
  public static final CVar<Float>   EDITOR_CAM_PIVOT_X   = reg.defineFloat("editor.cam.pivotX", 0f);
  public static final CVar<Float>   EDITOR_CAM_PIVOT_Y   = reg.defineFloat("editor.cam.pivotY", 0f);
  public static final CVar<Float>   EDITOR_CAM_PIVOT_Z   = reg.defineFloat("editor.cam.pivotZ", 0f);
  public static final CVar<Float>   EDITOR_CAM_YAW_DEG   = reg.defineFloat("editor.cam.yawDeg", 0f);
  public static final CVar<Float>   EDITOR_CAM_PITCH_DEG = reg.defineFloat("editor.cam.pitchDeg", -20f);
  public static final CVar<Float>   EDITOR_CAM_DISTANCE  = reg.defineFloat("editor.cam.distance", 30f);

  // FPS camera persistence
  public static final CVar<Float>   FPS_CAM_X            = reg.defineFloat("editor.fps.x", 0f);
  public static final CVar<Float>   FPS_CAM_Y            = reg.defineFloat("editor.fps.y", 1.7f);
  public static final CVar<Float>   FPS_CAM_Z            = reg.defineFloat("editor.fps.z", 0f);
  public static final CVar<Float>   FPS_CAM_YAW_DEG      = reg.defineFloat("editor.fps.yawDeg", 0f);
  public static final CVar<Float>   FPS_CAM_PITCH_DEG    = reg.defineFloat("editor.fps.pitchDeg", 0f);

  // Terrain / streaming
  public static final CVar<Integer> VIEW_RADIUS_CHUNKS   = reg.defineInt("terrain.viewRadiusChunks", 3);
  public static final CVar<Integer> DEFAULT_TEXTURE_ID   = reg.defineInt("terrain.defaultTextureId", 0);

  // Rendering / editor-adjustables
  public static final CVar<Float>   RENDER_TIME_OF_DAY   = reg.defineFloat("render.timeOfDayHour", 12f);
  public static final CVar<Float>   RENDER_SUN_SIZE_DEG  = reg.defineFloat("render.sunSizeDeg", 1.5f);
  public static final CVar<Float>   RENDER_SKY_WARMTH    = reg.defineFloat("render.skyWarmth", 0.5f);
  public static final CVar<Float>   RENDER_GODRAYS_EXP   = reg.defineFloat("render.godRaysExposure", 3f);
  public static final CVar<Boolean> RENDER_TERRAIN_WIREFRAME = reg.defineBool("render.terrainWireframe", false);
  public static final CVar<Boolean> RENDER_TERRAIN_CHUNK_GRID = reg.defineBool("render.terrainChunkGrid", false);
  public static final CVar<Boolean> RENDER_SHOW_OUTER_LOD = reg.defineBool("render.showOuterLod", true);

  // HDR tonemap controls
  public static final CVar<Float>   HDR_EXPOSURE         = reg.defineFloat("render.hdr.exposure", 0.9f);
  public static final CVar<Float>   HDR_SATURATION       = reg.defineFloat("render.hdr.saturation", 1.08f);
  public static final CVar<Float>   HDR_CONTRAST         = reg.defineFloat("render.hdr.contrast", 1.06f);

  // Post AA (FXAA) controls
  public static final CVar<Boolean> AA_FXAA_ENABLED      = reg.defineBool("render.aa.fxaa.enabled", true);
  public static final CVar<Float>   AA_FXAA_SUBPIX       = reg.defineFloat("render.aa.fxaa.subpix", 0.75f);
  public static final CVar<Float>   AA_FXAA_EDGE_THRESH  = reg.defineFloat("render.aa.fxaa.edgeThreshold", 0.166f);
  public static final CVar<Float>   AA_FXAA_EDGE_THRESH_MIN = reg.defineFloat("render.aa.fxaa.edgeThresholdMin", 0.0833f);
  public static final CVar<Float>   AA_FXAA_STRENGTH     = reg.defineFloat("render.aa.fxaa.strength", 0.6f);

  // Eye adaptation controls
  public static final CVar<Float>   EYE_KEY              = reg.defineFloat("render.eye.key", 0.18f);
  public static final CVar<Float>   EYE_MIN_EXP          = reg.defineFloat("render.eye.minExp", 0.80f);
  public static final CVar<Float>   EYE_MAX_EXP          = reg.defineFloat("render.eye.maxExp", 1.60f);
  public static final CVar<Float>   EYE_RATE_UP          = reg.defineFloat("render.eye.rateUp", 7.0f);
  public static final CVar<Float>   EYE_RATE_DOWN        = reg.defineFloat("render.eye.rateDown", 1.0f);
  public static final CVar<Float>   EYE_LUM_RATE         = reg.defineFloat("render.eye.lumRate", 2.0f);
  public static final CVar<Float>   EYE_MAX_STEP         = reg.defineFloat("render.eye.maxStep", 0.08f);

  // Rendering
  public static final CVar<Boolean> RENDER_VSYNC         = reg.defineBool("render.vsync", true);
  public static final CVar<Integer> RENDER_TARGET_FPS    = reg.defineInt("render.targetFps", 0);

  private CVars() {}
}
