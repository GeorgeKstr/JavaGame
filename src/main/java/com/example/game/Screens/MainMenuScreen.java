package com.example.game.Screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.example.game.App.GameApp;
import com.example.game.UI.SkinFactory;

public class MainMenuScreen implements Screen {
  private final Stage stage;
  private final Skin skin;

  public MainMenuScreen(GameApp app) {
    this.stage = new Stage(new ScreenViewport());
    this.skin  = SkinFactory.create(); // programmatic skin (no files)

    // Layout
    Table root = new Table();
    root.setFillParent(true);
    root.defaults().pad(8f).width(320f).height(56f);
    stage.addActor(root);

    Label title = new Label("HEIGHTMAP SHOOTER", skin, "title");
    title.setAlignment(Align.center);
    root.add(title).padBottom(24f).row();

    TextButton play = new TextButton("Play", skin);
    TextButton options = new TextButton("Options (stub)", skin);
    TextButton quit = new TextButton("Quit", skin);
    TextButton editor = new TextButton("Editor", skin);

    
    root.add(play).row();
    root.add(editor).row();
    root.add(options).row();
    root.add(quit).row();
    
    play.addListener(SkinFactory.onClick(() -> app.goToGame()));
    editor.addListener(SkinFactory.onClick(() -> app.goToEditor("maps/MyLevel.map")));
    options.addListener(SkinFactory.onClick(() -> System.out.println("Options coming soon")));
    quit.addListener(SkinFactory.onClick(() -> Gdx.app.exit()));

    Gdx.input.setInputProcessor(stage);
  }

  @Override public void show() {}
  @Override public void render(float dt) {
    stage.act(dt);
    Gdx.gl.glClearColor(0.06f,0.07f,0.09f,1f);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    stage.draw();
  }
  @Override public void resize(int w,int h){ stage.getViewport().update(w,h,true); }
  @Override public void pause() {}
  @Override public void resume() {}
  @Override public void hide() { }
  @Override public void dispose() { stage.dispose(); skin.dispose(); }
}
