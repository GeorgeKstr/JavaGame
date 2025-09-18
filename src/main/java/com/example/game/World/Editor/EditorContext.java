package com.example.game.World.Editor;
import com.badlogic.gdx.graphics.PerspectiveCamera;
public class EditorContext {
  private static PerspectiveCamera CAM;
  public static void setCamera(PerspectiveCamera c){ CAM=c; }
  public static PerspectiveCamera cam(){ return CAM; }
}
