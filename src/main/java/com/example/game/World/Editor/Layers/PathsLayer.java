package com.example.game.World.Editor.Layers;

import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.example.game.World.TerrainSystem;
import com.example.game.World.Editor.BaseToolLayer;
import com.example.game.World.Editor.BrushOverlay;

public final class PathsLayer extends BaseToolLayer {
  private final TerrainSystem terrain;
  private BrushOverlay overlay;

  public PathsLayer(TerrainSystem terrain, Skin skin) {
    super("paths", "Paths");
    this.terrain = terrain;
    // add your tools here, e.g.:
    
  }

 // onActivate()
    @Override public void onActivate() {
        if (overlay != null) { overlay.dispose(); overlay = null; }
    }

    // onDeactivate()
    @Override public void onDeactivate() {
    if (overlay != null) { overlay.dispose(); overlay = null; }
    }

    // update(dt)
    @Override public void update(float dt, Skin skin) {
    super.update(dt, skin);
    
    }

    
}
