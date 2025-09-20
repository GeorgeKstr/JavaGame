package com.example.game;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.example.game.App.GameApp;


public class Lwjgl3Launcher {
  public static void main(String[] args) {
    Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
    cfg.setTitle("Heightmap Starter");
    cfg.setWindowedMode(1280, 720);
    cfg.useVsync(true);
    cfg.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 2);
    // Start your GameApp (the Screen manager)
    new Lwjgl3Application(new GameApp(), cfg);
  }
}