package com.example.game.App;

import com.badlogic.gdx.Game;
import com.example.game.Config.Settings;
import com.example.game.Config.SettingsStore;
import com.example.game.Config.cvar.CVars;
import com.example.game.Config.cvar.PrefsCVarStore;
import com.example.game.Config.cvar.CVarSettingsBridge;
import com.example.game.Screens.EditorScreen;
import com.example.game.Screens.GameScreen;
import com.example.game.Screens.MainMenuScreen;


public class GameApp extends Game {
  private Settings settings;


  @Override
  public void create() {
    // 1) Load legacy settings
    settings = SettingsStore.load();
    // 2) Seed CVars from legacy
    CVarSettingsBridge.fromSettings(settings);
    // 3) Load CVar persistence (overrides)
    PrefsCVarStore.loadAll(CVars.reg);
    // 4) Rebuild Settings facade from CVars for backward compatibility
    settings = CVarSettingsBridge.toSettings();
    setScreen(new MainMenuScreen(this));
  }

  @Override
  public void dispose() {
    if (getScreen() != null) getScreen().dispose();
    // Persist both CVars and Settings facade
    PrefsCVarStore.saveAll(CVars.reg);
    SettingsStore.save(CVarSettingsBridge.toSettings());
    super.dispose();
  }

  // Convenience helpers for navigation
  public void goToGame() { setScreen(new GameScreen(this)); };
  public void goToMenu() { setScreen(new MainMenuScreen(this)); };
  public void goToEditor(String mapString) { setScreen(new EditorScreen(this, mapString, settings)); };
}
