package com.example.game.World;

/** A placeable world object that exposes a bounding box. */
public interface BoundObject extends Placeable {
  /** Returns the world-space AABB for the object at its current position. */
  AABB getBoundingBox();
}
