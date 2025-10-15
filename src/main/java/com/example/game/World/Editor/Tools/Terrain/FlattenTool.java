package com.example.game.World.Editor.Tools.Terrain;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.example.game.World.TerrainSystem;
import com.example.game.World.Editor.Tools.AbstractBrushTool;
import com.example.game.World.Editor.Tools.BrushSettings;

/**
 * Hard Flatten-to-0 brush.
 * - No easing: each stamp sets all covered samples to 0.
 * - Minimal throttling so drag doesn't spam redundant work.
 */
public final class FlattenTool extends AbstractBrushTool {

  // Throttle so we don't restamp too densely while dragging
  private static final float MIN_STAMP_DT_SEC    = 1f / 120f; // time gate
  private static final float MIN_STAMP_DIST_FRAC = 0.20f;     // distance gate (fraction of radius)
  
  private boolean pointerActive = false;

  private final Vector3 lastStampWorld = new Vector3();
  private float timeSinceLast = 0f;

  public FlattenTool(TerrainSystem terrain, BrushSettings sharedBrush) { super(sharedBrush, terrain); }

  @Override public String name() { return "Flatten"; }

  @Override
  public void buildUI(Table parent, Skin skin) {
    addBrushUI(parent, skin);
    parent.add(new Label("Click/drag to set terrain height to 0.", skin))
          .left().padTop(6).row();
  }

  @Override
  public void onPointerDown(int button, boolean shift, boolean ctrl) {
    pointerActive = true;
    lastStampWorld.set(world);
    stampAt(world);
    timeSinceLast = 0f;
  }

  @Override
  public void onPointerDrag(int button, boolean shift, boolean ctrl) {
    // Distance gate: only restamp after moving enough relative to brush size
    float tile = terrain.getTileSize();
    int rCells = Math.max(1, (int)brush.getRadius());
    float radiusWorld = rCells * tile;

    float dx = world.x - lastStampWorld.x;
    float dz = world.z - lastStampWorld.z;
    float dist2 = dx*dx + dz*dz;
    float need = radiusWorld * MIN_STAMP_DIST_FRAC;

    if (dist2 >= need*need && timeSinceLast >= MIN_STAMP_DT_SEC) {
      stampAt(world);
      lastStampWorld.set(world);
      timeSinceLast = 0f;
    }
  }

  @Override
  public void onPointerUp(int button, boolean shift, boolean ctrl) {
    pointerActive = false;
  }

  // Editor loop hook; we keep it light (only advances the timer)
  @Override
  public void update(float dt, Camera cam) {
    super.update(dt, cam);
    if (pointerActive) timeSinceLast += dt;
  }

  private void stampAt(Vector3 wpos) {
    int rCells = Math.max(1, (int)brush.getRadius()); // radius already in cells in your tools
    float tile = terrain.getTileSize();
    int gx = (int)Math.floor(wpos.x / tile);
    int gz = (int)Math.floor(wpos.z / tile);

    if (terrain.getEditor().setHeightsToZeroCircle(gx, gz, rCells/2)) {
      terrain.rebuildDirtyChunks();
    }
  }

}
