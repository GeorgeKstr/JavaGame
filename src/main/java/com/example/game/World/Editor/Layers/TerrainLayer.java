package com.example.game.World.Editor.Layers;

import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.example.game.World.TerrainSystem;
import com.example.game.World.Editor.BaseToolLayer;
import com.example.game.World.Editor.BrushOverlay;
import com.example.game.World.Editor.Tools.BrushSettings;
import com.example.game.World.Editor.Tools.Terrain.FlattenTool;
import com.example.game.World.Editor.Tools.Terrain.PaintTextureTool;
import com.example.game.World.Editor.Tools.Terrain.RaiseLowerTool;
import com.example.game.World.Editor.Tools.Terrain.PolishTool;

public final class TerrainLayer extends BaseToolLayer {
  private final TerrainSystem terrain;
  private final BrushSettings brush;     // the shared brush your tools already use
  private BrushOverlay overlay;

  public TerrainLayer(TerrainSystem terrain, Skin skin) {
    super("terrain", "Terrain");
    this.terrain = terrain;
    this.brush = new BrushSettings(); // or pass in a shared instance if you already have one
    // add your tools here, e.g.:
    addTool(new RaiseLowerTool(terrain, brush));
    addTool(new PaintTextureTool(terrain, brush));
    addTool(new PolishTool(terrain, brush));
    addTool(new FlattenTool(terrain, brush));
  }

 // onActivate()
    @Override public void onActivate() {
    if (overlay == null) overlay = new BrushOverlay(64, terrain);
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
