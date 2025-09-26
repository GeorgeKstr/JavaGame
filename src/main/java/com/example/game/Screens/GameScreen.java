package com.example.game.Screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.example.game.App.GameApp;
import com.example.game.Gameplay.PlayerController;
import com.example.game.Render.SimpleShader;
import com.example.game.UI.SkinFactory;
import com.example.game.World.TerrainChunk;

public class GameScreen implements Screen {
  private final GameApp app;

  private PerspectiveCamera cam;
  private SimpleShader shader;
  private TerrainChunk chunk;
  private PlayerController player;

  // HUD
  private Stage hud;
  private Skin skin;

  public GameScreen(GameApp app) {
    this.app = app;
  }

  @Override
  public void show() {
    // Camera
    cam = new PerspectiveCamera(75f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    cam.near = 0.8f;
    cam.far  = 2000f;

    // Shader
    shader = new SimpleShader();
    shader.init();
    shader.setSunDirection(-0.4f, -1.0f, -0.3f);
    shader.setColor(new Color(0.52f, 0.75f, 0.45f, 1f));

    // Terrain (replace with your own data later)
    final int S = 64; final float TILE = 1f; final float YSCALE = 0.5f;
    short[] heights = new short[S * S];
    for (int z = 0; z < S; z++) {
      for (int x = 0; x < S; x++) {
        heights[z * S + x] = (short)(Math.sin(x*0.15)*5 + Math.cos(z*0.2)*4);
      }
    }
    chunk = new TerrainChunk(S, TILE, YSCALE);
    chunk.setHeights(heights);
    chunk.buildMesh();

    // Player
    player = new PlayerController(cam, chunk);
    player.pos.set(32f, 0f, 32f);
    player.eyeHeight = 1.6f;

    // HUD
    hud = new Stage(new ScreenViewport());
    skin = SkinFactory.create();
    TextButton back = new TextButton("Back to Menu", skin);
    back.setPosition(16, 16);
    back.addListener(SkinFactory.onClick(() -> {
      if (Gdx.input.isCursorCatched()) Gdx.input.setCursorCatched(false);
      app.goToMenu();
    }));
    hud.addActor(back);

    // Input multiplexer (HUD + ESC toggle)
    Gdx.input.setInputProcessor(new InputMultiplexer(hud, new InputAdapter() {
      @Override
      public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.ESCAPE) {
          player.toggleCursor();
          return true;
        }
        return false;
      }
    }));
  }

  @Override
  public void render(float dt) {
    // Update player
    player.update(dt);

    // 3D pass
    Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    Gdx.gl.glClearColor(0.05f, 0.07f, 0.10f, 1f);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    Gdx.gl.glEnable(GL20.GL_CULL_FACE);
    Gdx.gl.glCullFace(GL20.GL_BACK);

    shader.bind();
    shader.setProjectionView(cam.combined);
    chunk.render(shader);
    shader.unbind();

    // HUD pass
    hud.act(dt);
    Gdx.gl.glDisable(GL20.GL_CULL_FACE);
    Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    hud.draw();
  }

  @Override
  public void resize(int width, int height) {
    cam.viewportWidth = width;
    cam.viewportHeight = height;
    cam.update();
    hud.getViewport().update(width, height, true);
  }

  @Override public void pause() {}
  @Override public void resume() {}
  @Override public void hide() {}
  @Override
  public void dispose() {
    if (shader != null) shader.dispose();
    if (chunk != null) chunk.dispose();
    if (hud != null) hud.dispose();
    if (skin != null) skin.dispose();
  }
}
