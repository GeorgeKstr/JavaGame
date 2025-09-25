package com.example.game.World.Editor.Layers;

import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.example.game.World.TerrainSystem;
import com.example.game.World.Editor.BaseToolLayer;
import com.example.game.World.Editor.Tools.BrushSettings;
import com.example.game.World.Editor.Tools.WalkTool;

public final class WalkLayer extends BaseToolLayer {
  public WalkLayer(TerrainSystem terrain, Skin skin) {
    super("walk", "Walk");
    addTool(new WalkTool(terrain));
  }

  @Override
  public void onActivate() {
    // Implement activation logic if needed
  }

  @Override
  public void onDeactivate() {
    // Implement deactivation logic if needed
  }
}
