package com.example.game.App;

import com.badlogic.gdx.Game;
import com.example.game.Screens.EditorScreen;
import com.example.game.Screens.GameScreen;
import com.example.game.Screens.MainMenuScreen;


public class GameApp extends Game {

  @Override
  public void create() {
    setScreen(new MainMenuScreen(this));
  }

  @Override
  public void dispose() {
    if (getScreen() != null) getScreen().dispose();
    super.dispose();
  }

  // Convenience helpers for navigation
  public void goToGame() { setScreen(new GameScreen(this)); };
  public void goToMenu() { setScreen(new MainMenuScreen(this)); };
  public void goToEditor(String mapString) { setScreen(new EditorScreen(this, mapString)); };
}
