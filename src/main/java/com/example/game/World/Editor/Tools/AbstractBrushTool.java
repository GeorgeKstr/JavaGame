package com.example.game.World.Editor.Tools;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.example.game.World.TerrainSystem;
import com.example.game.World.Editor.BrushOverlay;

/** Base for brush-style tools: provides shared BrushSettings + a small brush UI. */
public abstract class AbstractBrushTool implements Tool {
  protected final BrushSettings brush;
    private final TerrainSystem terrain;
  private BrushOverlay overlay;

  protected AbstractBrushTool(BrushSettings sharedBrush, TerrainSystem terrain) { 
    this.brush = sharedBrush; 
    this.terrain = terrain;
  }

  /** Common brush UI (radius/strength/falloff). Call from buildUI() in subclasses. */
  protected void addBrushUI(Table parent, Skin skin) {
    parent.add(new Label("Brush", skin, "title")).left().row();

    // --- Radius ---
    Table t = new Table();
    final Label rLbl = new Label(String.format("Radius: %.1f", brush.radius), skin);
    final Slider r = new Slider(1f, 64f, 0.5f, false, skin);
    r.setValue(brush.radius);
    r.addListener(new ChangeListener() {
      @Override public void changed(ChangeEvent event, Actor actor) {
        brush.radius = r.getValue();
        rLbl.setText(String.format("Radius: %.1f", brush.radius));
      }
    });
    t.add(rLbl).left().row();
    t.add(r).width(220).row();
    parent.add(t).left().padBottom(8).row();

    // --- Strength ---
    t = new Table();
    final Label sLbl = new Label(String.format("Strength: %.2f", brush.strength), skin);
    final Slider s = new Slider(0.01f, 1f, 0.01f, false, skin);
    s.setValue(brush.strength);
    s.addListener(new ChangeListener() {
      @Override public void changed(ChangeEvent event, Actor actor) {
        brush.strength = s.getValue();
        sLbl.setText(String.format("Strength: %.2f", brush.strength));
      }
    });
    t.add(sLbl).left().row();
    t.add(s).width(220).row();
    parent.add(t).left().padBottom(8).row();

    // --- Falloff ---
    t = new Table();
    final Label fLbl = new Label(String.format("Falloff: %.2f", brush.falloff), skin);
    final Slider f = new Slider(0f, 1f, 0.01f, false, skin);
    f.setValue(brush.falloff);
    f.addListener(new ChangeListener() {
      @Override public void changed(ChangeEvent event, Actor actor) {
        brush.falloff = f.getValue();
        fLbl.setText(String.format("Falloff: %.2f", brush.falloff));
      }
    });
    t.add(fLbl).left().row();
    t.add(f).width(220).row();
    parent.add(t).left().padBottom(8).row();
  }

  @Override public void onSelected() {
    if (overlay == null) overlay = new BrushOverlay(64);
  }

  @Override public void onDeselected() {
    if (overlay != null) { overlay.dispose(); overlay = null; }
  }

  @Override public void update(float dt, Camera cam) { 
    int radiusTiles = Math.max(1, Math.round(brush.radius / terrain.getTileSize()));
    if (overlay != null) overlay.update(cam, terrain, radiusTiles, terrain.getTileSize());
  }

  @Override public void render3D(Camera cam) { if (overlay != null) overlay.draw(cam);}
}
