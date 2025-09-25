package com.example.game.World.Editor;

import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Vector3;
import com.example.game.World.TerrainSystem;

public final class EditorBootstrap {
  private EditorBootstrap() {}

  public static EditorInputRouter inputRouter(LayerManager layers, EditorCamera edCam, Camera cam, TerrainSystem terrain) {
    return new EditorInputRouter(new EditorInputRouter.Access() {
      @Override public Camera cam() { return cam; }
      @Override public EditorLayer activeLayer() { return layers.active(); }
      @Override public void cameraOrbit(float dx, float dy) { edCam.orbit(-dx, -dy); }
      @Override public void cameraPan(float dx, float dy) { edCam.pan(cam, dx, dy); }
      @Override public void cameraZoom(float amount) { edCam.zoom(amount); }
      @Override public void moveWASD(boolean w, boolean a, boolean s, boolean d, float dt) { edCam.move(cam, w, a, s, d, dt); }
      @Override public Vector3 pickOnTerrain(int sx, int sy, Vector3 outWorld) {
        return terrain.raycastToGround(cam, sx, sy, outWorld) ? outWorld : null;
      }
    });
  }
}
