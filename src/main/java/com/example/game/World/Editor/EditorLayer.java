package com.example.game.World.Editor;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;

/** One editor layer (terrain, objects, etc.). */
public interface EditorLayer {
  String id();
  String displayName();

  void onActivate();
  void onDeactivate();

  void update(float dt);
  void render3D(Camera cam);

  /** Build (or rebuild) this layer's tools into the provided left pane. */
  void buildUI(Table leftPane, Skin skin);

  /** Let the layer consume structured input (optional). */
  default boolean handleInput(EditorInputRouter.InputEvent e) { return false; }

  default boolean scrolled(float amountX, float amountY) { return false; }

  /** Persistence hooks (optional now). */
  default void saveToMap(Object writer) {}
  default void loadFromMap(Object reader) {}
}
