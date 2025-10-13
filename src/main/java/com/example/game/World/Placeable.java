package com.example.game.World;

import com.badlogic.gdx.math.Vector3;

/** Common contract for placeable objects in the world editor. */
public interface Placeable {
  /** Stable unique identifier persisted with the object. */
  long getId();
  /** Human-friendly display name. */
  String getName();
  /** World-space position used for focusing/jumping. */
  Vector3 getPosition();
}
