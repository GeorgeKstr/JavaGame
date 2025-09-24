package com.example.game.World.Editor.Tools;

public final class BrushSettings {
  public float radius = 6f;
  public float strength = 0.5f;   // 0..1
  public float falloff = 0.8f;    // 0..1 (1=soft)

  public BrushSettings copy() {
    BrushSettings b = new BrushSettings();
    b.radius = radius; b.strength = strength; b.falloff = falloff;
    return b;
  }
}
