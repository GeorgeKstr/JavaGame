package com.example.game.World.Editor.Layers;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.example.game.World.Editor.BaseToolLayer;
import com.example.game.World.Editor.Tools.Tool;
import com.example.game.Render.Pipeline.RenderPipeline;
import com.example.game.Render.Passes.HDRPrePass;
import com.example.game.Render.Passes.SkyPass;
import com.example.game.Render.Passes.GodRaysPass;
import com.example.game.Screens.EditorScreen;
import com.example.game.Config.cvar.CVars;

public final class AtmosphereLayer extends BaseToolLayer {
  private final RenderPipeline pipeline;
  private final EditorScreen editor;

  public AtmosphereLayer(RenderPipeline pipeline, EditorScreen editor) {
    super("atmosphere", "Atmosphere");
    this.pipeline = pipeline;
    this.editor = editor;
    addTool(new ExposureTool());
    addTool(new SkyTool());
  }

  @Override public void onActivate() { }
  @Override public void onDeactivate() { }

  private final class ExposureTool implements Tool {
    @Override public String name() { return "Exposure"; }
    @Override public void buildUI(Table ui, Skin skin) {
      final HDRPrePass hdr = pipeline.get(HDRPrePass.class);
      ui.add(new Label("HDR", skin, "title")).left().row();
      // Exposure
      float initExp = CVars.HDR_EXPOSURE.get();
      final Label expLbl = new Label(String.format("Exposure: %.2f", initExp), skin);
      ui.add(expLbl).left().row();
      final Slider exp = new Slider(0.4f, 2.0f, 0.01f, false, skin);
      exp.setValue(initExp);
      exp.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ float v = ((Slider)actor).getValue(); expLbl.setText(String.format("Exposure: %.2f", v)); CVars.HDR_EXPOSURE.set(v); if (hdr!=null) hdr.setExposure(v);} });
      ui.add(exp).width(240).row();
      // Saturation
      float initSat = CVars.HDR_SATURATION.get();
      final Label satLbl = new Label(String.format("Saturation: %.2f", initSat), skin);
      ui.add(satLbl).left().padTop(4).row();
      final Slider sat = new Slider(0.5f, 2.0f, 0.01f, false, skin);
      sat.setValue(initSat);
      sat.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ float v=((Slider)actor).getValue(); satLbl.setText(String.format("Saturation: %.2f", v)); CVars.HDR_SATURATION.set(v); if (hdr!=null) hdr.setSaturation(v);} });
      ui.add(sat).width(240).row();
      // Contrast
      float initCon = CVars.HDR_CONTRAST.get();
      final Label conLbl = new Label(String.format("Contrast: %.2f", initCon), skin);
      ui.add(conLbl).left().padTop(4).row();
      final Slider con = new Slider(0.5f, 2.0f, 0.01f, false, skin);
      con.setValue(initCon);
      con.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ float v=((Slider)actor).getValue(); conLbl.setText(String.format("Contrast: %.2f", v)); CVars.HDR_CONTRAST.set(v); if (hdr!=null) hdr.setContrast(v);} });
      ui.add(con).width(240).row();

      ui.add(new Label("Eye Adaptation", skin, "title")).left().padTop(8).row();
      // Key
      final Label keyLbl = new Label(String.format("Key: %.3f", editor.getEyeKey()), skin);
      ui.add(keyLbl).left().row();
      final Slider key = new Slider(0.05f, 0.35f, 0.001f, false, skin);
      key.setValue(CVars.EYE_KEY.get());
      key.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ float v=((Slider)actor).getValue(); keyLbl.setText(String.format("Key: %.3f", v)); CVars.EYE_KEY.set(v); editor.setEyeKey(v);} });
      ui.add(key).width(240).row();
      // MinExp
      final Label minLbl = new Label(String.format("MinExp: %.2f", editor.getEyeMinExp()), skin);
      ui.add(minLbl).left().padTop(4).row();
      final Slider min = new Slider(0.5f, 1.0f, 0.01f, false, skin);
      min.setValue(CVars.EYE_MIN_EXP.get());
      min.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ float v=((Slider)actor).getValue(); minLbl.setText(String.format("MinExp: %.2f", v)); CVars.EYE_MIN_EXP.set(v); editor.setEyeMinExp(v);} });
      ui.add(min).width(240).row();
      // MaxExp
      final Label maxLbl = new Label(String.format("MaxExp: %.2f", editor.getEyeMaxExp()), skin);
      ui.add(maxLbl).left().padTop(4).row();
      final Slider max = new Slider(1.0f, 2.0f, 0.01f, false, skin);
      max.setValue(CVars.EYE_MAX_EXP.get());
      max.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ float v=((Slider)actor).getValue(); maxLbl.setText(String.format("MaxExp: %.2f", v)); CVars.EYE_MAX_EXP.set(v); editor.setEyeMaxExp(v);} });
      ui.add(max).width(240).row();
      // RateUp
      final Label upLbl = new Label(String.format("RateUp: %.2f", editor.getEyeRateUp()), skin);
      ui.add(upLbl).left().padTop(4).row();
      final Slider up = new Slider(1.0f, 12.0f, 0.1f, false, skin);
      up.setValue(CVars.EYE_RATE_UP.get());
      up.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ float v=((Slider)actor).getValue(); upLbl.setText(String.format("RateUp: %.2f", v)); CVars.EYE_RATE_UP.set(v); editor.setEyeRates(v, editor.getEyeRateDown());} });
      ui.add(up).width(240).row();
      // RateDown
      final Label downLbl = new Label(String.format("RateDown: %.2f", editor.getEyeRateDown()), skin);
      ui.add(downLbl).left().padTop(4).row();
      final Slider down = new Slider(0.2f, 6.0f, 0.1f, false, skin);
      down.setValue(CVars.EYE_RATE_DOWN.get());
      down.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ float v=((Slider)actor).getValue(); downLbl.setText(String.format("RateDown: %.2f", v)); CVars.EYE_RATE_DOWN.set(v); editor.setEyeRates(editor.getEyeRateUp(), v);} });
      ui.add(down).width(240).row();
      // LumRate
      final Label lumLbl = new Label(String.format("LumRate: %.2f", editor.getEyeLumRate()), skin);
      ui.add(lumLbl).left().padTop(4).row();
      final Slider lum = new Slider(0.5f, 6.0f, 0.1f, false, skin);
      lum.setValue(CVars.EYE_LUM_RATE.get());
      lum.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ float v=((Slider)actor).getValue(); lumLbl.setText(String.format("LumRate: %.2f", v)); CVars.EYE_LUM_RATE.set(v); editor.setEyeLumRate(v);} });
      ui.add(lum).width(240).row();
      // MaxStep
      final Label stepLbl = new Label(String.format("MaxStep: %.3f", editor.getEyeMaxStep()), skin);
      ui.add(stepLbl).left().padTop(4).row();
      final Slider step = new Slider(0.01f, 0.2f, 0.001f, false, skin);
      step.setValue(CVars.EYE_MAX_STEP.get());
      step.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ float v=((Slider)actor).getValue(); stepLbl.setText(String.format("MaxStep: %.3f", v)); CVars.EYE_MAX_STEP.set(v); editor.setEyeMaxStep(v);} });
      ui.add(step).width(240).row();
    }
  }

  private final class SkyTool implements Tool {
    @Override public String name() { return "Sky"; }
    @Override public void buildUI(Table ui, Skin skin) {
      final SkyPass sky = pipeline.get(SkyPass.class);
      final GodRaysPass rays = pipeline.get(GodRaysPass.class);
      ui.add(new Label("Sky", skin, "title")).left().row();
      // Sun Size (deg)
      float initSun = CVars.RENDER_SUN_SIZE_DEG.get();
      final Label sunLbl = new Label(String.format("Sun Size: %.2f°", initSun), skin);
      ui.add(sunLbl).left().padTop(4).row();
      final Slider sun = new Slider(0.3f, 3.0f, 0.01f, false, skin);
      sun.setValue(initSun);
      sun.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ float v=((Slider)actor).getValue(); sunLbl.setText(String.format("Sun Size: %.2f°", v)); CVars.RENDER_SUN_SIZE_DEG.set(v); if (sky!=null) sky.setSunSizeDeg(v);} });
      ui.add(sun).width(240).row();

      // Warmth
      float initWarm = CVars.RENDER_SKY_WARMTH.get();
      final Label warmLbl = new Label(String.format("Warmth: %.2f", initWarm), skin);
      ui.add(warmLbl).left().padTop(4).row();
      final Slider warm = new Slider(0f, 1f, 0.01f, false, skin);
      warm.setValue(initWarm);
      warm.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ float v=((Slider)actor).getValue(); warmLbl.setText(String.format("Warmth: %.2f", v)); CVars.RENDER_SKY_WARMTH.set(v); if (sky!=null) sky.setWarmth(v);} });
      ui.add(warm).width(240).row();

      // God Rays exposure (CVar in 0..5, EditorScreen maps to internal 0..~0.5)
      float initRays = CVars.RENDER_GODRAYS_EXP.get();
      ui.add(new Label("God Rays", skin, "title")).left().padTop(8).row();
      final Label raysLbl = new Label(String.format("Exposure: %.2f", initRays), skin);
      ui.add(raysLbl).left().row();
      final Slider raysS = new Slider(0f, 5.0f, 0.01f, false, skin);
      raysS.setValue(initRays);
      raysS.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ float v=((Slider)actor).getValue(); raysLbl.setText(String.format("Exposure: %.2f", v)); CVars.RENDER_GODRAYS_EXP.set(v); if (rays!=null) { float mapped = Math.max(0f, Math.min(5f, v)) * 0.1f; rays.setExposure(mapped);} } });
      ui.add(raysS).width(240).row();
    }
  }
}
