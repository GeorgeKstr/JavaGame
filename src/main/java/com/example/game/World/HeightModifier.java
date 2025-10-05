package com.example.game.World;

/** Produces a height delta (in world units) at a given world XZ position.
 * Implementations should be fast; they may approximate.
 */
public interface HeightModifier {
  /** Return height delta to add to base height at (x,z). Positive raises, negative lowers. */
  float deltaAt(float x, float z);
}
