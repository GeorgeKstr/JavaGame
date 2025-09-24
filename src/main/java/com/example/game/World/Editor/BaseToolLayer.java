package com.example.game.World.Editor;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.example.game.World.Editor.Tools.Tool;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseToolLayer implements EditorLayer {
  private final String id, display;
  protected final List<Tool> tools = new ArrayList<>();
  protected Tool active;

  protected BaseToolLayer(String id, String displayName) {
    this.id = id; this.display = displayName;
  }

  protected void addTool(Tool t) { tools.add(t); if (active == null) active = t; }

  @Override public String id() { return id; }
  @Override public String displayName() { return display; }

  /** Build the left-pane UI: tabs for tools + tool's own UI content below. */
  @Override public void buildUI(Table parent, Skin skin) {
    parent.clear();

    // tool buttons
    Table bar = new Table();
    ButtonGroup<TextButton> group = new ButtonGroup<>();
    bar.defaults().padRight(6);

    for (Tool t : tools) {
      TextButton b = new TextButton(t.name(), skin, "toggle");
      group.add(b);
      b.setChecked(t == active);
      final Tool TT = t;
      b.addListener(com.example.game.UI.SkinFactory.onClick(() -> {
        if (active != null) active.onDeselected();
        active = TT;
        rebuildToolUI(parent, skin); // rebuild below
        active.onSelected();
      }));
      bar.add(b);
    }
    parent.add(bar).left().row();
    parent.add(new Label(display + " Tools", skin, "title")).left().padTop(6).row();

    // tool-specific UI goes here
    rebuildToolUI(parent, skin);
  }

  private void rebuildToolUI(Table parent, Skin skin) {
    // remove everything below the header rows and reconstruct
    while (parent.getChildren().size > 2) parent.removeActorAt(2, true);
    Table toolUI = new Table(); toolUI.top().left();
    if (active != null) active.buildUI(toolUI, skin);
    parent.add(toolUI).left().top().growX().row();
  }

  /** Forward input to active tool (layers can override if they need hit-testing first). */
  public void onPointerDown(Camera cam, com.badlogic.gdx.math.Vector3 world, int button, boolean sh, boolean ctrl) {
    if (active != null) active.onPointerDown(world, button, sh, ctrl);
  }
  public void onPointerDrag(Camera cam, com.badlogic.gdx.math.Vector3 world, int button, boolean sh, boolean ctrl) {
    if (active != null) active.onPointerDrag(world, button, sh, ctrl);
  }
  public void onPointerUp(Camera cam, com.badlogic.gdx.math.Vector3 world, int button, boolean sh, boolean ctrl) {
    if (active != null) active.onPointerUp(world, button, sh, ctrl);
  }

  @Override public void update(float dt) { if (active != null) active.update(dt, EditorContext.getCamera()); }
  @Override public void render3D(com.badlogic.gdx.graphics.Camera cam) { /* optional: tool previews */ }
}
