package com.example.game.World.Editor.Tools.Walk;

import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.example.game.World.Editor.Tools.Tool;

/** Roam tool: no UI, enables FPS roaming when active. */
public final class RoamTool implements Tool {
  @Override public String name() { return "Roam"; }
  @Override public void buildUI(Table parent, Skin skin) {
    parent.add(new Label("Roam using mouse-look + WASD. Press ESC to release cursor.", skin)).left();
  }
  @Override public void onSelected() { com.badlogic.gdx.Gdx.input.setCursorCatched(true); }
  @Override public void onDeselected() { com.badlogic.gdx.Gdx.input.setCursorCatched(false); }
}
