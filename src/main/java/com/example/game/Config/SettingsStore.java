package com.example.game.Config;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

public class SettingsStore {
  private static final String PREFS = "game_settings";

  public static Settings load() {
    if (Gdx.app == null) return new Settings();

    Preferences p = Gdx.app.getPreferences(PREFS);
    Settings s = new Settings();
    s.invertY           = p.getBoolean("invertY", s.invertY);
    s.mouseSensitivity  = p.getFloat("mouseSensitivity", s.mouseSensitivity);
    s.panSpeedFactor    = p.getFloat("panSpeedFactor", s.panSpeedFactor);
    s.brushMaxRadius    = p.getInteger("brushMaxRadius", s.brushMaxRadius);
    s.brushAlwaysOnTop  = p.getBoolean("brushAlwaysOnTop", s.brushAlwaysOnTop);

    s.viewRadiusChunks  = p.getInteger("viewRadiusChunks", s.viewRadiusChunks);
    s.defaultTextureId  = p.getInteger("defaultTextureId", s.defaultTextureId);

    // New persisted editor-render values
    s.timeOfDayHour     = p.getFloat("timeOfDayHour", s.timeOfDayHour);
    s.sunSizeDeg        = p.getFloat("sunSizeDeg", s.sunSizeDeg);
    s.skyWarmth         = p.getFloat("skyWarmth", s.skyWarmth);
    s.godRaysExposure   = p.getFloat("godRaysExposure", s.godRaysExposure);

    s.vsync             = p.getBoolean("vsync", s.vsync);
    s.targetFps         = p.getInteger("targetFps", s.targetFps);
    return s;
  }

  public static void save(Settings s) {
    if (Gdx.app == null) return;
    Preferences p = Gdx.app.getPreferences(PREFS);
    p.putBoolean("invertY", s.invertY);
    p.putFloat  ("mouseSensitivity", s.mouseSensitivity);
    p.putFloat  ("panSpeedFactor", s.panSpeedFactor);
    p.putInteger("brushMaxRadius", s.brushMaxRadius);
    p.putBoolean("brushAlwaysOnTop", s.brushAlwaysOnTop);

    p.putInteger("viewRadiusChunks", s.viewRadiusChunks);
    p.putInteger("defaultTextureId", s.defaultTextureId);

    // New persisted editor-render values
    p.putFloat("timeOfDayHour", s.timeOfDayHour);
    p.putFloat("sunSizeDeg", s.sunSizeDeg);
    p.putFloat("skyWarmth", s.skyWarmth);
    p.putFloat("godRaysExposure", s.godRaysExposure);

    p.putBoolean("vsync", s.vsync);
    p.putInteger("targetFps", s.targetFps);
    p.flush();
  }
}
