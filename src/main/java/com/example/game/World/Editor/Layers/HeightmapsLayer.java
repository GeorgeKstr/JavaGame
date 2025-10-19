package com.example.game.World.Editor.Layers;

import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.example.game.World.TerrainSystem;
import com.example.game.World.Editor.BaseToolLayer;
import com.example.game.World.Editor.Tools.Terrain.MountainTool;

public final class HeightmapsLayer extends BaseToolLayer {
  private final TerrainSystem terrain;
  private final MountainTool mountainTool;
  private final com.example.game.World.Editor.Tools.Terrain.CaptureAreaTool captureTool;
  private final com.example.game.World.Editor.Tools.Terrain.PaintHeightmapTool paintHMTool;

  public HeightmapsLayer(TerrainSystem terrain, Skin skin) {
    super("heightmaps", "Heightmaps");
    this.terrain = terrain;
    this.mountainTool = new MountainTool(terrain);
    addTool(this.mountainTool);
    this.captureTool = new com.example.game.World.Editor.Tools.Terrain.CaptureAreaTool(terrain, this.mountainTool);
    addTool(this.captureTool);
    this.paintHMTool = new com.example.game.World.Editor.Tools.Terrain.PaintHeightmapTool(terrain);
    addTool(this.paintHMTool);
  }

  public MountainTool getMountainTool() { return mountainTool; }

  @Override public void onActivate() { }
  @Override public void onDeactivate() { }
}
