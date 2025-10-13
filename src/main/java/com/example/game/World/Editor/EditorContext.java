package com.example.game.World.Editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Vector3;

/**
 * Global editor context for lightweight shared references (camera, etc.).
 * Keep this minimal to avoid hidden coupling.
 */
public final class EditorContext {

  private static volatile Camera camera;   // set once on Screen.show(), updated by EditorCamera
  private static volatile com.example.game.World.Placeable selectedPlaceable; // currently selected placeable (optional)

  private EditorContext() {}

  /** Set the active editor camera (call from your Screen.show()). */
  public static void setCamera(Camera cam) {
    camera = cam;
  }

  /** Get the active editor camera (may be null before Screen.show()). */
  public static Camera getCamera() {
    return camera;
  }

  // -------------------
  // Optional helpers
  // -------------------

  /** Convenience: unproject a screen-space point to world space with the active camera. */
  public static Vector3 unproject(int screenX, int screenY, float screenZ, Vector3 out) {
    Camera cam = camera;
    if (cam == null) return null;
    out.set(screenX, screenY, screenZ);
    cam.unproject(out);
    return out;
  }

  /** Convenience: project a world-space point to screen space with the active camera. */
  public static Vector3 project(Vector3 world, Vector3 out) {
    Camera cam = camera;
    if (cam == null) return null;
    out.set(world);
    cam.project(out);
    return out;
  }

  /** True if a camera is set and viewport matches current window size (debug aid). */
  public static boolean isCameraValid() {
    Camera cam = camera;
    return cam != null && cam.viewportWidth == Gdx.graphics.getWidth() && cam.viewportHeight == Gdx.graphics.getHeight();
  }

  // -------------------
  // Selected placeable tracking
  // -------------------
  /** Set the currently selected placeable (null to clear). */
  public static void setSelectedPlaceable(com.example.game.World.Placeable p) { selectedPlaceable = p; }
  /** Get the currently selected placeable, or null. */
  public static com.example.game.World.Placeable getSelectedPlaceable() { return selectedPlaceable; }
}
