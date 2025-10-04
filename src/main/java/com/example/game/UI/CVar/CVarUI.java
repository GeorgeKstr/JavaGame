package com.example.game.UI.CVar;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.example.game.Config.cvar.CVar;

import java.util.function.Function;

/** Helpers to bind Scene2D widgets to CVars. */
public final class CVarUI {
  private CVarUI() {}

  public static CheckBox checkbox(String label, Skin skin, final CVar<Boolean> var) {
    final CheckBox cb = new CheckBox(label, skin);
    // initial
    cb.setChecked(Boolean.TRUE.equals(var.get()));
    // UI -> CVar
    cb.addListener(new ChangeListener() {
      @Override public void changed(ChangeEvent event, Actor actor) {
        var.set(cb.isChecked());
      }
    });
    // CVar -> UI
    var.onChanged(v -> {
      if (cb.isChecked() != (v != null && v)) cb.setChecked(v != null && v);
    });
    return cb;
  }

  public static Table sliderFloat(String label, Skin skin, final CVar<Float> var, float min, float max, float step, Function<Float, String> formatter) {
    final Table t = new Table();
    final Label lbl = new Label(formatter != null ? formatter.apply(var.get()) : label, skin);
    final Slider s = new Slider(min, max, step, false, skin);
    s.setValue(var.get());
    t.add(lbl).left().row();
    t.add(s).width(240).height(32).left();
    // UI -> CVar
    s.addListener(new ChangeListener() {
      @Override public void changed(ChangeEvent event, Actor actor) {
        float v = s.getValue();
        var.set(v);
        if (formatter != null) lbl.setText(formatter.apply(v));
      }
    });
    // CVar -> UI
    var.onChanged(v -> {
      float nv = v == null ? 0f : v;
      if (Math.abs(s.getValue() - nv) > 1e-6f) s.setValue(nv);
      if (formatter != null) lbl.setText(formatter.apply(nv));
    });
    return t;
  }

  public static Table sliderInt(String label, Skin skin, final CVar<Integer> var, int min, int max, int step, Function<Integer, String> formatter) {
    final Table t = new Table();
    final Label lbl = new Label(formatter != null ? formatter.apply(var.get()) : label, skin);
    final Slider s = new Slider(min, max, step, false, skin);
    s.setValue(var.get());
    t.add(lbl).left().row();
    t.add(s).width(240).height(32).left();
    // UI -> CVar
    s.addListener(new ChangeListener() {
      @Override public void changed(ChangeEvent event, Actor actor) {
        int v = Math.round(s.getValue());
        var.set(v);
        if (formatter != null) lbl.setText(formatter.apply(v));
      }
    });
    // CVar -> UI
    var.onChanged(v -> {
      int nv = v == null ? 0 : v;
      if (Math.round(s.getValue()) != nv) s.setValue(nv);
      if (formatter != null) lbl.setText(formatter.apply(nv));
    });
    return t;
  }
}
