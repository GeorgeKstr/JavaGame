package com.example.game.World.Atmosphere;

import com.badlogic.gdx.math.Vector3;

/** Simple spherical fog volume with tint and falloff. */
public final class FogVolume {
  public final Vector3 center = new Vector3(); // world space meters
  public float radius = 25f;                   // meters
  public float density = 0.35f;                // 0..1 (alpha)
  public float falloff = 0.7f;                 // 0..1 (0=linear, 1=softer)
  public float r = 0.85f, g = 0.90f, b = 1.00f;// tint
  public float noise = 0.0f;                   // 0..1, local noise modulation
  public float nearClip = 10.0f;               // meters: when inside, push fog start away from camera

  public FogVolume set(float x, float y, float z, float radius){
    this.center.set(x,y,z);
    this.radius = Math.max(0.1f, radius);
    return this;
  }
}
