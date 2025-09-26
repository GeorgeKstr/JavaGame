package com.example.game;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.example.game.App.GameApp;


public class Lwjgl3Launcher {
  public static void main(String[] args) {
    Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
    cfg.setTitle("Heightmap Starter");
    cfg.setWindowedMode(1280, 720);
    cfg.useVsync(false);
    cfg.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 2);
    cfg.setBackBufferConfig(
    8, 8, 8, 8,   // RGBA
    24,           // depth bits (request 24)
    8,            // stencil bits (optional)
    4);             // MSAA samples (optional)
    // Start your GameApp (the Screen manager)
    new Lwjgl3Application(new GameApp(), cfg);
  }
}