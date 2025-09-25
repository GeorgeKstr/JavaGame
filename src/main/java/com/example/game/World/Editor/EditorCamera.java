package com.example.game.World.Editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.example.game.Config.Settings;

/**
 * Simple orbital editor camera controller.
 * - orbit(dx,dy): rotates camera around a pivot/focus
 * - pan(dx,dy):    shifts the pivot in view space
 * - zoom(amount):  changes camera distance to pivot
 * - move(WASD):    strafes the pivot on XZ plane (keeps height)
 * - update(cam):   applies yaw/pitch/distance to the libGDX Camera
 */
public final class EditorCamera {

  private final Settings settings;

  // Spherical state around pivot
  private final Vector3 pivot = new Vector3(0, 0, 0);
  private float yawDeg = 0f;            // yaw around +Y
  private float pitchDeg = -20f;        // up/down (clamped)
  private float distance = 30f;         // radius from pivot

  // Tunables
  private float orbitSens = 0.2f;       // pixels → degrees
  private float panFactor = 0.0030f;    // pixels → world units (scaled by distance)
  private float zoomFactor = 0.1f;      // scroll multiplier (expo)

  // Scratch
  private final Vector3 dir = new Vector3();
  private final Vector3 right = new Vector3();
  private final Vector3 up = new Vector3();

  public EditorCamera(Settings settings) {
    this.settings = settings;
  }

  /** Current world-space focus/pivot. */
  public Vector3 focus() { return pivot; }

  /** Set focus (e.g., on selection). */
  public void setFocus(float x, float y, float z) { pivot.set(x, y, z); }

  /** Rotate around the pivot. dx,dy in pixels. */
  public void orbit(float dx, float dy) {
    yawDeg   -= dx * orbitSens;
    pitchDeg -= dy * orbitSens;
    pitchDeg = MathUtils.clamp(pitchDeg, -89f, 89f);
  }

  /** Pan the pivot in camera space (Shift+MMB by default). dx,dy in pixels. */
  public void pan(Camera cam, float dx, float dy) {
    // Build a stable right/up basis from the current camera
    right.set(cam.direction).crs(cam.up).nor();
    up.set(right).crs(cam.direction).nor();

    float s = Math.max(0.001f, distance) * panFactor;
    pivot.mulAdd(right, -dx * s).mulAdd(up, dy * s);
  }

  /** Scroll (positive = zoom out by default). */
  public void zoom(float amount) {
    // Exponential zoom feels better across scales
    float mul = (float)Math.exp(amount * zoomFactor);
    distance = MathUtils.clamp(distance * mul, 1.0f, 2000f);
  }

  /**
   * Move the pivot on the XZ plane using WASD intent.
   * Signature matches your router: move(cam, w, a, s, d, dt)
   */
  public void move(Camera cam, boolean w, boolean a, boolean s, boolean d, float dt) {
    float base = (settings != null ? settings.editorMoveSpeed : 10f);

    // Planar forward/right (ignore pitch)
    Vector3 fwd = new Vector3(cam.direction.x, 0f, cam.direction.z).nor();
    if (fwd.isZero()) fwd.set(0f, 0f, -1f);
    Vector3 rgt = new Vector3(fwd).crs(Vector3.Y).nor();

    Vector3 delta = new Vector3();
    if (w) delta.add(fwd);
    if (s) delta.sub(fwd);
    if (a) delta.sub(rgt);
    if (d) delta.add(rgt);

    if (!delta.isZero()) {
      delta.nor().scl(-dt*base*zoomFactor*distance*0.1f); // scale by distance
      pivot.add(delta);
    }
  }

  /** Apply yaw/pitch/distance to the libGDX Camera. Call once per frame. */
  public void update(Camera cam) {
    // Dir from yaw/pitch
    float yawRad = yawDeg * MathUtils.degreesToRadians;
    float pitchRad = pitchDeg * MathUtils.degreesToRadians;

    float cp = MathUtils.cos(pitchRad);
    dir.set(
      MathUtils.sin(yawRad) * cp,
      MathUtils.sin(pitchRad),
      -MathUtils.cos(yawRad) * cp
    ).nor();

    // Position the camera on the sphere, looking at pivot
    cam.position.set(pivot).sub(new Vector3(dir).scl(distance));
    cam.direction.set(dir);
    cam.up.set(Vector3.Y); // keep a stable up for editor
    cam.update(true);
  }
}
