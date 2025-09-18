package com.example.game.World.Editor;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.example.game.World.Editor.IO.MapReader;
import com.example.game.World.Editor.IO.MapWriter;

/** A self-contained editor layer (terrain, objects, atmosphere, rpg, …). */
public interface EditorLayer {
  String id();                 // e.g., "terrain", "objects", "atmos", "rpg"
  String displayName();        // for the toolbar/tab

  void onActivate();           // layer became active (focus/UI can be created here)
  void onDeactivate();         // layer lost focus (hide UI/cleanup transient state)

  void update(float dt);       // per-frame logic
  void render3D(Camera cam);   // gizmos/overlays for this layer
  void buildUI(Stage ui);      // (re)build this layer's right/left panel or HUD

  boolean handleInput(EditorInputRouter.InputEvent e); // return true if consumed

  void saveToMap(MapWriter w); // write this layer's data to the .map
  void loadFromMap(MapReader r);// read this layer's data from the .map
}
