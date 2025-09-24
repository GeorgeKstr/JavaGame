package com.example.game.World.Editor.Tools;

import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.math.Vector3;
import com.example.game.World.TerrainSystem;
import com.example.game.World.Editor.Tools.Tool;

public final class WalkTool implements Tool {
  private final TerrainSystem terrain;
  public WalkTool(TerrainSystem t){ this.terrain = t; }
  @Override public String name() { return "Walk"; }

  @Override public void buildUI(Table parent, Skin skin) {
    parent.add(new Label("Walk Tool", skin, "title")).left().row();
    parent.add(new Label("LMB: add walk node\nCtrl+LMB: remove\nRMB drag/WASD: move", skin)).left().row();
  }

  @Override public void onPointerDown(Vector3 world, int button, boolean shift, boolean ctrl) {
    //if (!ctrl) terrain.walk().addNode(world.x, world.z); else terrain.walk().removeNearest(world.x, world.z);
  }
}
