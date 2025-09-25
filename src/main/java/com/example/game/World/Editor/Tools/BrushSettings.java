package com.example.game.World.Editor.Tools;

public final class BrushSettings {
  private float radius = 6f;
  private float strength = 0.5f;   // 0..1
  private float falloff = 0.8f;    // 0..1 (1=soft)

  public BrushSettings copy() {
    BrushSettings b = new BrushSettings();
    b.radius = radius; b.strength = strength; b.falloff = falloff;
    return b;
  }

  public float getRadius() { return radius; }
  public float getStrength() { return strength; }
  public float getFalloff() { return falloff; }
  public void setRadius(float r) { radius = r; }
  public void setStrength(float s) { strength = s; }
  public void setFalloff(float f) { falloff = f; }
}
