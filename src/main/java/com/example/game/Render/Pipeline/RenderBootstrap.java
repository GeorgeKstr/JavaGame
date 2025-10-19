package com.example.game.Render.Pipeline;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.example.game.Render.Passes.*;
import com.example.game.World.TerrainSystem;

public final class RenderBootstrap {

  public static final class Config {
    public int birdsCount = 30;
    public float birdsRadius = 600f;
    public float birdsMinPx = 2f, birdsMaxPx = 9f;
    public float birdsSpeed = 0.8f;
    public float birdsCenterRepel = 0.8f;
    public float birdsHorizonBias = 1.0f;
    public float raysExposure = 0.6f;
    public static Config defaultConfig() { return new Config(); }
  }

  /** Build passes and pipeline in one place. */
  public static RenderPipeline build(RenderContext ctx, TerrainSystem terrain, Config cfg) {
    // Passes
    SkyPass sky = new SkyPass(); sky.init();
    TerrainPass terrainPass = new TerrainPass(terrain); terrainPass.init();
    
    // Birds
    Texture birdTex = new Texture(Gdx.files.internal("textures/bird_silhouette.png"), true);
    birdTex.setFilter(TextureFilter.MipMapLinearLinear, TextureFilter.Linear);
    birdTex.setWrap(TextureWrap.ClampToEdge, TextureWrap.ClampToEdge);

    BirdsPass birds = new BirdsPass(cfg.birdsCount);
    birds.init();
    birds.setRadius(cfg.birdsRadius);
    birds.setPixelSizeRange(cfg.birdsMinPx, cfg.birdsMaxPx);
    birds.setGlobalSpeed(cfg.birdsSpeed);
    birds.setTexture(birdTex, true);
    birds.setTint(0.9f, 0.9f, 0.9f);
    birds.setCenterRepel(cfg.birdsCenterRepel);
    birds.setHorizonBias(cfg.birdsHorizonBias);

    GodRaysPass rays = new GodRaysPass(sky, terrainPass, birds); rays.init(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    // Rays exposure
    rays.setExposure(cfg.raysExposure);

    // Editor marker (on-top ring at camera ground point)
    EditorMarkerPass marker = new EditorMarkerPass(terrain.getBaseHeightSampler());
    marker.init();
    marker.setRadius(1.1f);
    marker.setThickness(0.10f);

    // Pipeline order: sky → terrain → rays → birds → marker (marker on top of everything)
    return new RenderPipeline()
        .add(sky)
        .add(birds)
        .add(terrainPass)
        .add(rays)
        .add(marker);
  }
}
