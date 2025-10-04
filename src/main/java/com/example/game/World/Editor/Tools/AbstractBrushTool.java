package com.example.game.World.Editor.Tools;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.example.game.World.TerrainSystem;
import com.example.game.World.Editor.BrushOverlay;
import com.example.game.Config.cvar.CVars;
import com.example.game.UI.CVar.CVarUI;

/** Base for brush-style tools: provides shared BrushSettings + a small brush UI. */
public abstract class AbstractBrushTool implements Tool {
  protected final BrushSettings brush;
  protected final TerrainSystem terrain;
  protected BrushOverlay overlay;
  // UI now bound via CVarUI; we keep no local slider references

  protected AbstractBrushTool(BrushSettings sharedBrush, TerrainSystem terrain) {
    this.brush = sharedBrush;
    this.terrain = terrain;
    // Initialize brush from CVars and keep it in sync
    brush.setRadius(CVars.BRUSH_RADIUS.get());
    brush.setStrength(CVars.BRUSH_STRENGTH.get());
    brush.setFalloff(CVars.BRUSH_FALLOFF.get());

    CVars.BRUSH_RADIUS.onChanged(v -> {
      float nv = v == null ? brush.getRadius() : v;
      brush.setRadius(nv);
      if (overlay != null) overlay.setRadius(nv);
    });
    CVars.BRUSH_STRENGTH.onChanged(v -> brush.setStrength(v == null ? brush.getStrength() : v));
    CVars.BRUSH_FALLOFF.onChanged(v -> brush.setFalloff(v == null ? brush.getFalloff() : v));
  }

  /** Common brush UI (radius/strength/falloff). Call from buildUI() in subclasses. */
  protected void addBrushUI(Table parent, Skin skin) {
    parent.add(new Label("Brush settings:", skin, "title")).left().row();

    // Radius (1..BRUSH_MAX_RADIUS)
    int maxR = Math.max(1, CVars.BRUSH_MAX_RADIUS.get());
    Table t = CVarUI.sliderFloat(
        String.format("Radius: %.1f", CVars.BRUSH_RADIUS.get()),
        skin,
        CVars.BRUSH_RADIUS,
        1f, maxR, 0.5f,
        v -> String.format("Radius: %.1f", v)
    );
    parent.add(t).left().padBottom(8).row();

    // Strength (0.01..1)
    Table t2 = CVarUI.sliderFloat(
        String.format("Strength: %.2f", CVars.BRUSH_STRENGTH.get()),
        skin,
        CVars.BRUSH_STRENGTH,
        0.01f, 1f, 0.01f,
        v -> String.format("Strength: %.2f", v)
    );
    parent.add(t2).left().padBottom(8).row();

    // Falloff (0..1)
    Table t3 = CVarUI.sliderFloat(
        String.format("Falloff: %.2f", CVars.BRUSH_FALLOFF.get()),
        skin,
        CVars.BRUSH_FALLOFF,
        0f, 1f, 0.01f,
        v -> String.format("Falloff: %.2f", v)
    );
    parent.add(t3).left().padBottom(8).row();
  }

  @Override public boolean scrolled(float amountX, float amountY) {
    if (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) && overlay != null) {
        float newR = brush.getRadius();
        if(amountY < 0) newR = Math.min(Math.max(1f, CVars.BRUSH_MAX_RADIUS.get()), brush.getRadius() + 1);
        else newR = Math.max(1f, brush.getRadius() - 1);
        CVars.BRUSH_RADIUS.set(newR);
        overlay.setRadius(newR);
        return true;
    } else if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) && overlay != null) {
        float ns = brush.getStrength();
        if(amountY < 0) ns = Math.min(1f, ns + 0.01f);
        else ns = Math.max(0.01f, ns - 0.01f);
        CVars.BRUSH_STRENGTH.set(ns);
        return true;
    }
    return false;
}

  @Override public void onSelected() {
    if (overlay == null) overlay = new BrushOverlay(64, terrain);
  }

  @Override public void onDeselected() {
    if (overlay != null) { overlay.dispose(); overlay = null; }
  }

  @Override public void update(float dt, Camera cam) { 
    int radiusTiles = Math.max(1, Math.round(brush.getRadius() / terrain.getTileSize()));
    if (overlay != null) overlay.update(cam, terrain, radiusTiles, brush.getFalloff(), terrain.getTileSize());
  }

  @Override public void render3D(Camera cam) { if (overlay != null) overlay.draw(cam);}
}
