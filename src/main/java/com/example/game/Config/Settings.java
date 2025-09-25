package com.example.game.Config;

public class Settings {
  // --- Editor UI / camera ---
  public boolean invertY = false;
  public float mouseSensitivity = 0.3f;     // orbit speed
  public float panSpeedFactor  = 0.0025f;   // world units per px * distance
  public int   brushMaxRadius  = 64;        // clamp
  public boolean brushAlwaysOnTop = true;   // draw over terrain
  public float editorMoveSpeed = 1000f;     // units per second

  // --- Terrain / streaming ---
  public int   viewRadiusChunks = 3;        // ±radius
  public int   defaultTextureId = 0;

  // --- Rendering / editor-adjustables ---
  /** Persist last time-of-day used in the editor (0..24). */
  public float timeOfDayHour = 12f;
  /** Sky sun disk size in degrees (matches SkyPass.setSunSizeDeg). */
  public float sunSizeDeg = 1.5f;
  /** Sky warmth/tint (0..1, matches SkyPass.setWarmth). */
  public float skyWarmth = 0.5f;
  /** God rays exposure (UI slider value). */
  public float godRaysExposure = 3f;

  // --- Rendering ---
  public boolean vsync = true;
  public int targetFps = 0;                 // 0 = uncapped

  // --- Utility ---
  public Settings copy() {
    Settings s = new Settings();
    s.invertY = invertY;
    s.mouseSensitivity = mouseSensitivity;
    s.panSpeedFactor = panSpeedFactor;
    s.brushMaxRadius = brushMaxRadius;
    s.brushAlwaysOnTop = brushAlwaysOnTop;

    s.viewRadiusChunks = viewRadiusChunks;
    s.defaultTextureId = defaultTextureId;

    s.timeOfDayHour = timeOfDayHour;
    s.sunSizeDeg = sunSizeDeg;
    s.skyWarmth = skyWarmth;
    s.godRaysExposure = godRaysExposure;

    s.vsync = vsync;
    s.targetFps = targetFps;
    return s;
  }
}
