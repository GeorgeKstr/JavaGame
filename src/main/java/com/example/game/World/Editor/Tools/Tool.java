package com.example.game.World.Editor.Tools;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;

public interface Tool {
  String name();
  /** Called once when tool is selected in its layer. */
  default void onSelected() {}
  /** Called once when tool is deselected. */
  default void onDeselected() {}

  /** Build the tool-specific UI into the given container (called each time tool becomes active). */
  void buildUI(Table container, Skin skin);

  /** Per-frame tick for tool logic/preview, dt in seconds. */
  default void update(float dt, Camera cam) {}

  // Pointer actions in world space (ray-hit position already resolved by layer)
  default void onPointerDown(Vector3 world, int button, boolean shift, boolean ctrl) {}
  default void onPointerDrag(Vector3 world, int button, boolean shift, boolean ctrl) {}
  default void onPointerUp(Vector3 world, int button, boolean shift, boolean ctrl) {}
  default void render3D(Camera cam) {}
}
