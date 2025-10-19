package com.example.game.World.Editor;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.example.game.World.TerrainSystem;
import com.example.game.World.Editor.Tools.Tool;

public final class EditorBootstrap {
  private EditorBootstrap() {}

  public static EditorInputRouter inputRouter(LayerManager layers, EditorCamera edCam, FpsCameraController fpsCam, Camera cam, TerrainSystem terrain) {
    return new EditorInputRouter(new EditorInputRouter.Access() {
      @Override public Camera cam() { return cam; }
      @Override public EditorLayer activeLayer() { return layers.active(); }
      @Override public void cameraOrbit(float dx, float dy) {
        EditorLayer l = layers.active();
        boolean useFps = false;
        if (l != null && "walk".equals(l.id()) && l instanceof BaseToolLayer) {
          Tool t = ((BaseToolLayer) l).getActiveTool();
          useFps = (t != null && "Roam".equals(t.name()));
        }
        // In FPS (Roam), invert Y so moving mouse up looks up; X preserved
        if (useFps) fpsCam.look(dx, -dy); else edCam.orbit(-dx, -dy);
      }
      @Override public void cameraZoom(float amount) {
        EditorLayer l = layers.active();
        boolean useFps = false;
        if (l != null && "walk".equals(l.id()) && l instanceof BaseToolLayer) {
          Tool t = ((BaseToolLayer) l).getActiveTool();
          useFps = (t != null && "Roam".equals(t.name()));
        }
        if (useFps) {
          // Map scroll to FPS speed change
          // positive amountY usually means scroll down; decrease speed a bit
          float cur = 6.0f; // default baseline; no getter, so approximate by deltas
          float mul = (float)Math.exp(-amount * 0.1f);
          fpsCam.setMoveSpeed(cur * mul);
        } else {
          edCam.zoom(amount);
        }
      }
      @Override public void moveWASD(boolean w, boolean a, boolean s, boolean d, float dt) { 
        EditorLayer l = layers.active();
        boolean useFps = false;
        if (l != null && "walk".equals(l.id()) && l instanceof BaseToolLayer) {
          Tool t = ((BaseToolLayer) l).getActiveTool();
          useFps = (t != null && "Roam".equals(t.name()));
        }
        if (useFps) fpsCam.move(w, a, s, d, dt); else edCam.move(cam, s, d, w, a, dt/3f); 
      }
    });
  }
}
