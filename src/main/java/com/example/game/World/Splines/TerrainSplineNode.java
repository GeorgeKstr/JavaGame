package com.example.game.World.Splines;

import com.badlogic.gdx.math.Vector3;

/** A control node for TerrainSpline. XZ define the path; Y is sampled from terrain unless explicitly set. */
public class TerrainSplineNode {
  public final Vector3 pos = new Vector3();
  public boolean hasCustomY = false; // if true, use pos.y; else sampling decides

  public TerrainSplineNode() {}
  public TerrainSplineNode(float x, float y, float z) { pos.set(x, y, z); }
}
