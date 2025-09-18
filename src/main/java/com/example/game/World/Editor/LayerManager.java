package com.example.game.World.Editor;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;

import java.util.LinkedHashMap;
import java.util.Map;

public class LayerManager {
  private final Map<String, EditorLayer> layers = new LinkedHashMap<>();
  private EditorLayer active;

  public void add(EditorLayer l) {
    layers.put(l.id(), l);
    if (active == null) setActive(l.id());
  }

  public Iterable<EditorLayer> all() { return layers.values(); }
  public EditorLayer getActive() { return active; }

  public void setActive(String id) {
    if (active != null) active.onDeactivate();
    active = layers.get(id);
    if (active != null) active.onActivate();
  }

  public void buildUI(Table leftPane, Skin skin) {
    if (active != null) active.buildUI(leftPane, skin);
  }

  public void update(float dt) { if (active != null) active.update(dt); }
  public void render3D(Camera cam) { if (active != null) active.render3D(cam); }

  public boolean handleInput(EditorInputRouter.InputEvent e) {
    return active != null && active.handleInput(e);
  }

  public boolean scrolled(float amountX, float amountY) {
    return active != null && active.scrolled(amountX, amountY);
  }
}
