package com.example.game.World.Editor;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/**
 * Editor camera controller: orbit, pan, zoom around a focus point.
 */
public class EditorCamera {
  private float yaw = 135f;
  private float pitch = 35f;
  private float distance = 60f;

  private final Vector3 focus = new Vector3(32, 0, 32); // center of interest

  /** Orbit the camera around the focus point. */
  public void orbit(float dx, float dy) {
    yaw   -= dx * 0.3f;
    pitch = MathUtils.clamp(pitch + dy * 0.3f, 5f, 85f);
  }

  /** Pan the focus point along the ground plane. */
  public void pan(PerspectiveCamera cam, float dx, float dy) {
    float panSpeed = 0.0025f * distance;
    Vector3 right = new Vector3(cam.direction).crs(cam.up).nor();
    Vector3 fwd   = new Vector3(cam.direction.x, 0f, cam.direction.z);
    if (fwd.isZero()) fwd.set(0,0,-1);
    fwd.nor();
    focus.mulAdd(right, -dx * panSpeed).mulAdd(fwd, dy * panSpeed);
  }

  /** Zoom the camera in or out. */
  public void zoom(float amount) {
    distance = MathUtils.clamp(distance + amount * 3f, 5f, 500f);
  }

  /** Update the LibGDX camera position and orientation. */
  public void update(PerspectiveCamera cam) {
    float cy = MathUtils.clamp(pitch, 5f, 85f);
    float cp = MathUtils.cosDeg(cy), sp = MathUtils.sinDeg(cy);
    float sy = MathUtils.sinDeg(yaw), cyaw = MathUtils.cosDeg(yaw);

    Vector3 eye = new Vector3(
      focus.x + distance * cp * sy,
      focus.y + distance * sp,
      focus.z + distance * cp * -cyaw
    );
    cam.position.set(eye);
    cam.lookAt(focus);
    cam.up.set(Vector3.Y);
    cam.update();
  }

  public Vector3 focus() { return focus; }
  public float distance() { return distance; }
}
