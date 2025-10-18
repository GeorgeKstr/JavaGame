package com.example.game.World;

import com.badlogic.gdx.math.Vector3;

/**
 * Base class for placeable objects whose bounds are an AABB defined
 * relative to their origin/position. Subclasses provide the position
 * via Placeable.getPosition().
 */
public abstract class AABBObject implements BoundObject {
  protected final Vector3 bminRel = new Vector3();
  protected final Vector3 bmaxRel = new Vector3();
  private final AABB tmp = new AABB();

  public Vector3 getBMinRel() { return bminRel; }
  public Vector3 getBMaxRel() { return bmaxRel; }

  public AABBObject setBMinRel(float x, float y, float z) { this.bminRel.set(x, y, z); return this; }
  public AABBObject setBMaxRel(float x, float y, float z) { this.bmaxRel.set(x, y, z); return this; }
  public AABBObject setBMinRel(Vector3 v) { this.bminRel.set(v); return this; }
  public AABBObject setBMaxRel(Vector3 v) { this.bmaxRel.set(v); return this; }

  @Override
  public AABB getBoundingBox() {
    Vector3 p = getPosition();
    float minX = p.x + Math.min(bminRel.x, bmaxRel.x);
    float minY = p.y + Math.min(bminRel.y, bmaxRel.y);
    float minZ = p.z + Math.min(bminRel.z, bmaxRel.z);
    float maxX = p.x + Math.max(bminRel.x, bmaxRel.x);
    float maxY = p.y + Math.max(bminRel.y, bmaxRel.y);
    float maxZ = p.z + Math.max(bminRel.z, bmaxRel.z);
    return tmp.set(minX, minY, minZ, maxX, maxY, maxZ);
  }
}
