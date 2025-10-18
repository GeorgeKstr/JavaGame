package com.example.game.World.Splines;

import com.badlogic.gdx.math.Vector3;
import com.example.game.World.AABBObject;

/** A control node for TerrainSpline. XZ define the path; Y is sampled from terrain unless explicitly set. */
public class TerrainSplineNode extends AABBObject {
  public final Vector3 pos = new Vector3();
  public boolean hasCustomY = false; // if true, use pos.y; else sampling decides
  private long id = 0L;
  private String name = "Node";

  public TerrainSplineNode() {}
  public TerrainSplineNode(float x, float y, float z) { pos.set(x, y, z); }

  @Override public long getId() { return id; }
  public void setId(long id) { this.id = id; }
  @Override public String getName() { return name; }
  @Override public void setName(String name) { this.name = name; }
  @Override public Vector3 getPosition() { return pos; }
}
