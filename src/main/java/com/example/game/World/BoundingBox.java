package com.example.game.World;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;

/** Generic bounding box interface for picking and spatial tests. */
public interface BoundingBox {
  /** Returns true if the point is inside the box (inclusive on faces). */
  boolean contains(float x, float y, float z);

  default boolean contains(Vector3 p) { return contains(p.x, p.y, p.z); }

  /**
   * Ray intersection using parametric t along the ray. Returns the entry t if hit, otherwise -1.
   * Implementations should return the nearest non-negative intersection (t >= 0).
   */
  float intersectRayT(Ray ray);
}
