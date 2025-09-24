package com.example.game.World.Editor.Tools.Terrain;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.example.game.World.TerrainSystem;
import com.example.game.World.Editor.Tools.AbstractBrushTool;
import com.example.game.World.Editor.Tools.BrushSettings;

public final class RaiseLowerTool extends AbstractBrushTool {
  private final TerrainSystem terrain;
  private boolean lowerMode = false;

  public RaiseLowerTool(TerrainSystem terrain, BrushSettings sharedBrush){ super(sharedBrush); this.terrain = terrain; }

  @Override public String name() { return "Raise/Lower"; }

  @Override public void buildUI(Table parent, Skin skin) {
    addBrushUI(parent, skin);
    Label mode = new Label("Mode: Raise (hold Shift for Lower)", skin);
    parent.add(mode).left().padTop(6).row();
  }

  @Override public void onPointerDown(Vector3 world, int button, boolean shift, boolean ctrl) { stroke(world, shift); }
  @Override public void onPointerDrag(Vector3 world, int button, boolean shift, boolean ctrl) { stroke(world, shift); }

  private void stroke(Vector3 world, boolean shift) {
    float sgn = shift ? -1f : 1f;
    //terrain.brushHeight(world.x, world.z, brush.radius, sgn*brush.strength, brush.falloff);
  }

  @Override public void update(float dt, Camera cam) { /* optional: show brush decal */ }
}
