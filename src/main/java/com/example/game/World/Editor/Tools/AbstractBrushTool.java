package com.example.game.World.Editor.Tools;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.example.game.World.TerrainSystem;
import com.example.game.World.Editor.BrushOverlay;

/** Base for brush-style tools: provides shared BrushSettings + a small brush UI. */
public abstract class AbstractBrushTool implements Tool {
  protected final BrushSettings brush;
  protected final TerrainSystem terrain;
  protected BrushOverlay overlay;
  private Label rLbl;
  private Label sLbl;
  private Slider r;
  private Slider s;

  protected AbstractBrushTool(BrushSettings sharedBrush, TerrainSystem terrain) { 
    this.brush = sharedBrush; 
    this.terrain = terrain;
  }

  /** Common brush UI (radius/strength/falloff). Call from buildUI() in subclasses. */
  protected void addBrushUI(Table parent, Skin skin) {
    parent.add(new Label("Brush settings:", skin, "title")).left().row();

    // --- Radius ---
    Table t = new Table();
    rLbl = new Label(String.format("Radius: %.1f", brush.getRadius()), skin);
    r = new Slider(1f, 64f, 0.5f, false, skin);
    r.setValue(brush.getRadius());
    r.addListener(new ChangeListener() {
      @Override public void changed(ChangeEvent event, Actor actor) {
        brush.setRadius(r.getValue());
        rLbl.setText(String.format("Radius: %.1f", brush.getRadius()));
      }
    });
    t.add(rLbl).left().row();
    t.add(r).width(220).row();
    parent.add(t).left().padBottom(8).row();

    // --- Strength ---
    t = new Table();
    sLbl = new Label(String.format("Strength: %.2f", brush.getStrength()), skin);
    s = new Slider(0.01f, 1f, 0.01f, false, skin);
    s.setValue(brush.getStrength());
    s.addListener(new ChangeListener() {
      @Override public void changed(ChangeEvent event, Actor actor) {
        brush.setStrength(s.getValue());
        sLbl.setText(String.format("Strength: %.2f", brush.getStrength()));
      }
    });
    t.add(sLbl).left().row();
    t.add(s).width(220).row();
    parent.add(t).left().padBottom(8).row();

    // --- Falloff ---
    t = new Table();
    final Label fLbl = new Label(String.format("Falloff: %.2f", brush.getFalloff()), skin);
    final Slider f = new Slider(0f, 1f, 0.01f, false, skin);
    f.setValue(brush.getFalloff());
    f.addListener(new ChangeListener() {
      @Override public void changed(ChangeEvent event, Actor actor) {
        brush.setFalloff(f.getValue());
        fLbl.setText(String.format("Falloff: %.2f", brush.getFalloff()));
      }
    });
    t.add(fLbl).left().row();
    t.add(f).width(220).row();
    parent.add(t).left().padBottom(8).row();
  }

  @Override public boolean scrolled(float amountX, float amountY) {
    if (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) && overlay != null) {
        if(amountY < 0) brush.setRadius(Math.min(800f, brush.getRadius() + 1));
        else brush.setRadius(Math.max(1f, brush.getRadius() - 1));
        overlay.setRadius(brush.getRadius());
        if (rLbl != null) rLbl.setText(String.format("Radius: %.1f", brush.getRadius()));
        if (r != null) r.setValue(brush.getRadius());
        return true;
    } else if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) && overlay != null) {
        if(amountY < 0) brush.setStrength(Math.min(1f, brush.getStrength() + 0.01f));
        else brush.setStrength(Math.max(0.01f, brush.getStrength() - 0.01f));
        if (sLbl != null) sLbl.setText(String.format("Strength: %.2f", brush.getStrength()));
        if (s != null) s.setValue(brush.getStrength());
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
