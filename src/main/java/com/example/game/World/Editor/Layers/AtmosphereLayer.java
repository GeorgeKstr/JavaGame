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
import com.example.game.Render.Passes.FogPass;
import com.example.game.Screens.EditorScreen;
import com.example.game.Config.cvar.CVars;
import com.example.game.World.Atmosphere.FogVolume;
import com.example.game.World.TerrainSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.example.game.World.Editor.EditorContext;

public final class AtmosphereLayer extends BaseToolLayer {
  private final RenderPipeline pipeline;
  private final EditorScreen editor;
  private final TerrainSystem terrain;

  public AtmosphereLayer(RenderPipeline pipeline, EditorScreen editor, TerrainSystem terrain) {
    super("atmosphere", "Atmosphere");
    this.pipeline = pipeline;
    this.editor = editor;
    this.terrain = terrain;
    addTool(new ExposureTool());
    addTool(new SkyTool());
    addTool(new FogTool());
    // Hook fog supplier if pass exists
    try {
      FogPass fog = pipeline.get(FogPass.class);
      if (fog != null) fog.setSupplier(() -> fogVolumes);
    } catch (Throwable ignored) {}
  }

  // ---------------- Fog Tool ----------------
  private final java.util.List<FogVolume> fogVolumes = new java.util.ArrayList<>();
  private int fogSelected = -1;
  private final class FogTool implements Tool {
    private final com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20 imBox = new com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20(4096, false, true, 0);
    private final com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20 imCir = new com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20(4096, false, true, 0);
    @Override public String name() { return "Fog"; }
    @Override public void buildUI(Table ui, Skin skin) {
      ui.add(new Label("Fog Volumes", skin, "title")).left().row();

      // List of fog items
      final com.badlogic.gdx.scenes.scene2d.ui.List<String> list = new com.badlogic.gdx.scenes.scene2d.ui.List<>(skin);
      final com.badlogic.gdx.scenes.scene2d.ui.ScrollPane scroll = new com.badlogic.gdx.scenes.scene2d.ui.ScrollPane(list, skin);
      scroll.setFadeScrollBars(false);
      ui.add(scroll).width(260).height(160).left().row();

      // Radius slider
      ui.add(new Label("Radius", skin)).left().padTop(6).row();
      final Slider radius = new Slider(1f, 2000f, 0.5f, false, skin);
      ui.add(radius).width(240).row();

      // Falloff slider
      ui.add(new Label("Falloff", skin)).left().padTop(6).row();
      final Slider falloff = new Slider(0f, 1f, 0.01f, false, skin);
      ui.add(falloff).width(240).row();

      // Intensity (density) slider
      ui.add(new Label("Intensity", skin)).left().padTop(6).row();
      final Slider intensity = new Slider(0f, 3f, 0.01f, false, skin);
      ui.add(intensity).width(240).row();

      // RGB tint sliders
      ui.add(new Label("Tint R", skin)).left().padTop(6).row();
      final Slider tintR = new Slider(0f, 1f, 0.01f, false, skin);
      ui.add(tintR).width(240).row();
      ui.add(new Label("Tint G", skin)).left().padTop(4).row();
      final Slider tintG = new Slider(0f, 1f, 0.01f, false, skin);
      ui.add(tintG).width(240).row();
      ui.add(new Label("Tint B", skin)).left().padTop(4).row();
      final Slider tintB = new Slider(0f, 1f, 0.01f, false, skin);
      ui.add(tintB).width(240).row();

      // Noise slider
      ui.add(new Label("Local Noise", skin)).left().padTop(6).row();
      final Slider noise = new Slider(0f, 1f, 0.01f, false, skin);
      ui.add(noise).width(240).row();

      // Near Clip slider (when inside, push fog start away from camera)
      ui.add(new Label("Near Clip (m)", skin)).left().padTop(6).row();
      final Slider nearClip = new Slider(0f, 50f, 0.5f, false, skin);
      ui.add(nearClip).width(240).row();

      // Delete button
      com.badlogic.gdx.scenes.scene2d.ui.TextButton del = new com.badlogic.gdx.scenes.scene2d.ui.TextButton("Delete", skin);
      ui.add(del).left().padTop(6).row();

      // Bindings
      list.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
        fogSelected = list.getSelectedIndex();
        if (fogSelected >= 0 && fogSelected < fogVolumes.size()) {
          FogVolume v = fogVolumes.get(fogSelected);
          radius.setValue(v.radius);
          falloff.setValue(v.falloff);
          intensity.setValue(v.density);
          noise.setValue(v.noise);
          nearClip.setValue(v.nearClip);
          tintR.setValue(v.r); tintG.setValue(v.g); tintB.setValue(v.b);
        }
      }});
      radius.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
        if (fogSelected >= 0 && fogSelected < fogVolumes.size()) fogVolumes.get(fogSelected).radius = radius.getValue();
      }});
      falloff.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
        if (fogSelected >= 0 && fogSelected < fogVolumes.size()) fogVolumes.get(fogSelected).falloff = falloff.getValue();
      }});
      intensity.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
        if (fogSelected >= 0 && fogSelected < fogVolumes.size()) fogVolumes.get(fogSelected).density = intensity.getValue();
      }});
      noise.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
        if (fogSelected >= 0 && fogSelected < fogVolumes.size()) fogVolumes.get(fogSelected).noise = noise.getValue();
      }});
      nearClip.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
        if (fogSelected >= 0 && fogSelected < fogVolumes.size()) fogVolumes.get(fogSelected).nearClip = nearClip.getValue();
      }});
      tintR.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ if (fogSelected>=0 && fogSelected<fogVolumes.size()) fogVolumes.get(fogSelected).r = tintR.getValue(); }});
      tintG.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ if (fogSelected>=0 && fogSelected<fogVolumes.size()) fogVolumes.get(fogSelected).g = tintG.getValue(); }});
      tintB.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ if (fogSelected>=0 && fogSelected<fogVolumes.size()) fogVolumes.get(fogSelected).b = tintB.getValue(); }});
      del.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
        if (fogSelected >= 0 && fogSelected < fogVolumes.size()) { fogVolumes.remove(fogSelected); fogSelected = Math.min(fogSelected, fogVolumes.size()-1); refreshList(list); }
      }});

      refreshList(list);
    }

    private void refreshList(com.badlogic.gdx.scenes.scene2d.ui.List<String> list) {
      String[] items = new String[fogVolumes.size()];
      for (int i = 0; i < items.length; i++) items[i] = String.format("Fog %d", i);
      list.setItems(items);
      if (fogSelected >= 0 && fogSelected < items.length) list.setSelectedIndex(fogSelected);
    }

    // Interaction state
    private boolean dragging = false;

    @Override public void onSelected() { dragging = false; }

    @Override public void onPointerDown(int button, boolean shift, boolean ctrl) {
      if (button != com.badlogic.gdx.Input.Buttons.LEFT) return;
      Camera cam = EditorContext.getCamera();
      Ray ray = cam.getPickRay(Gdx.input.getX(), Gdx.input.getY());
      if (ctrl) {
        // Place new fog at ground under cursor (baseline + mountains)
        Vector3 hit = terrain.getPicker().rayIntersectTerrainFastPlane(ray, terrain.getBasePlusMountainsSampler());
        if (hit != null) {
          FogVolume v = new FogVolume().set(hit.x, hit.y, hit.z, 25f);
          fogVolumes.add(v); fogSelected = fogVolumes.size()-1;
        }
        dragging = false; return;
      }
      // Select nearest fog by projected distance
      int nearest = pickNearestFog(cam);
      if (nearest >= 0) { fogSelected = nearest; dragging = true; } else { dragging = false; }
    }

    @Override public void onPointerDrag(int button, boolean shift, boolean ctrl) { /* handled in update */ }

    @Override public void onPointerUp(int button, boolean shift, boolean ctrl) { dragging = false; }

    @Override public void update(float dt, Camera cam) {
      if (!dragging || fogSelected < 0 || fogSelected >= fogVolumes.size()) return;
      Ray ray = cam.getPickRay(Gdx.input.getX(), Gdx.input.getY());
      Vector3 hit = terrain.getPicker().rayIntersectTerrainFastPlane(ray, terrain.getBasePlusMountainsSampler());
      if (hit != null) {
        FogVolume v = fogVolumes.get(fogSelected);
        v.center.set(hit.x, hit.y, hit.z);
      }
    }

    @Override public void render3D(Camera cam) {
      // Pass 1: node boxes (exactly like NodeBoxRenderer)
      com.badlogic.gdx.Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, 0);
      Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
      Gdx.gl.glDisable(GL20.GL_CULL_FACE);
      Gdx.gl.glLineWidth(2f);
      imBox.begin(cam.combined, GL20.GL_LINES);
      for (int i = 0; i < fogVolumes.size(); i++) {
        FogVolume v = fogVolumes.get(i);
        float s = 0.6f; // constant half-size for origin box
        float x0 = v.center.x - s, y0 = v.center.y - s, z0 = v.center.z - s;
        float x1 = v.center.x + s, y1 = v.center.y + s, z1 = v.center.z + s;
        // Colors like spline nodes (alpha=1 for solid bright lines)
        float cr, cg, cb, ca = 1.0f;
        if (i == fogSelected) { cr = 1.0f; cg = 0.25f; cb = 0.25f; }
        else if (fogSelected >= 0) { cr = 0.20f; cg = 0.60f; cb = 1.00f; }
        else { cr = 0.08f; cg = 0.35f; cb = 0.60f; }
        boxLines(imBox, cr,cg,cb,ca, x0,y0,z0, x1,y1,z1);
      }
      imBox.end();
      Gdx.gl.glLineWidth(1f);

      // Pass 2: ground helper circles (depth-tested so they "fight" terrain)
      Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
      // Keep culling disabled for wire circles
      Gdx.gl.glDisable(GL20.GL_CULL_FACE);
      Gdx.gl.glEnable(GL20.GL_BLEND);
      Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
      imCir.begin(cam.combined, GL20.GL_LINES);
      for (int i = 0; i < fogVolumes.size(); i++) {
        FogVolume v = fogVolumes.get(i);
        float y = v.center.y;
        drawCircleXZ(imCir, v.center.x, y, v.center.z, v.radius, (i == fogSelected));
      }
      imCir.end();
      Gdx.gl.glDisable(GL20.GL_BLEND);
      Gdx.gl.glEnable(GL20.GL_CULL_FACE);
      // Leave depth test state as it was before
    }

    private void boxLines(com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20 im, float cr,float cg,float cb,float ca, float x0,float y0,float z0, float x1,float y1,float z1) {
      // 12 edges
      line(im, cr,cg,cb,ca, x0,y0,z0, x1,y0,z0); line(im, cr,cg,cb,ca, x1,y0,z0, x1,y0,z1); line(im, cr,cg,cb,ca, x1,y0,z1, x0,y0,z1); line(im, cr,cg,cb,ca, x0,y0,z1, x0,y0,z0);
      line(im, cr,cg,cb,ca, x0,y1,z0, x1,y1,z0); line(im, cr,cg,cb,ca, x1,y1,z0, x1,y1,z1); line(im, cr,cg,cb,ca, x1,y1,z1, x0,y1,z1); line(im, cr,cg,cb,ca, x0,y1,z1, x0,y1,z0);
      line(im, cr,cg,cb,ca, x0,y0,z0, x0,y1,z0); line(im, cr,cg,cb,ca, x1,y0,z0, x1,y1,z0); line(im, cr,cg,cb,ca, x1,y0,z1, x1,y1,z1); line(im, cr,cg,cb,ca, x0,y0,z1, x0,y1,z1);
    }
    private void line(com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20 im, float cr,float cg,float cb,float ca, float x0,float y0,float z0, float x1,float y1,float z1) {
      im.color(cr,cg,cb,ca); im.vertex(x0,y0,z0);
      im.color(cr,cg,cb,ca); im.vertex(x1,y1,z1);
    }

    // Overload used by drawCircleXZ which sets color externally
    private void line(com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20 im, float x0,float y0,float z0, float x1,float y1,float z1) {
      im.vertex(x0,y0,z0);
      im.vertex(x1,y1,z1);
    }

    private void drawCircleXZ(com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20 im, float cx, float y, float cz, float r, boolean selected) {
      int segs = 48;
      float scale = 1f; // boosted to better match perceived screen-space fog mask
      float rr = r * scale;
      float prevX = cx + rr, prevZ = cz;
      for (int i = 1; i <= segs; i++) {
        float ang = (float)(i * (Math.PI * 2.0) / segs);
        float x = cx + rr * (float)Math.cos(ang);
        float z = cz + rr * (float)Math.sin(ang);
        if (selected) im.color(1.0f, 0.75f, 0.15f, 1.0f); else im.color(0.2f, 0.9f, 0.6f, 0.9f);
        line(im, prevX, y, prevZ, x, y, z);
        prevX = x; prevZ = z;
      }
    }

    private int pickNearestFog(Camera cam) {
      int sx = Gdx.input.getX(); int sy = Gdx.input.getY();
      float best = 24f; // pixels
      int bestIdx = -1;
      for (int i = 0; i < fogVolumes.size(); i++) {
        Vector3 ndc = new Vector3(fogVolumes.get(i).center).prj(cam.combined);
        float ux = (ndc.x * 0.5f + 0.5f) * Gdx.graphics.getWidth();
        float uy = (1f - (ndc.y * 0.5f + 0.5f)) * Gdx.graphics.getHeight();
        float dx = sx - ux, dy = sy - uy; float d = (float)Math.sqrt(dx*dx + dy*dy);
        if (d < best) { best = d; bestIdx = i; }
      }
      return bestIdx;
    }
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
