package com.example.game.World.Editor;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.example.game.World.Editor.Tools.Tool;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseToolLayer implements EditorLayer {
  private final String id, display;
  protected final List<Tool> tools = new ArrayList<>();
  protected Tool active;

  // NEW: a persistent content area we clear/rebuild
  private final Table content = new Table();

  protected BaseToolLayer(String id, String displayName) {
    this.id = id; this.display = displayName;
    content.top().left();
  }

  protected void addTool(Tool t) {
    tools.add(t);
    if (active == null) { active = t; t.onSelected(); }
  }

  @Override public String id() { return id; }
  @Override public String displayName() { return display; }

  /** Build the left-pane UI: tabs for tools + tool's own UI content below. */
  @Override public void buildUI(Table parent, Skin skin) {
    parent.clear();

    // --- tool buttons bar ---
    Table bar = new Table();
    ButtonGroup<TextButton> group = new ButtonGroup<>();
    group.setUncheckLast(true);   // do not allow unchecking the active toggle

    bar.defaults().padRight(6);
    for (Tool t : tools) {
      TextButton b = new TextButton(t.name(), skin, "toggle");
      group.add(b);
      b.setChecked(t == active);
      final Tool TT = t;
      b.addListener(com.example.game.UI.SkinFactory.onClick(() -> {
        if (active == TT) return;            // NEW: ignore re-click on the same tool
        if (active != null) active.onDeselected();
        active = TT;
        group.setChecked(b.getText().toString()); // keep visual state sane
        active.onSelected();
        rebuildToolUI(skin);
      }));
      bar.add(b);
    }

    parent.add(bar).left().row();
    parent.add(new Label(display + " Tools", skin, "title")).left().padTop(6).row();

    // --- content area (persistent) ---
    parent.add(content).left().top().growX().row();

    // first build
    rebuildToolUI(skin);
  }

  private void rebuildToolUI(Skin skin) {
    content.clearChildren();                            // NEW: clear only the content area
    content.add(new Label("Active Tool: " + (active != null ? active.name() : "None"),
                          skin, "default")).left().padBottom(6).row();

    Table toolUI = new Table(); toolUI.top().left();
    if (active != null) active.buildUI(toolUI, skin);
    content.add(toolUI).left().top().growX().row();
    content.invalidateHierarchy();
  }

  // ---------------- input / lifecycle passthrough ----------------
  public void onPointerDown(Camera cam, Vector3 world, int button, boolean sh, boolean ctrl) {
    if (active != null) active.onPointerDown(world, button, sh, ctrl);
  }
  public void onPointerDrag(Camera cam, Vector3 world, int button, boolean sh, boolean ctrl) {
    if (active != null) active.onPointerDrag(world, button, sh, ctrl);
  }
  public void onPointerUp(Camera cam, Vector3 world, int button, boolean sh, boolean ctrl) {
    if (active != null) active.onPointerUp(world, button, sh, ctrl);
  }

  public Tool getActiveTool() { return active; }

  @Override public void update(float dt) {
    if (active != null) active.update(dt, EditorContext.getCamera());
  }

  @Override public void render3D(Camera cam) {
    if (active != null) active.render3D(cam);
  }
}
