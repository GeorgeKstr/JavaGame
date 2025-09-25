// com/example/game/Config/Settings.java
package com.example.game.Config;

public class Settings {
  // --- Editor UI / camera ---
  public boolean invertY = false;
  public float mouseSensitivity = 0.3f;     // orbit speed
  public float panSpeedFactor  = 0.0025f;   // world units per px * distance
  public int   brushMaxRadius  = 64;        // clamp
  public boolean brushAlwaysOnTop = true;   // draw over terrain
  public float editorMoveSpeed = 800f;    // units per second

  // --- Terrain / streaming ---
  public int   viewRadiusChunks = 3;        // ±radius
  public int   defaultTextureId = 0;

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
    s.vsync = vsync;
    s.targetFps = targetFps;
    return s;
  }
}
