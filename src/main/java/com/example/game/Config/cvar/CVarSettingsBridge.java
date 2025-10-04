package com.example.game.Config.cvar;

import com.example.game.Config.Settings;

/** Sync utilities between legacy Settings and the new CVars. */
public final class CVarSettingsBridge {
  private CVarSettingsBridge() {}

  /** Populate CVars from a Settings instance. */
  public static void fromSettings(Settings s) {
    if (s == null) return;
    CVars.EDITOR_INVERT_Y.set(s.invertY);
    CVars.EDITOR_MOUSE_SENS.set(s.mouseSensitivity);
    CVars.EDITOR_PAN_SPEED.set(s.panSpeedFactor);
    CVars.BRUSH_MAX_RADIUS.set(s.brushMaxRadius);
    CVars.BRUSH_ALWAYS_ON_TOP.set(s.brushAlwaysOnTop);
    CVars.EDITOR_MOVE_SPEED.set(s.editorMoveSpeed);

    CVars.VIEW_RADIUS_CHUNKS.set(s.viewRadiusChunks);
    CVars.DEFAULT_TEXTURE_ID.set(s.defaultTextureId);

    CVars.RENDER_TIME_OF_DAY.set(s.timeOfDayHour);
    CVars.RENDER_SUN_SIZE_DEG.set(s.sunSizeDeg);
    CVars.RENDER_SKY_WARMTH.set(s.skyWarmth);
    CVars.RENDER_GODRAYS_EXP.set(s.godRaysExposure);

    CVars.RENDER_VSYNC.set(s.vsync);
    CVars.RENDER_TARGET_FPS.set(s.targetFps);
  }

  /** Create a Settings instance from current CVars. */
  public static Settings toSettings() {
    Settings s = new Settings();
    s.invertY = CVars.EDITOR_INVERT_Y.get();
    s.mouseSensitivity = CVars.EDITOR_MOUSE_SENS.get();
    s.panSpeedFactor = CVars.EDITOR_PAN_SPEED.get();
    s.brushMaxRadius = CVars.BRUSH_MAX_RADIUS.get();
    s.brushAlwaysOnTop = CVars.BRUSH_ALWAYS_ON_TOP.get();
    s.editorMoveSpeed = CVars.EDITOR_MOVE_SPEED.get();

    s.viewRadiusChunks = CVars.VIEW_RADIUS_CHUNKS.get();
    s.defaultTextureId = CVars.DEFAULT_TEXTURE_ID.get();

    s.timeOfDayHour = CVars.RENDER_TIME_OF_DAY.get();
    s.sunSizeDeg = CVars.RENDER_SUN_SIZE_DEG.get();
    s.skyWarmth = CVars.RENDER_SKY_WARMTH.get();
    s.godRaysExposure = CVars.RENDER_GODRAYS_EXP.get();

    s.vsync = CVars.RENDER_VSYNC.get();
    s.targetFps = CVars.RENDER_TARGET_FPS.get();
    return s;
  }
}
