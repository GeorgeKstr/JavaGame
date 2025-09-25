package com.example.game.World.Editor;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;

import java.util.*;

/**
 * Manages editor layers: add, select active, build UI for active,
 * and forward update/render calls.
 */
public final class LayerManager {

  private final List<EditorLayer> ordered = new ArrayList<>();
  private final Map<String, EditorLayer> byId = new HashMap<>();
  private EditorLayer active;

  /** Add a layer; the first added becomes active by default. */
  public void add(EditorLayer layer) {
    if (layer == null) return;
    if (byId.containsKey(layer.id())) return; // ignore duplicates by id
    ordered.add(layer);
    byId.put(layer.id(), layer);
    if (active == null) active = layer;
  }

  /** All layers in insertion order (read-only view). */
  public List<EditorLayer> all() {
    return Collections.unmodifiableList(ordered);
  }

  /** Currently active layer (may be null if none added yet). */
  public EditorLayer active() {
    return active;
  }

  /** Activate a layer by id (no-op if id unknown). */
  public void setActive(String id) {
    EditorLayer l = byId.get(id);
    if (l != null && l != active) {
      active = l;
    }
  }

  /** Build the left-pane UI for the active layer. */
  public void buildUI(Table parent, Skin skin) {
    if (active != null) {
      active.buildUI(parent, skin);
    }
  }

  /** Per-frame tick for the active layer. */
  public void update(float dt) {
    if (active != null) {
      active.update(dt);
    }
  }

  /** 3D overlays/gizmos for the active layer. */
  public void render3D(Camera cam) {
    if (active != null) {
      active.render3D(cam);
    }
  }
}
