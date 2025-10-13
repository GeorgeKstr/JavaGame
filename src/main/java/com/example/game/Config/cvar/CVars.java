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

  // Rendering
  public static final CVar<Boolean> RENDER_VSYNC         = reg.defineBool("render.vsync", true);
  public static final CVar<Integer> RENDER_TARGET_FPS    = reg.defineInt("render.targetFps", 0);

  private CVars() {}
}
